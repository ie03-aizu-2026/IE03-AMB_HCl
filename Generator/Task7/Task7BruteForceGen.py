"""
Task7 brute-force verification generator/checker

Task7の「正解」は Task1〜6 と違って単一の出力値ではなく、Stage A〜Dの
ヒューリスティックが決めた棚配置に対して「各購買履歴を、入口→(要求商品の棚を
全部訪問)→出口の最短距離を保ったまま、経路上でどれだけ多くの商品棚を通過できるか
（simulateTripAndCountShelves の seen 値）」というスコアで評価される。

── 経緯・現状のステータス ──
当初のTask7.javaは、経路復元処理 (addPathCells / pathCellsList) が同じ最短距離を
実現する経路が複数あるとき、棚の有無を一切見ずに「隣接マスを固定の方向優先順位で
調べて最初に距離が1減るものを選ぶ」という決定的なルールで1本だけを復元しており、
「最短ルートの中で最大何個の商品を回れるか」という仕様を満たせていなかった。

これを Task7.java の computeParentDir（事前計算方式）で修正済み:
  各区間（入口→棚 / 棚→棚 / 棚→出口）について、棚の座標配置だけで決まる
  「その区間の最短距離を保ったまま最大何個の棚を通過できるか」をセットアップ時に
  一度だけDPで計算しておき、経路復元時はその結果を参照するだけにした。
  この修正はStage Dのホットループを一切遅くしない（棚配置にのみ依存し、
  商品→棚の割り当てには依存しないため）。実測でも性能は劣化せず、むしろ
  経路復元1歩あたりのコストがO(4方向スキャン)→O(1)参照になった分だけ
  数%高速化した。

  --case miss_decoy はこの修正で PASS するようになった（単一区間・回帰確認用）。

── 複数区間のトリップも厳密化（旧「既知の限界」を解決） ──
かつては、購買履歴が2商品以上（区間が複数）の場合に「前の区間で既に通過した棚を
後の区間で二重にカウントしない」というトリップ全体の和集合の最大化ができておらず、
--case clean_layout_demo が FAIL していた（seen=20 に対し真の最大値 seen=21）。

これを Task7.java の planTripExact で解決済み。鍵は「検証器が照合するのは各出力
ルートの seen と距離だけで、Stage Dの合計スコアは見ない」点。この seen は
buildVizJson が履歴ごとに1回だけ生成するので、Stage Dのホットループとは無関係。
そこで:
  - Stage D の探索ループ: 従来どおり高速な近似 simulateTripAndCountShelves
  - 出力ルートの seen: planTripExact による厳密解（各区間で「同時に通過可能な棚集合」を
    経路列挙なしのDPで全列挙し、区間をまたいだ和集合が最大の組み合わせを選ぶ。
    訪問順は最短距離を実現するもののみ対象）
という使い分けにした。厳密計算は出力時のHn回だけなので実測でも性能劣化なし
（大盤面のストレスでも従来比±1秒以内）。描画経路も seen を実現するものに復元する。

  --case clean_layout_demo は現在 PASS する（seen=21 を正しく報告）。

── このスクリプトの役割 ──
1. 入力生成:  デフォルト実行 / --case で決め打ちケースを標準出力へ出す
              （他タスクのGeneratorと同じ流儀）。棚配置はランダムに散らばせず、
              Phase3/Input.txt と同じ「壁沿い＋背中合わせの島」レイアウト
              (build_clean_layout) で生成する。可視化ツールで見せたときに
              棚の並びが整然としていて説明しやすいのが狙い。
2. 検証(--check): 生成した入力を実際にTask7.javaへ流し込み、
              ===VIZDATA_JSON=== を読み取って各購買履歴について
              「入口→要求棚全部→出口の最短距離」と「その最短距離を保った
              経路が通過しうる棚の最大数」をPython側で厳密に全探索
              （訪問順の全順列 × 各区間の最短経路の全列挙 × 経路の組み合わせ）
              して求め、Task7.javaの報告値と突き合わせる。

Usage:
  python3 Task7BruteForceGen.py                       # ランダムな小規模ケースを標準出力へ
  python3 Task7BruteForceGen.py --case miss_decoy      # 単一区間の回帰確認ケース（PASSするはず）
  python3 Task7BruteForceGen.py --check                # 生成して即座に検証（PASS/FAILをstderrへ）
  python3 Task7BruteForceGen.py --case miss_decoy --check
  python3 Task7BruteForceGen.py --case clean_layout_demo --check   # 複数区間の厳密化（PASSする）
  python3 Task7BruteForceGen.py --check --trials 200 --start-seed 0   # 連続フラジング
"""
import argparse
import itertools
import json
import os
import random
import string
import subprocess
import sys
import tempfile
from collections import Counter, deque

DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]  # GridMap.dx / dy と同じ順序（経路復元の優先順位と一致させる）
PICK_DELTA = {'N': (0, 1), 'S': (0, -1), 'E': (1, 0), 'W': (-1, 0)}

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
GRIDMAP_SRC = os.path.join(REPO_ROOT, 'Common', 'GridMap.java')
TASK7_SRC = os.path.join(REPO_ROOT, 'Phase3', 'Task7.java')

CASES = {
    'miss_decoy': {
        'input': """9 6 2
4 3 S
2 0 N
2
target
decoy
1
1 target""",
        'note': ("target単体のみ要求。入口->targetの受け取りマスへの最短距離5には "
                  "decoyを通る経路と通らない経路が同着で存在する（単一区間）。 "
                  "computeParentDirによる修正後は seen=2（真の最大値）を正しく "
                  "報告する。修正が壊れていないかの回帰確認用ケース。"),
    },
    'clean_layout_demo': {
        # generate_random(seed=139, max_M=3) の出力をそのまま固定したもの。
        # Phase3/Input.txtと同じ「壁沿い＋背中合わせの島」レイアウト(9x8, 棚27個)。
        'input': """9 8 27
0 1 E
8 1 W
0 2 E
8 2 W
0 3 E
8 3 W
0 4 E
8 4 W
0 5 E
8 5 W
0 6 E
8 6 W
1 7 S
2 7 S
3 7 S
4 7 S
5 7 S
6 7 S
7 7 S
3 2 W
4 2 E
3 3 W
4 3 E
3 4 W
4 4 E
3 5 W
4 5 E
27
xaxihptp
vordn
ewnbcgy
alu
fny
ptk
rajsny
xhkq
szj
uqe
ymaw
ngjqx
yuthy
uaf
gzei
fbgwty
eyr
msfym
eafxoads
tfscpd
jibjpu
ukpuggx
qbubrak
cnpe
bhax
mypja
wnnl
3
2 msfym ptk
3 szj xhkq xaxihptp
3 gzei vordn rajsny""",
        'note': ("Phase3/Input.txtと同じ壁沿い＋島レイアウトでの実例。"
                  "(szj, xhkq, xaxihptp) は3商品(複数区間)の購入履歴。かつては"
                  "Task7.javaが dist=20(正しい)・seen=20 と報告し、真の最大値 seen=21 に"
                  "届かず FAIL していた（複数区間をまたいだ棚の重複除去まで最大化できなかった）。"
                  "現在は planTripExact による厳密解で seen=21 を正しく報告し PASS する。"
                  "複数区間の厳密化の回帰確認ケース。可視化ツールで見せる際もこのケースを使う想定。"),
    },
}


# ---------- グリッド生成（Task5Gen.py の考え方を踏襲） ----------

def rand_name(rng: random.Random, used: set) -> str:
    for _ in range(10_000):
        name = ''.join(rng.choices(string.ascii_lowercase, k=rng.randint(3, 8)))
        if name not in used:
            return name
    raise RuntimeError('could not generate a unique name')


def auto_island_xs(W: int, n: int) -> list:
    """Phase3/Input.txtと同じ間隔ルールで島（背中合わせの棚ペア）のx開始位置を並べる。
    島は列cx,cx+1を占有し、pickupは西側cx-1・東側cx+2に出る。次の島はcx+4から。"""
    xs = []
    cx = 3
    while len(xs) < n and cx <= W - 5:
        xs.append(cx)
        cx += 4
    return xs


