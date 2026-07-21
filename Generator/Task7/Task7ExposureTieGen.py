"""
Task7ExposureTieGen.py — Task7 露出スコア(exposure)の非対称バグ 生成器/検証器

── 当初の狙いとの食い違い ──
Step2として当初想定していたのは「入口側区間・出口側区間の両方から到達できる
"お得な棚"が複数あるとき、どちらの区間がそれを拾うべきか」という、区間をまたいだ
タイブレークの検証だった。しかし実際に調べると、Task7のグリッドは入口・出口が
常にy=0（下端）に固定されているため、ある棚iの「入口→棚i」区間の候補領域(矩形)と
「棚i→出口」区間の候補領域(矩形)は、棚iのx座標を通る1本の列でしか重ならない。
その列上の点同士は常に「鎖」(互いに両立可能)であり「反鎖」(排他的)にはなり得ない
ため、Task6TieBreakGen.py的な「区間をまたいだ排他的タイブレーク」はexposureの
計算では原理的に起こらない（実際にブルートフォースで再現を試みたが、最初に
見えた不一致は全てオラクル側のパス列挙上限(path_cap)超過による偽陽性だった）。

── 代わりに見つかった実バグ ──
Task7.javaの pickupToShelf は「取得マス座標 -> 棚インデックス」の単純な
HashMapで、複数の棚が同じ取得マス座標を持つ場合は後から登録した方で
上書きされる(Task7.java:84-85付近)。

    pickupToShelf = new HashMap<>();
    for (int i = 0; i < S; i++) pickupToShelf.put(key(pickX[i], pickY[i]), i);

exposureの計算(Stage A)は「棚iへの経路上のセルが、棚i自身以外のどれかの棚の
取得マスなら+1」という判定を pickupToShelf.get(c) != i で行っている。
2つの棚が同じ取得マス座標を共有する状況（例: 向かい合う2枚の棚が同じ通路マスから
取得できる背中合わせ配置）では、後から宣言された棚だけが正しく自分自身を
除外でき、先に宣言された棚は「自分の取得マス」を「別の棚」と誤認して
exposureが+1多く出る。

この2枚の棚は物理的に対称（どちらから見ても同じ通路マスを共有している）なので、
本来は同じexposureになるべきだが、実際は宣言順に依存して非対称な値になる。
--case pickup_collision と --case pickup_collision_swapped で、宣言順を
入れ替えるだけでどちらが「割を食う」かが入れ替わることを示す（宣言順に
依存すること自体が、これが正しい仕様ではなくバグである証拠）。

さらに重要なのは、この衝突は Phase3/Input.txt 自体でも実際に発生している点。
Input.txtは壁沿い(左右)と上壁の棚を四隅で接するように配置しており、
左壁の一番上の棚(0 10 E, pickup(1,10))と上壁の一番左の棚(1 11 S, pickup(1,10))
が同じ取得マスを共有してしまっている。実測でも
  1 10 exposure=28 milk   (先に宣言、自己誤認込みで水増しされている)
  1 10 exposure=27 tofu   (後から宣言、正しく自己除外できている)
のように、同じ物理位置にも関わらず異なるexposureが出力される
（--case input_corners で再現。--input-file でPhase3/Input.txt自体も検証可能）。

Usage:
  python3 Task7ExposureTieGen.py                              # ランダムな壁+島レイアウトを標準出力へ
  python3 Task7ExposureTieGen.py --case pickup_collision       # 最小の衝突再現ケース
  python3 Task7ExposureTieGen.py --case pickup_collision --check
  python3 Task7ExposureTieGen.py --case pickup_collision_swapped --check   # 宣言順を入れ替えて非対称が反転することを確認
  python3 Task7ExposureTieGen.py --case input_corners --check              # Input.txt風レイアウトの四隅で再現
  python3 Task7ExposureTieGen.py --input-file ../../Phase3/Input.txt --check   # 実物のInput.txtを直接検証
  python3 Task7ExposureTieGen.py --check --trials 50 --start-seed 0        # ランダムレイアウトで連続フラジング
"""
import argparse
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(__file__))
from Task7BruteForceGen import (  # noqa: E402
    build_clean_layout, ensure_compiled, generate_random, parse_vizdata,
    rand_name, run_task7,
)
import random  # noqa: E402

CASES = {
    'pickup_collision': {
        # shelf alpha(3,1,N)のpickupと shelf beta(3,3,S)のpickupが両方(3,2)になる
        # 最小構成。alphaが先に宣言されているため、自分自身の取得マスを
        # 「betaの取得マス」と誤認し、exposureが1(誤)になる。betaは正しく0。
        'input': """7 6 2
3 1 N
3 3 S
2
alpha
beta
1
1 alpha""",
        'note': ("alpha(3,1,N)とbeta(3,3,S)は取得マスが同じ(3,2)（背中合わせの棚）。"
                  "物理的に対称なので同じexposureになるべきだが、実際は "
                  "alpha=1(誤って自分自身をbetaとして数えている)・beta=0(正しい) "
                  "と非対称になる。pickupToShelfが後勝ちのHashMapであるため。"),
    },
    'pickup_collision_swapped': {
        # pickup_collisionと全く同じ幾何配置で、宣言順だけをbeta->alphaに
        # 入れ替えたもの。非対称の「割を食う側」が反転すれば、これが
        # 宣言順(=HashMapの上書き順)由来のバグであることの証明になる。
        'input': """7 6 2
3 3 S
3 1 N
2
beta
alpha
1
1 alpha""",
        'note': ("pickup_collisionと同じ幾何配置で宣言順のみ入れ替え。"
                  "非対称の向きが反転する(今度はalpha=1・beta=0ではなく逆)ことを"
                  "確認する。もし向きが変わらなければ幾何由来、変われば"
                  "宣言順(実装上のバグ)由来と判定できる。"),
    },
    'input_corners': {
        # Phase3/Input.txtと同じ壁+上壁のレイアウトを縮小再現。四隅で
        # 左右壁の一番端の棚と上壁の端の棚が同じ取得マスを共有してしまう。
        # generate()で動的に作るため、ここでは目印用のnoteだけを持たせる。
        'note': ("Phase3/Input.txtと同じ壁沿いレイアウトの縮小版。左壁の一番上の棚と"
                  "上壁の一番左の棚が同じ取得マスを共有する（右側も同様）。"
                  "実物のInput.txtでも同じ構造で発生済み(1,10)/(10,10)。"),
    },
}