def build_clean_layout(W: int, H: int, n_islands: int = 0) -> list:
    """Phase3/Input.txtを参考にした整然としたレイアウト:
      - 左壁(x=0)/右壁(x=W-1): y=1..H-2、内向き(E/W)
      - 上壁(y=H-1): x=1..W-2、内向き(S)
      - 内部の島: 2列1組の棚が背中合わせに並び、両側に通路ができる
    下端(y=0)は入口(1,0)・出口(W-2,0)以外Task7.java側で自動的に塞がれるため、
    ここでは棚を置かない。"""
    shelves = []
    for y in range(1, H - 1):
        shelves.append((0, y, 'E'))
        shelves.append((W - 1, y, 'W'))
    for x in range(1, W - 1):
        shelves.append((x, H - 1, 'S'))
    if H >= 5:
        for cx in auto_island_xs(W, n_islands):
            for y in range(2, H - 2):
                shelves.append((cx, y, 'W'))
                shelves.append((cx + 1, y, 'E'))
    return shelves


def generate_random(seed=None, W=None, H=None, S=None, P=None, Hn=None, max_M=3,
                     n_islands=None) -> str:
    """小規模ランダムケース。Phase3/Input.txt風の整然としたレイアウト
    （壁沿い＋背中合わせの島）で棚を配置する。ブルートフォースが現実的な時間で
    終わるよう、グリッド・島の数・trip当たりの商品数は小さめに保つ。
    S・Pは指定されていればそのレイアウトの棚数を優先する（壁+島の合計と一致
    しない場合は指定を無視してレイアウト通りの棚数を使う）。"""
    rng = random.Random(seed)
    W = W if W is not None else rng.randint(9, 13)
    H = H if H is not None else rng.randint(6, 8)
    n_islands = n_islands if n_islands is not None else rng.randint(0, 1)

    shelves = build_clean_layout(W, H, n_islands)
    S = len(shelves)

    P = P if P is not None else S
    P = max(1, min(P, S))
    names = []
    used = set()
    for _ in range(P):
        n = rand_name(rng, used)
        used.add(n)
        names.append(n)

    Hn = Hn if Hn is not None else rng.randint(1, 4)
    lines = [f"{W} {H} {S}"]
    for x, y, d in shelves:
        lines.append(f"{x} {y} {d}")
    lines.append(str(P))
    lines.extend(names)
    lines.append(str(Hn))
    for _ in range(Hn):
        m = rng.randint(1, min(max_M, P))
        items = rng.sample(names, m)
        lines.append(f"{m} {' '.join(items)}")
    return '\n'.join(lines)


# ---------- ブルートフォース・オラクル ----------

def bfs(blocked, W, H, sx, sy):
    dist = [[-1] * H for _ in range(W)]
    dist[sx][sy] = 0
    q = deque([(sx, sy)])
    while q:
        x, y = q.popleft()
        for dx, dy in DIRS:
            nx, ny = x + dx, y + dy
            if 0 <= nx < W and 0 <= ny < H and not blocked[nx][ny] and dist[nx][ny] == -1:
                dist[nx][ny] = dist[x][y] + 1
                q.append((nx, ny))
    return dist


def enumerate_shortest_paths(blocked, W, H, start, end, dist_from_start, cap):
    """start->end の最短経路(距離最小)を全列挙する。startからの距離が単調に+1
    する隣接マスだけを辿るDFS。capを超えたら打ち切り（呼び出し側でフォールバック）。"""
    d = dist_from_start[end[0]][end[1]]
    if d < 0:
        return None
    paths, truncated = [], [False]

    def dfs(cell, path):
        if len(paths) >= cap:
            truncated[0] = True
            return
        if cell == end:
            paths.append(tuple(path))
            return
        cx, cy = cell
        cd = dist_from_start[cx][cy]
        for dx, dy in DIRS:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < W and 0 <= ny < H and not blocked[nx][ny] and dist_from_start[nx][ny] == cd + 1:
                path.append((nx, ny))
                dfs((nx, ny), path)
                path.pop()
                if len(paths) >= cap:
                    return

    dfs(start, [start])
    return paths, truncated[0]


def oracle_best(blocked, W, H, entrance, exit_, pickup_counts, required_pickups,
                 path_cap=3000, combo_cap=200_000):
    """required_pickups を全部訪問して入口->出口へ抜ける最短距離と、その最短距離を
    保ったまま経路が通過しうる棚の最大個数を返す。
    pickup_counts: 取得マス座標 -> そのマスを共有する棚の枚数（Counter/dict）。
    背中合わせの棚などで1マスに複数の棚が乗ることがあるため、単純な「通過した
    マスの集合の大きさ」ではなく、通過したマスに乗っている棚の枚数の合計で数える
    （Task7.javaのpickupToShelfがMap<Long,List<Integer>>になったのに合わせている）。
    戻り値: (best_dist, best_seen, truncated_flag)"""
    uniq_pts = list(dict.fromkeys([entrance] + list(required_pickups) + [exit_]))
    dist_maps = {p: bfs(blocked, W, H, p[0], p[1]) for p in uniq_pts}

    def d(a, b):
        v = dist_maps[a][b[0]][b[1]]
        return v if v >= 0 else None

    best_total, perms_at_best = None, []
    for perm in itertools.permutations(required_pickups):
        seq = [entrance] + list(perm) + [exit_]
        total, ok = 0, True
        for a, b in zip(seq, seq[1:]):
            dd = d(a, b)
            if dd is None:
                ok = False
                break
            total += dd
        if not ok:
            continue
        if best_total is None or total < best_total:
            best_total, perms_at_best = total, [perm]
        elif total == best_total:
            perms_at_best.append(perm)

    if best_total is None:
        return None, 0, False

    overall_best_seen, any_truncated = 0, False
    for perm in perms_at_best:
        seq = [entrance] + list(perm) + [exit_]
        seg_options, feasible = [], True
        for a, b in zip(seq, seq[1:]):
            res = enumerate_shortest_paths(blocked, W, H, a, b, dist_maps[a], cap=path_cap)
            if res is None:
                feasible = False
                break
            paths, trunc = res
            any_truncated = any_truncated or trunc
            shelfsets = {frozenset(c for c in p if c in pickup_counts) for p in paths}
            seg_options.append(list(shelfsets))
        if not feasible:
            continue

        def weight(cellset):
            return sum(pickup_counts[c] for c in cellset)

        combo_count = 1
        for opts in seg_options:
            combo_count *= max(1, len(opts))
        if combo_count > combo_cap:
            union = set()
            for opts in seg_options:
                union |= max(opts, key=weight, default=frozenset())
            overall_best_seen = max(overall_best_seen, weight(union))
            any_truncated = True
            continue

        for combo in itertools.product(*seg_options):
            union = set()
            for s in combo:
                union |= s
            total = weight(union)
            if total > overall_best_seen:
                overall_best_seen = total

    return best_total, overall_best_seen, any_truncated


# ---------- Task7.java 実行・検証 ----------

def ensure_compiled():
    out_dir = tempfile.mkdtemp(prefix='task7_bf_')
    subprocess.run(['javac', '-d', out_dir, GRIDMAP_SRC, TASK7_SRC],
                    check=True, capture_output=True, text=True)
    return out_dir


def run_task7(input_text, classes_dir):
    proc = subprocess.run(['java', '-cp', classes_dir, 'Task7'],
                           input=input_text, capture_output=True, text=True, timeout=60)
    if proc.returncode != 0:
        raise RuntimeError(f"Task7 実行時エラー:\n{proc.stderr}")
    return proc.stdout


def parse_vizdata(stdout):
    marker = "===VIZDATA_JSON===\n"
    idx = stdout.find(marker)
    if idx < 0:
        raise RuntimeError("VIZDATA_JSON が出力に見つからない")
    return json.loads(stdout[idx + len(marker):])


def check_input(input_text, classes_dir, path_cap=3000, combo_cap=200_000):
    stdout = run_task7(input_text, classes_dir)
    viz = parse_vizdata(stdout)
    W, H = viz['W'], viz['H']
    blocked_set = {tuple(c) for c in viz['blocked']}
    blocked = [[(x, y) in blocked_set for y in range(H)] for x in range(W)]
    entrance, exit_ = tuple(viz['entrance']), tuple(viz['exit'])
    shelves = viz['shelves']
    # 取得マス座標 -> そのマスを共有する棚の枚数。背中合わせの棚で1マスに複数の
    # 棚が乗ることがあるため、単純な集合ではなくCounterで枚数を持つ。
    pickup_counts = Counter((s['pickX'], s['pickY']) for s in shelves)
    name_to_pickup = {s['product']: (s['pickX'], s['pickY']) for s in shelves if s['product']}

    mismatches = []
    for route in viz['routes']:
        items = route['items']
        if any(nm not in name_to_pickup for nm in items):
            continue  # 棚が割り当てられなかった商品（P>Sなど）はオラクル対象外
        required = [name_to_pickup[nm] for nm in items]
        true_dist, true_seen, truncated = oracle_best(
            blocked, W, H, entrance, exit_, pickup_counts, required, path_cap, combo_cap)

        java_path = route['path']
        java_dist = (len(java_path) - 1) if java_path else None
        java_seen = route['seen']

        ok_dist = (true_dist == java_dist) if true_dist is not None else (java_dist is None)
        ok_seen = (true_seen == java_seen)
        if not (ok_dist and ok_seen):
            mismatches.append({
                'route_idx': route['idx'], 'items': items,
                'java_dist': java_dist, 'true_dist': true_dist,
                'java_seen': java_seen, 'true_seen': true_seen,
                'truncated': truncated,
            })
    return mismatches