def build_input_corners_case(W=9, H=8):
    """Phase3/Input.txtと同じ「壁沿い」パターンをそのまま使うと、左壁最上段と
    上壁最左列が必ず同じ取得マスで衝突する。build_clean_layout自体がこの
    構造を再現しているので、それをそのまま使う。"""
    shelves = build_clean_layout(W, H, n_islands=0)
    rng = random.Random(0)
    used = set()
    names = []
    for _ in range(len(shelves)):
        n = rand_name(rng, used)
        used.add(n)
        names.append(n)
    lines = [f"{W} {H} {len(shelves)}"]
    for x, y, d in shelves:
        lines.append(f"{x} {y} {d}")
    lines.append(str(len(shelves)))
    lines.extend(names)
    lines.append("0")
    return '\n'.join(lines)


def check_symmetry(input_text, classes_dir):
    """同じ取得マス座標を共有する棚グループを探し、exposureが全員一致しているか
    確認する。物理的に同じ場所を共有する棚同士のexposureが食い違っていれば、
    pickupToShelfの上書きによる自己誤認バグが発現している。"""
    stdout = run_task7(input_text, classes_dir)
    viz = parse_vizdata(stdout)
    groups = defaultdict(list)
    for s in viz['shelves']:
        groups[(s['pickX'], s['pickY'])].append(s)

    problems = []
    for coord, group in groups.items():
        if len(group) < 2:
            continue
        exposures = {s['exposure'] for s in group}
        if len(exposures) > 1:
            problems.append({
                'coord': coord,
                'shelves': [(s['idx'], s['product'], s['exposure']) for s in group],
            })
    return problems


if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task7 exposure asymmetry (pickup collision) generator/checker')
    p.add_argument('--case', choices=list(CASES.keys()), default=None)
    p.add_argument('--input-file', default=None, help='既存の入力ファイル(例: ../../Phase3/Input.txt)をそのまま検証する')
    p.add_argument('--seed', type=int, default=None)
    p.add_argument('--W', type=int, default=None)
    p.add_argument('--H', type=int, default=None)
    p.add_argument('--n-islands', type=int, default=None)
    p.add_argument('--check', action='store_true')
    p.add_argument('--trials', type=int, default=1, help='with --check (ランダム生成時): 連続フラジングする件数')
    p.add_argument('--start-seed', type=int, default=0)
    args = p.parse_args()

    if args.input_file:
        with open(args.input_file, encoding='utf-8') as f:
            text = f.read()
    elif args.case == 'input_corners':
        text = build_input_corners_case(
            W=args.W or 9, H=args.H or 8)
    elif args.case:
        text = CASES[args.case]['input']
    elif args.check and args.trials > 1:
        text = None
    else:
        text = generate_random(seed=args.seed, W=args.W, H=args.H,
                                n_islands=args.n_islands, max_M=1)

    if not args.check:
        print(text)
        if args.case and CASES[args.case].get('note'):
            print('--- note ---', file=sys.stderr)
            print(CASES[args.case]['note'], file=sys.stderr)
        sys.exit(0)

    classes_dir = ensure_compiled()

    def report(label, problems):
        if problems:
            print(f"FAIL [{label}]", file=sys.stderr)
            for pr in problems:
                print(f"  coord={pr['coord']} shelves(idx,product,exposure)={pr['shelves']}", file=sys.stderr)
        else:
            print(f"PASS [{label}]", file=sys.stderr)
        return bool(problems)

    if args.input_file:
        failed = report(args.input_file, check_symmetry(text, classes_dir))
        sys.exit(1 if failed else 0)
    elif args.case:
        failed = report(args.case, check_symmetry(text, classes_dir))
        sys.exit(1 if failed else 0)
    else:
        n = args.trials
        fails = 0
        for i in range(n):
            seed = args.start_seed + i
            text = generate_random(seed=seed, W=args.W, H=args.H,
                                    n_islands=args.n_islands, max_M=1)
            problems = check_symmetry(text, classes_dir)
            if problems:
                fails += 1
                print(f"seed={seed}: FAIL ({len(problems)} coord(s))", file=sys.stderr)
                for pr in problems:
                    print(f"  coord={pr['coord']} shelves(idx,product,exposure)={pr['shelves']}", file=sys.stderr)
        print(f"{n - fails}/{n} passed", file=sys.stderr)
        sys.exit(1 if fails else 0)