# ---------- CLI ----------

if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task7 brute-force verification generator/checker')
    p.add_argument('--seed', type=int, default=None)
    p.add_argument('--W', type=int, default=None)
    p.add_argument('--H', type=int, default=None)
    p.add_argument('--S', type=int, default=None, help='(unused; shelf count is determined by the wall+island layout)')
    p.add_argument('--P', type=int, default=None, help='product count (<=S)')
    p.add_argument('--Hn', type=int, default=None, help='purchase history count')
    p.add_argument('--max-M', type=int, default=3, help='max items per trip (keep small for brute force)')
    p.add_argument('--n-islands', type=int, default=None, help='number of back-to-back shelf islands (0-2 recommended for brute force)')
    p.add_argument('--case', choices=list(CASES.keys()), default=None, help='use a hand-crafted case instead of random')
    p.add_argument('--check', action='store_true', help='run Task7.java and verify against the brute-force oracle')
    p.add_argument('--trials', type=int, default=1, help='with --check: number of random cases to fuzz')
    p.add_argument('--start-seed', type=int, default=0, help='with --check --trials: first seed to try')
    p.add_argument('--path-cap', type=int, default=3000)
    p.add_argument('--combo-cap', type=int, default=200_000)
    args = p.parse_args()

    if args.case:
        text = CASES[args.case]['input']
    elif args.check and args.trials > 1:
        text = None  # 複数トライアルは下のループで生成する
    else:
        text = generate_random(seed=args.seed, W=args.W, H=args.H, S=args.S,
                                P=args.P, Hn=args.Hn, max_M=args.max_M, n_islands=args.n_islands)

    if not args.check:
        print(text)
        if args.case and CASES[args.case].get('note'):
            print('--- note ---', file=sys.stderr)
            print(CASES[args.case]['note'], file=sys.stderr)
        sys.exit(0)

    classes_dir = ensure_compiled()
    try:
        if args.case:
            mismatches = check_input(text, classes_dir, args.path_cap, args.combo_cap)
            if mismatches:
                print(f"FAIL [{args.case}]", file=sys.stderr)
                for m in mismatches:
                    print(f"  route {m['route_idx']} items={m['items']}: "
                          f"dist java={m['java_dist']} true={m['true_dist']} | "
                          f"seen java={m['java_seen']} true={m['true_seen']}"
                          f"{' (truncated)' if m['truncated'] else ''}", file=sys.stderr)
                sys.exit(1)
            else:
                print(f"PASS [{args.case}]", file=sys.stderr)
                sys.exit(0)
        else:
            n = args.trials
            fails = 0
            for i in range(n):
                seed = args.start_seed + i
                text = generate_random(seed=seed, W=args.W, H=args.H, S=args.S,
                                        P=args.P, Hn=args.Hn, max_M=args.max_M, n_islands=args.n_islands)
                try:
                    mismatches = check_input(text, classes_dir, args.path_cap, args.combo_cap)
                except Exception as e:
                    print(f"seed={seed}: ERROR {e}", file=sys.stderr)
                    fails += 1
                    continue
                if mismatches:
                    fails += 1
                    print(f"seed={seed}: FAIL ({len(mismatches)} route(s))", file=sys.stderr)
                    for m in mismatches:
                        print(f"  route {m['route_idx']} items={m['items']}: "
                              f"dist java={m['java_dist']} true={m['true_dist']} | "
                              f"seen java={m['java_seen']} true={m['true_seen']}"
                              f"{' (truncated)' if m['truncated'] else ''}", file=sys.stderr)
                    print("--- reproducing input ---", file=sys.stderr)
                    print(text, file=sys.stderr)
                    if n == 1:
                        break
            print(f"{n - fails}/{n} passed", file=sys.stderr)
            sys.exit(1 if fails else 0)
    finally:
        pass
