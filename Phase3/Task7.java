import java.util.*;
import java.io.*;

public class Task7 {
    static int W, H, S, P, Hn;
    static int[] pickX, pickY;      // 棚iの取得マス座標
    static String[] productNames;   // 商品名（P個）
    static int[][] history;         // 購買履歴（商品インデックスの配列、Hn件）

    static int[] startDist, exitDist;   // startDist[i] = 入口->棚iの取得マス, exitDist[i] = 棚iの取得マス->出口
    static int[][] interDist;           // interDist[i][j] = 棚iの取得マス->棚jの取得マス
    static int[][] distFromStart, distFromExit;
    static int[][][] distFromPickup;    // 各棚の取得マスを始点としたBFS距離マップ
    // 取得マス座標 -> そのマスを共有する棚インデックスの一覧。
    // 背中合わせの棚などで複数の棚が同じ取得マスを持つことがあるため、
    // 1マスにつき1棚だけを覚える方式（後勝ちで上書き）ではなく、
    // マスを共有する棚を全部保持する。
    static Map<Long, List<Integer>> pickupToShelf;

    // 各BFS始点について、「その始点からの最短距離を保ったまま、通過できる棚の数を
    // 最大化する経路」を復元するための後退方向テーブル（0-3=GridMap.dx/dy添字, -1=始点）。
    // 棚の座標配置だけで決まる値なので、商品→棚の割り当てが変わるStage Dのループ内では
    // 再計算せず、セットアップ時に一度だけ計算して使い回す。
    static int[][] parentDirFromStart, parentDirFromExit;
    static int[][][] parentDirFromPickup;

    static final int INF = Integer.MAX_VALUE / 2;
    static final int TOP_PAIR_COUNT = 30; // Stage Dで局所探索の対象にする上位共起ペア数
    static final int MAX_ROUNDS = 5;      // Stage Dの反復上限（全ペアで改善なしなら早期終了）

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        StringTokenizer st = new StringTokenizer(br.readLine());
        W = Integer.parseInt(st.nextToken());
        H = Integer.parseInt(st.nextToken());
        S = Integer.parseInt(st.nextToken());

        pickX = new int[S]; pickY = new int[S];
        boolean[][] blocked = new boolean[W][H];

        for (int i = 0; i < S; i++) {
            st = new StringTokenizer(br.readLine());
            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());
            char d = st.nextToken().charAt(0);
            blocked[x][y] = true;
            if      (d == 'E') { pickX[i] = x + 1; pickY[i] = y;     }
            else if (d == 'W') { pickX[i] = x - 1; pickY[i] = y;     }
            else if (d == 'N') { pickX[i] = x;     pickY[i] = y + 1; }
            else               { pickX[i] = x;     pickY[i] = y - 1; }
        }

        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0]     = false;
        blocked[W - 2][0] = false;

        P = Integer.parseInt(br.readLine().trim());
        productNames = new String[P];
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < P; i++) {
            String name = br.readLine().trim();
            productNames[i] = name;
            nameToIdx.put(name, i);
        }

        Hn = Integer.parseInt(br.readLine().trim());
        history = new int[Hn][];
        for (int r = 0; r < Hn; r++) {
            st = new StringTokenizer(br.readLine());
            int m = Integer.parseInt(st.nextToken());
            Set<Integer> items = new LinkedHashSet<>();
            for (int j = 0; j < m; j++) items.add(nameToIdx.get(st.nextToken()));
            int[] arr = new int[items.size()];
            int k = 0;
            for (int v : items) arr[k++] = v;
            history[r] = arr;
        }

        // ---- 全棚間の距離テーブル（入口/出口/棚同士）。Task6と同じ構造 ----
        distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);
        distFromPickup = new int[S][][];
        for (int i = 0; i < S; i++) distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);

        startDist = new int[S];
        exitDist  = new int[S];
        interDist = new int[S][S];
        for (int i = 0; i < S; i++) {
            startDist[i] = distFromStart[pickX[i]][pickY[i]];
            exitDist[i]  = distFromExit[pickX[i]][pickY[i]];
            for (int j = 0; j < S; j++) interDist[i][j] = distFromPickup[i][pickX[j]][pickY[j]];
        }

        pickupToShelf = new HashMap<>();
        for (int i = 0; i < S; i++) {
            pickupToShelf.computeIfAbsent(key(pickX[i], pickY[i]), k -> new ArrayList<>()).add(i);
        }

        // ---- 経路復元用の後退方向テーブルを一度だけ計算（Task7.java.orig_backupとの主な差分） ----
        parentDirFromStart = computeParentDir(distFromStart, W, H);
        parentDirFromExit  = computeParentDir(distFromExit, W, H);
        parentDirFromPickup = new int[S][][];
        for (int i = 0; i < S; i++) parentDirFromPickup[i] = computeParentDir(distFromPickup[i], W, H);

        // ---- Stage A: 棚ごとの露出スコア（単独で買いに行った時に何棚分「見える」か） ----
        int[] exposure = new int[S];
        for (int i = 0; i < S; i++) {
            Set<Long> cells = new HashSet<>();
            addPathCells(cells, distFromStart, parentDirFromStart, pickX[i], pickY[i]); // 入口 -> 棚i
            addPathCells(cells, distFromExit,  parentDirFromExit,  pickX[i], pickY[i]); // 棚i -> 出口
            int cnt = 0;
            for (long c : cells) {
                List<Integer> here = pickupToShelf.get(c);
                if (here == null) continue;
                for (int sh : here) if (sh != i) cnt++;
            }
            exposure[i] = cnt;
        }

        // ---- Stage B: 商品ごとの需要スコア（Task1相当の個別頻度 + Task2/4相当の共起頻度合計） ----
        int[] individualFreq = new int[P];
        int[][] pairFreq = new int[P][P];
        for (int[] items : history) {
            for (int a : items) individualFreq[a]++;
            for (int a = 0; a < items.length; a++)
                for (int b = a + 1; b < items.length; b++) {
                    pairFreq[items[a]][items[b]]++;
                    pairFreq[items[b]][items[a]]++;
                }
        }
        final double LAMBDA = 1.0; // ペア共起の重み。大きくするほどペア購入の多い商品を優先して奥に配置する
        double[] demand = new double[P];
        for (int p = 0; p < P; p++) {
            int pairSum = 0;
            for (int q = 0; q < P; q++) pairSum += pairFreq[p][q];
            demand[p] = individualFreq[p] + LAMBDA * pairSum;
        }

        // ---- Stage C: 並べ替え不等式による貪欲マッチング ----
        // demand降順の商品と、exposure降順の棚を同順位同士で対応させると
        // Σ demand(p)*exposure(shelf(p)) が最大化される（並べ替え不等式）。
        Integer[] prodOrder = new Integer[P];
        for (int i = 0; i < P; i++) prodOrder[i] = i;
        Arrays.sort(prodOrder, (a, b) -> Double.compare(demand[b], demand[a]));

        Integer[] shelfOrder = new Integer[S];
        for (int i = 0; i < S; i++) shelfOrder[i] = i;
        Arrays.sort(shelfOrder, (a, b) -> Integer.compare(exposure[b], exposure[a]));

        int limit = Math.min(P, S);
        int[] productToShelf = new int[P];
        Arrays.fill(productToShelf, -1);
        int[] shelfToProduct = new int[S];
        Arrays.fill(shelfToProduct, -1);
        for (int k = 0; k < limit; k++) {
            int prod = prodOrder[k];
            int shelf = shelfOrder[k];
            productToShelf[prod] = shelf;
            shelfToProduct[shelf] = prod;
        }

        long[] stageCSplit = evaluateSplit(productToShelf);

        // ---- Stage D: 上位共起ペアを対象にした局所探索（swap）でペア相乗効果を追加 ----
        List<int[]> topPairs = new ArrayList<>();
        for (int p = 0; p < P; p++)
            for (int q = p + 1; q < P; q++)
                if (pairFreq[p][q] > 0) topPairs.add(new int[]{p, q, pairFreq[p][q]});
        topPairs.sort((a, b) -> Integer.compare(b[2], a[2]));
        if (topPairs.size() > TOP_PAIR_COUNT) topPairs = topPairs.subList(0, TOP_PAIR_COUNT);

        runStageD(topPairs, productToShelf, shelfToProduct);

        long[] stageDSplit = evaluateSplit(productToShelf);

        // ---- 出力：棚の配置（Stage D適用後） ----
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < S; i++) {
            String name = (shelfToProduct[i] >= 0) ? productNames[shelfToProduct[i]] : "-";
            sb.append(pickX[i]).append(' ').append(pickY[i]).append(' ')
              .append("exposure=").append(exposure[i]).append(' ')
              .append(name).append('\n');
        }

        sb.append("---\n");
        appendEvalLine(sb, "Stage A-C only", stageCSplit);
        appendEvalLine(sb, "Stage A-C + D ", stageDSplit);

        sb.append("===VIZDATA_JSON===\n");
        sb.append(buildVizJson(productToShelf, shelfToProduct, exposure, blocked));
        sb.append('\n');

        System.out.print(sb);
    }

    // 可視化用：グリッド・棚配置・各履歴の実巡回ルートをJSONとして書き出す
    static String buildVizJson(int[] productToShelf, int[] shelfToProduct, int[] exposure, boolean[][] blocked) {
        StringBuilder j = new StringBuilder();
        j.append("{\"W\":").append(W).append(",\"H\":").append(H).append(',');
        j.append("\"entrance\":[1,0],\"exit\":[").append(W - 2).append(",0],");
        j.append("\"blocked\":[");
        boolean first = true;
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
            if (blocked[x][y]) {
                if (!first) j.append(',');
                j.append('[').append(x).append(',').append(y).append(']');
                first = false;
            }
        }
        j.append("],");
        j.append("\"shelves\":[");
        for (int i = 0; i < S; i++) {
            if (i > 0) j.append(',');
            String name = (shelfToProduct[i] >= 0) ? productNames[shelfToProduct[i]] : null;
            j.append("{\"idx\":").append(i)
             .append(",\"pickX\":").append(pickX[i])
             .append(",\"pickY\":").append(pickY[i])
             .append(",\"exposure\":").append(exposure[i])
             .append(",\"product\":").append(name == null ? "null" : ("\"" + name + "\""))
             .append('}');
        }
        j.append("],");
        j.append("\"routes\":[");
        for (int r = 0; r < Hn; r++) {
            if (r > 0) j.append(',');
            int[] items = history[r];
            int[] shelves = new int[items.length];
            for (int k = 0; k < items.length; k++) shelves[k] = productToShelf[items[k]];
            // 出力ルートの seen は厳密解を使う（区間をまたいだ棚の重複除去まで最大化）。
            // Stage Dの最適化ループは従来の simulateTripAndCountShelves のままなので性能に影響しない。
            TripPlan plan = planTripExact(shelves);
            List<int[]> path = plan.path;
            int seen = plan.seen;
            j.append("{\"idx\":").append(r).append(",\"items\":[");
            for (int k = 0; k < items.length; k++) {
                if (k > 0) j.append(',');
                j.append('"').append(productNames[items[k]]).append('"');
            }
            j.append("],\"seen\":").append(seen).append(",\"path\":[");
            for (int k = 0; k < path.size(); k++) {
                if (k > 0) j.append(',');
                j.append('[').append(path.get(k)[0]).append(',').append(path.get(k)[1]).append(']');
            }
            j.append("]}");
        }
        j.append("]}");
        return j.toString();
    }

    // dist上のセルをtxtyから逆順に辿ってリスト化する（順序付き版）。
    // parentDirはcomputeParentDirで事前計算した「棚を最も多く通る経路」の後退方向。
    static List<int[]> pathCellsList(int[][] dist, int[][] parentDir, int tx, int ty) {
        List<int[]> list = new ArrayList<>();
        int x = tx, y = ty;
        list.add(new int[]{x, y});
        while (dist[x][y] != 0) {
            int d = parentDir[x][y];
            if (d < 0) break;
            x = x + GridMap.dx[d];
            y = y + GridMap.dy[d];
            list.add(new int[]{x, y});
        }
        return list;
    }

    // dist（あるBFS始点からの距離マップ）が与えられたとき、始点から各マスへの
    // 「最短距離を保ったまま通過できる棚の数」を最大化する経路の、後退方向を求める。
    // BFSと同じ距離の昇順（レイヤー順）に処理する前向きDP:
    //   best[c] = max( best[predecessor] ) + (cが棚の取得マスなら1)
    // 距離が1減る隣接マスは複数ありうるが、bestが最大の方をparentDirとして採用する。
    // 棚の座標配置だけで決まりStage Dの割り当てには依存しないため、セットアップ時に
    // 一度だけ計算すればよく、Stage Dのホットループを重くしない。
    static int[][] computeParentDir(int[][] dist, int Wp, int Hp) {
        int[][] best = new int[Wp][Hp];
        int[][] parentDir = new int[Wp][Hp];
        for (int[] row : best) Arrays.fill(row, -1);
        for (int[] row : parentDir) Arrays.fill(row, -1);

        int maxDist = 0;
        for (int x = 0; x < Wp; x++)
            for (int y = 0; y < Hp; y++)
                if (dist[x][y] > maxDist) maxDist = dist[x][y];

        List<List<int[]>> buckets = new ArrayList<>(maxDist + 1);
        for (int k = 0; k <= maxDist; k++) buckets.add(new ArrayList<>());
        for (int x = 0; x < Wp; x++)
            for (int y = 0; y < Hp; y++)
                if (dist[x][y] >= 0) buckets.get(dist[x][y]).add(new int[]{x, y});

        for (int[] c : buckets.get(0)) {
            List<Integer> here0 = pickupToShelf.get(key(c[0], c[1]));
            best[c[0]][c[1]] = (here0 != null) ? here0.size() : 0;
        }
        for (int k = 1; k <= maxDist; k++) {
            for (int[] c : buckets.get(k)) {
                int x = c[0], y = c[1];
                int bestPred = -1, bestDir = -1;
                for (int d = 0; d < 4; d++) {
                    int nx = x + GridMap.dx[d], ny = y + GridMap.dy[d];
                    if (nx < 0 || ny < 0 || nx >= Wp || ny >= Hp) continue;
                    if (dist[nx][ny] != k - 1) continue;
                    if (best[nx][ny] > bestPred) { bestPred = best[nx][ny]; bestDir = d; }
                }
                List<Integer> here = pickupToShelf.get(key(x, y));
                int selfShelf = (here != null) ? here.size() : 0;
                best[x][y] = bestPred + selfShelf;
                parentDir[x][y] = bestDir;
            }
        }
        return parentDir;
    }

    // ==================== 出力ルート専用の厳密解 ====================
    // buildVizJson（履歴ごとに1回だけ実行）で seen を厳密に求めるための一式。
    // Stage Dのホットループでは使わない（simulateTripAndCountShelvesは従来のまま）ので
    // 最適化の実行時間には影響しない。
    //
    // 考え方: あるトリップについて「入口→(要求棚を全部)→出口の最短距離」を保ったまま
    //   通過できる棚（=商品）の枚数を最大化する。区間ごとに独立して最大化すると、
    //   区間をまたいで同じ棚を二重に数えられず和集合の最大化にならない。そこで
    //   各区間で「同時に通過可能な棚集合」を経路列挙なしのDPで全列挙し、区間をまたいだ
    //   和集合が最大になる組み合わせを選ぶ。訪問順は最短距離を実現するものだけを対象。
    static final class TripPlan { int dist; int seen; List<int[]> path; }

    static final int EXACT_MAX_ITEMS = 8;      // これを超える履歴は順列全探索が非現実的なので従来法にフォールバック
    static final int EXACT_MAX_BONUS = 62;     // ボーナス棚がlong mask(63bit)上限を超える場合は従来法にフォールバック
    static final int EXACT_COMBO_CAP = 1 << 16; // 区間結合時に保持する和集合マスク数の上限

    static int[][] distMapForStop(int stop) {
        if (stop == -1) return distFromStart;   // 入口
        if (stop == -2) return distFromExit;    // 出口
        return distFromPickup[stop];            // 棚
    }
    static int stopX(int stop) { return stop == -1 ? 1 : stop == -2 ? (W - 2) : pickX[stop]; }
    static int stopY(int stop) { return stop == -1 ? 0 : stop == -2 ? 0     : pickY[stop]; }

    // stopA -> stopB の最短経路DAG上で、各セルに「そこへ到達するまでに通過しうる
    // ボーナス棚集合（bitmask）の全パターン」をレイヤーDPで求める。bidxはボーナスセル
    // ->ビット位置。経路復元にも使うので、終点だけでなく全セルのマスク集合を返す。
    static Map<Long, Set<Long>> segMasksWithCells(int a, int b, Map<Long, Integer> bidx) {
        int[][] dA = distMapForStop(a), dB = distMapForStop(b);
        int ax = stopX(a), ay = stopY(a), bx = stopX(b), by = stopY(b);
        int D = dA[bx][by];
        Map<Long, Set<Long>> masks = new HashMap<>();
        if (D < 0) return masks;

        List<int[]> valid = new ArrayList<>();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                if (dA[x][y] >= 0 && dB[x][y] >= 0 && dA[x][y] + dB[x][y] == D)
                    valid.add(new int[]{x, y});
        valid.sort((p, q) -> Integer.compare(dA[p[0]][p[1]], dA[q[0]][q[1]]));

        for (int[] c : valid) masks.put(key(c[0], c[1]), new HashSet<>());
        masks.get(key(ax, ay)).add(bonusBit(ax, ay, bidx));

        for (int[] c : valid) {
            int x = c[0], y = c[1];
            if (x == ax && y == ay) continue;
            int cd = dA[x][y];
            Set<Long> acc = new HashSet<>();
            for (int d = 0; d < 4; d++) {
                int ux = x + GridMap.dx[d], uy = y + GridMap.dy[d];
                if (ux < 0 || uy < 0 || ux >= W || uy >= H) continue;
                if (dA[ux][uy] != cd - 1) continue;
                Set<Long> um = masks.get(key(ux, uy));
                if (um != null) acc.addAll(um);
            }
            long bb = bonusBit(x, y, bidx);
            Set<Long> cm = masks.get(key(x, y));
            for (long mm : acc) cm.add(mm | bb);
        }
        return masks;
    }

    // 終点で到達可能なボーナス棚集合（区間単体の全パターン）
    static Set<Long> segAchievableMasks(int a, int b, Map<Long, Integer> bidx, Set<Long> requiredCells) {
        Map<Long, Set<Long>> masks = segMasksWithCells(a, b, bidx);
        Set<Long> bm = masks.get(key(stopX(b), stopY(b)));
        if (bm == null || bm.isEmpty()) { Set<Long> s = new HashSet<>(); s.add(0L); return s; }
        return bm;
    }

    // stopA -> stopB の最短経路で、ボーナス棚集合が targetMask となる具体的な経路を
    // segMasksWithCellsの結果から後退復元する。返すのは A から B へ向かう座標列。
    static List<int[]> reconstructSegPath(int a, int b, long targetMask,
                                           Map<Long, Set<Long>> masks, Map<Long, Integer> bidx) {
        int[][] dA = distMapForStop(a);
        int ax = stopX(a), ay = stopY(a), bx = stopX(b), by = stopY(b);
        List<int[]> rev = new ArrayList<>();   // B から A へ
        int cx = bx, cy = by;
        long cur = targetMask;
        rev.add(new int[]{cx, cy});
        while (!(cx == ax && cy == ay)) {
            long prevMask = cur & ~bonusBit(cx, cy, bidx);
            int cd = dA[cx][cy];
            boolean moved = false;
            for (int d = 0; d < 4; d++) {
                int ux = cx + GridMap.dx[d], uy = cy + GridMap.dy[d];
                if (ux < 0 || uy < 0 || ux >= W || uy >= H) continue;
                if (dA[ux][uy] != cd - 1) continue;
                Set<Long> um = masks.get(key(ux, uy));
                if (um != null && um.contains(prevMask)) {
                    cx = ux; cy = uy; cur = prevMask; rev.add(new int[]{cx, cy}); moved = true; break;
                }
            }
            if (!moved) break;   // 理論上到達するはず（保険）
        }
        Collections.reverse(rev);   // A から B へ
        return rev;
    }

    static long bonusBit(int x, int y, Map<Long, Integer> bidx) {
        Integer i = bidx.get(key(x, y));
        return (i == null) ? 0L : (1L << i);
    }

    // マスク集合が大きくなりすぎたら、popcount上位のものだけ残す（和集合最大化には十分）
    static Set<Long> capMasks(Set<Long> masks, Map<Long, Integer> bidx) {
        if (masks.size() <= EXACT_COMBO_CAP) return masks;
        long[] inv = invBidx(bidx);
        List<Long> list = new ArrayList<>(masks);
        list.sort((p, q) -> Integer.compare(maskWeight(q, inv), maskWeight(p, inv)));
        return new HashSet<>(list.subList(0, EXACT_COMBO_CAP));
    }

    static long[] invBidx(Map<Long, Integer> bidx) {
        long[] inv = new long[bidx.size()];
        for (Map.Entry<Long, Integer> e : bidx.entrySet()) inv[e.getValue()] = e.getKey();
        return inv;
    }

    // maskに含まれるボーナスセルの棚枚数合計（衝突マスは複数枚として数える）
    static int maskWeight(long mask, long[] inv) {
        int w = 0;
        for (int i = 0; i < inv.length; i++) {
            if ((mask >> i & 1L) != 0) {
                List<Integer> here = pickupToShelf.get(inv[i]);
                if (here != null) w += here.size();
            }
        }
        return w;
    }

    // トリップの厳密解（seen最大値と、その最短距離を実現する経路）を返す
    static TripPlan planTripExact(int[] shelves) {
        TripPlan res = new TripPlan();
        int m = shelves.length;
        if (m == 0) { res.dist = 0; res.seen = 0; res.path = new ArrayList<>(); return res; }

        // 大きすぎる履歴は順列全探索が非現実的なので従来法にフォールバック
        if (m > EXACT_MAX_ITEMS) {
            res.path = buildRoutePath(shelves);
            res.seen = simulateTripAndCountShelves(shelves);
            res.dist = res.path.isEmpty() ? -1 : res.path.size() - 1;
            return res;
        }

        Set<Long> requiredCells = new HashSet<>();
        for (int s : shelves) requiredCells.add(key(pickX[s], pickY[s]));

        // 最短距離を実現する訪問順を全列挙
        List<int[]> minOrders = new ArrayList<>();
        int[] best = { Integer.MAX_VALUE };
        permuteOrders(shelves, 0, new int[m], new boolean[m], minOrders, best);
        if (minOrders.isEmpty()) {   // 到達不能
            res.dist = -1; res.seen = 0; res.path = new ArrayList<>(); return res;
        }
        res.dist = best[0];

        int reqW = 0;
        for (long ck : requiredCells) {
            List<Integer> here = pickupToShelf.get(ck);
            if (here != null) reqW += here.size();
        }

        int bestSeen = -1;
        int[] bestOrder = minOrders.get(0);
        boolean fellBack = false;

        for (int[] order : minOrders) {
            int[] stops = orderToStops(order);
            Map<Long, Integer> bidx = buildBidxForStops(stops, requiredCells);
            if (bidx.size() > EXACT_MAX_BONUS) { fellBack = true; break; }  // long maskに収まらない

            Set<Long> reach = new HashSet<>();
            reach.add(0L);
            for (int seg = 0; seg <= m; seg++) {
                Set<Long> sm = segAchievableMasks(stops[seg], stops[seg + 1], bidx, requiredCells);
                Set<Long> nxt = new HashSet<>();
                for (long r : reach) for (long mm : sm) nxt.add(r | mm);
                reach = capMasks(nxt, bidx);
            }

            long[] inv = invBidx(bidx);
            int bw = 0;
            for (long mm : reach) { int w = maskWeight(mm, inv); if (w > bw) bw = w; }
            int total = reqW + bw;
            if (total > bestSeen) { bestSeen = total; bestOrder = order; }
        }

        if (fellBack) {
            res.path = buildRoutePath(shelves);
            res.seen = simulateTripAndCountShelves(shelves);
            res.dist = res.path.isEmpty() ? -1 : res.path.size() - 1;
            return res;
        }

        res.seen = bestSeen;
        // seenを実現する経路を復元（区間ごとに最適なボーナス集合を通る具体的な経路）
        res.path = reconstructOptimalPath(bestOrder, requiredCells);
        return res;
    }

    static int[] orderToStops(int[] order) {
        int m = order.length;
        int[] stops = new int[m + 2];
        stops[0] = -1; stops[m + 1] = -2;
        for (int i = 0; i < m; i++) stops[i + 1] = order[i];
        return stops;
    }

    // 訪問順の各区間に登場するボーナスセル（要求棚でないpickupセル）にビット位置を割り当てる
    static Map<Long, Integer> buildBidxForStops(int[] stops, Set<Long> requiredCells) {
        int segCount = stops.length - 1;
        Map<Long, Integer> bidx = new HashMap<>();
        for (int seg = 0; seg < segCount; seg++) {
            int a = stops[seg], b = stops[seg + 1];
            int[][] dA = distMapForStop(a), dB = distMapForStop(b);
            int D = dA[stopX(b)][stopY(b)];
            if (D < 0) continue;
            for (int x = 0; x < W; x++)
                for (int y = 0; y < H; y++) {
                    if (dA[x][y] >= 0 && dB[x][y] >= 0 && dA[x][y] + dB[x][y] == D) {
                        long ck = key(x, y);
                        if (pickupToShelf.containsKey(ck) && !requiredCells.contains(ck) && !bidx.containsKey(ck))
                            bidx.put(ck, bidx.size());
                    }
                }
        }
        return bidx;
    }

    // 与えられた訪問順で、区間をまたいだボーナス棚の和集合を最大化する組み合わせを
    // 後戻り情報つきで求め、各区間で選んだマスクどおりの具体的な経路を復元して連結する。
    static List<int[]> reconstructOptimalPath(int[] order, Set<Long> requiredCells) {
        int m = order.length;
        int[] stops = orderToStops(order);
        Map<Long, Integer> bidx = buildBidxForStops(stops, requiredCells);

        // 各区間のセル別マスクと終点マスク集合を用意
        List<Map<Long, Set<Long>>> segCellMasks = new ArrayList<>();
        List<List<Long>> segEndMasks = new ArrayList<>();
        for (int seg = 0; seg <= m; seg++) {
            Map<Long, Set<Long>> mc = segMasksWithCells(stops[seg], stops[seg + 1], bidx);
            segCellMasks.add(mc);
            Set<Long> end = mc.get(key(stopX(stops[seg + 1]), stopY(stops[seg + 1])));
            if (end == null || end.isEmpty()) { end = new HashSet<>(); end.add(0L); }
            segEndMasks.add(new ArrayList<>(end));
        }

        // 区間結合DP（後戻り情報つき）: union -> {前のunion, その区間で選んだマスク}
        long[] inv = invBidx(bidx);
        List<Map<Long, long[]>> back = new ArrayList<>();   // 区間ごとの後戻り表
        Map<Long, Integer> reach = new HashMap<>();          // union -> weight
        reach.put(0L, 0);
        for (int seg = 0; seg <= m; seg++) {
            Map<Long, long[]> bk = new HashMap<>();
            Map<Long, Integer> nxt = new HashMap<>();
            for (Map.Entry<Long, Integer> e : reach.entrySet()) {
                long prevUnion = e.getKey();
                for (long sm : segEndMasks.get(seg)) {
                    long nu = prevUnion | sm;
                    if (!nxt.containsKey(nu)) {
                        nxt.put(nu, maskWeight(nu, inv));
                        bk.put(nu, new long[]{prevUnion, sm});
                    }
                }
            }
            // capMasksと同じ発想で上位のみ保持
            if (nxt.size() > EXACT_COMBO_CAP) {
                final Map<Long, Integer> nxtRef = nxt;
                List<Long> keys = new ArrayList<>(nxt.keySet());
                keys.sort((p, q) -> Integer.compare(nxtRef.get(q), nxtRef.get(p)));
                Map<Long, Integer> trimmed = new HashMap<>();
                Map<Long, long[]> trimmedBk = new HashMap<>();
                for (int i = 0; i < EXACT_COMBO_CAP; i++) { trimmed.put(keys.get(i), nxt.get(keys.get(i))); trimmedBk.put(keys.get(i), bk.get(keys.get(i))); }
                nxt = trimmed; bk = trimmedBk;
            }
            reach = nxt; back.add(bk);
        }

        // 最良のunionを選ぶ
        long bestUnion = 0L; int bestW = -1;
        for (Map.Entry<Long, Integer> e : reach.entrySet())
            if (e.getValue() > bestW) { bestW = e.getValue(); bestUnion = e.getKey(); }

        // 後戻りして各区間で選んだマスクを復元
        long[] segChosen = new long[m + 1];
        long cur = bestUnion;
        for (int seg = m; seg >= 0; seg--) {
            long[] bp = back.get(seg).get(cur);
            segChosen[seg] = bp[1];
            cur = bp[0];
        }

        // 各区間の経路を復元して連結
        List<int[]> path = new ArrayList<>();
        for (int seg = 0; seg <= m; seg++) {
            List<int[]> segPath = reconstructSegPath(stops[seg], stops[seg + 1], segChosen[seg],
                                                      segCellMasks.get(seg), bidx);
            if (seg == 0) path.addAll(segPath);
            else path.addAll(segPath.subList(1, segPath.size()));
        }
        return path;
    }

    // 最短距離を実現する訪問順を全て集める（順列全探索、mが小さいときのみ）
    static void permuteOrders(int[] shelves, int depth, int[] cur, boolean[] used,
                               List<int[]> minOrders, int[] best) {
        int m = shelves.length;
        if (depth == m) {
            int total = 0; boolean ok = true;
            int d0 = startDist[cur[0]];
            if (d0 < 0) ok = false; else total += d0;
            for (int i = 1; ok && i < m; i++) {
                int d = interDist[cur[i - 1]][cur[i]];
                if (d < 0) ok = false; else total += d;
            }
            if (ok) { int de = exitDist[cur[m - 1]]; if (de < 0) ok = false; else total += de; }
            if (ok) {
                if (total < best[0]) { best[0] = total; minOrders.clear(); minOrders.add(cur.clone()); }
                else if (total == best[0]) minOrders.add(cur.clone());
            }
            return;
        }
        for (int i = 0; i < m; i++) {
            if (used[i]) continue;
            used[i] = true; cur[depth] = shelves[i];
            permuteOrders(shelves, depth + 1, cur, used, minOrders, best);
            used[i] = false;
        }
    }

    // 与えられた訪問順に沿って最短経路を復元する（距離は最小、棚を多く通る向きに寄せる）
    static List<int[]> buildRoutePathForOrder(int[] order) {
        List<int[]> path = new ArrayList<>();
        int m = order.length;
        if (m == 0) return path;
        int first = order[0];
        List<int[]> seg = pathCellsList(distFromStart, parentDirFromStart, pickX[first], pickY[first]);
        Collections.reverse(seg);
        path.addAll(seg);
        for (int k = 1; k < m; k++) {
            int prev = order[k - 1], cur = order[k];
            seg = pathCellsList(distFromPickup[prev], parentDirFromPickup[prev], pickX[cur], pickY[cur]);
            Collections.reverse(seg);
            path.addAll(seg.subList(1, seg.size()));
        }
        int last = order[m - 1];
        seg = pathCellsList(distFromExit, parentDirFromExit, pickX[last], pickY[last]);
        path.addAll(seg.subList(1, seg.size()));
        return path;
    }

    // 履歴1件分の実際の巡回順路（座標列、入口→…→出口）を復元する。simulateTripAndCountShelvesと同じDPを使う
    static List<int[]> buildRoutePath(int[] shelves) {
        int m = shelves.length;
        int full = 1 << m;
        int[][] dp = new int[full][m];
        int[][] parent = new int[full][m];
        for (int[] row : dp) Arrays.fill(row, INF);
        for (int[] row : parent) Arrays.fill(row, -1);
        for (int i = 0; i < m; i++) {
            int sd = startDist[shelves[i]];
            if (sd >= 0) dp[1 << i][i] = sd;
        }
        for (int mask = 1; mask < full; mask++) {
            for (int i = 0; i < m; i++) {
                if ((mask & (1 << i)) == 0) continue;
                int di = dp[mask][i];
                if (di >= INF) continue;
                for (int j = 0; j < m; j++) {
                    if ((mask & (1 << j)) != 0) continue;
                    int d = interDist[shelves[i]][shelves[j]];
                    if (d < 0) continue;
                    int nmask = mask | (1 << j);
                    int cand = di + d;
                    if (cand < dp[nmask][j]) { dp[nmask][j] = cand; parent[nmask][j] = i; }
                }
            }
        }
        int bestEnd = -1, bestVal = INF;
        for (int i = 0; i < m; i++) {
            if (dp[full - 1][i] >= INF) continue;
            int ed = exitDist[shelves[i]];
            if (ed < 0) continue;
            int total = dp[full - 1][i] + ed;
            if (total < bestVal) { bestVal = total; bestEnd = i; }
        }
        if (bestEnd == -1) return new ArrayList<>();

        int[] orderIdx = new int[m];
        int curMask = full - 1, curI = bestEnd;
        for (int k = m - 1; k >= 0; k--) {
            orderIdx[k] = curI;
            int prevI = parent[curMask][curI];
            curMask &= ~(1 << curI);
            curI = prevI;
        }

        List<int[]> path = new ArrayList<>();
        int firstShelf = shelves[orderIdx[0]];
        List<int[]> seg = pathCellsList(distFromStart, parentDirFromStart, pickX[firstShelf], pickY[firstShelf]);
        Collections.reverse(seg);
        path.addAll(seg);
        for (int k = 1; k < m; k++) {
            int prevShelf = shelves[orderIdx[k - 1]];
            int curShelf  = shelves[orderIdx[k]];
            seg = pathCellsList(distFromPickup[prevShelf], parentDirFromPickup[prevShelf], pickX[curShelf], pickY[curShelf]);
            Collections.reverse(seg);
            path.addAll(seg.subList(1, seg.size()));
        }
        int lastShelf = shelves[orderIdx[m - 1]];
        seg = pathCellsList(distFromExit, parentDirFromExit, pickX[lastShelf], pickY[lastShelf]);
        path.addAll(seg.subList(1, seg.size()));
        return path;
    }

    // split = {singleTotal, singleCount, multiTotal, multiCount}
    static void appendEvalLine(StringBuilder sb, String label, long[] split) {
        long singleTotal = split[0], singleCount = split[1], multiTotal = split[2], multiCount = split[3];
        long grandTotal = singleTotal + multiTotal;
        long grandCount = singleCount + multiCount;
        sb.append(label)
          .append(" total=").append(grandTotal)
          .append(" avg=").append(grandCount == 0 ? 0 : (double) grandTotal / grandCount)
          .append(" | single-item trips: total=").append(singleTotal)
          .append(" avg=").append(singleCount == 0 ? 0 : (double) singleTotal / singleCount)
          .append(" | multi-item trips: total=").append(multiTotal)
          .append(" avg=").append(multiCount == 0 ? 0 : (double) multiTotal / multiCount)
          .append('\n');
    }

    static long[] evaluateSplit(int[] productToShelf) {
        long singleTotal = 0, singleCount = 0, multiTotal = 0, multiCount = 0;
        for (int[] items : history) {
            int[] shelves = new int[items.length];
            for (int j = 0; j < items.length; j++) shelves[j] = productToShelf[items[j]];
            int seen = simulateTripAndCountShelves(shelves);
            if (items.length <= 1) { singleTotal += seen; singleCount++; }
            else                   { multiTotal  += seen; multiCount++; }
        }
        return new long[]{singleTotal, singleCount, multiTotal, multiCount};
    }

    static long evaluateTotal(int[] productToShelf) {
        long total = 0;
        for (int[] items : history) {
            int[] shelves = new int[items.length];
            for (int j = 0; j < items.length; j++) shelves[j] = productToShelf[items[j]];
            total += simulateTripAndCountShelves(shelves);
        }
        return total;
    }

    // 上位共起ペアそれぞれについて、片方の商品を他の商品とswapして全体スコアが改善するなら採用する山登り法。
    // 改善が見つからなくなるかMAX_ROUNDSに達したら終了する。
    static void runStageD(List<int[]> topPairs, int[] productToShelf, int[] shelfToProduct) {
        long currentTotal = evaluateTotal(productToShelf);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean improved = false;
            for (int[] pair : topPairs) {
                int q = pair[1];
                if (productToShelf[q] < 0) continue;
                for (int q2 = 0; q2 < P; q2++) {
                    if (q2 == q || productToShelf[q2] < 0) continue;
                    swapAssignment(productToShelf, shelfToProduct, q, q2);
                    long newTotal = evaluateTotal(productToShelf);
                    if (newTotal > currentTotal) {
                        currentTotal = newTotal;
                        improved = true;
                    } else {
                        swapAssignment(productToShelf, shelfToProduct, q, q2); // 改善しなければ元に戻す
                    }
                }
            }
            if (!improved) break;
        }
    }

    static void swapAssignment(int[] productToShelf, int[] shelfToProduct, int prodA, int prodB) {
        int shelfA = productToShelf[prodA];
        int shelfB = productToShelf[prodB];
        productToShelf[prodA] = shelfB;
        productToShelf[prodB] = shelfA;
        shelfToProduct[shelfA] = prodB;
        shelfToProduct[shelfB] = prodA;
    }

    static long key(int x, int y) { return (long) x * 100000 + y; }

    // dist: あるBFS始点からの距離マップ。(tx,ty)からdist=0の始点までparentDir
    // （computeParentDirで事前計算済み、棚を最も多く通る経路の後退方向）を辿り、
    // 通過セルを全てcellsへ追加する
    static void addPathCells(Set<Long> cells, int[][] dist, int[][] parentDir, int tx, int ty) {
        int x = tx, y = ty;
        cells.add(key(x, y));
        while (dist[x][y] != 0) {
            int d = parentDir[x][y];
            if (d < 0) break;
            x = x + GridMap.dx[d];
            y = y + GridMap.dy[d];
            cells.add(key(x, y));
        }
    }

    // Task6と同じ形のbitmask DPで、与えられた棚集合を全部訪問する最短順序を求め、
    // 実際に歩く経路セルを復元して、その上に乗る棚（=商品）の総数を返す。
    //
    // 【この関数の役割と近似について】
    // これは Stage D の局所探索（山登り）が大量に呼ぶスコア関数。区間ごとに独立して
    // computeParentDir 事前計算の経路を辿るため、複数区間のトリップでは「区間をまたいだ
    // 棚の重複除去まで最大化する」ことはしない近似値を返す（そのぶん高速）。
    // 最適化の内側ループなので、この近似で十分としている。
    //
    // 一方、最終出力（buildVizJson）で各履歴について報告する seen 値は、planTripExact が
    // 区間をまたいだ和集合まで厳密に最大化した値を使う。つまり:
    //   - Stage D の探索中: この近似関数（高速）
    //   - 出力ルートの seen: planTripExact（厳密、履歴ごとに1回だけなので性能影響なし）
    // という使い分けにしている。
    //   厳密性の回帰確認: Generator/Task7/Task7BruteForceGen.py
    //     --case miss_decoy（単一区間）/ --case clean_layout_demo（複数区間）ともにPASS。
    //   pickupToShelf は1マスに複数の棚（背中合わせ配置）が乗る場合も全部数える
    //     （Generator/Task7/Task7ExposureTieGen.py で回帰確認済み）。
    static int simulateTripAndCountShelves(int[] shelves) {
        int m = shelves.length;
        int full = 1 << m;
        int[][] dp = new int[full][m];
        int[][] parent = new int[full][m];
        for (int[] row : dp) Arrays.fill(row, INF);
        for (int[] row : parent) Arrays.fill(row, -1);

        for (int i = 0; i < m; i++) {
            int sd = startDist[shelves[i]];
            if (sd >= 0) dp[1 << i][i] = sd;
        }
        for (int mask = 1; mask < full; mask++) {
            for (int i = 0; i < m; i++) {
                if ((mask & (1 << i)) == 0) continue;
                int di = dp[mask][i];
                if (di >= INF) continue;
                for (int j = 0; j < m; j++) {
                    if ((mask & (1 << j)) != 0) continue;
                    int d = interDist[shelves[i]][shelves[j]];
                    if (d < 0) continue;
                    int nmask = mask | (1 << j);
                    int cand = di + d;
                    if (cand < dp[nmask][j]) { dp[nmask][j] = cand; parent[nmask][j] = i; }
                }
            }
        }

        int bestEnd = -1, bestVal = INF;
        for (int i = 0; i < m; i++) {
            if (dp[full - 1][i] >= INF) continue;
            int ed = exitDist[shelves[i]];
            if (ed < 0) continue;
            int total = dp[full - 1][i] + ed;
            if (total < bestVal) { bestVal = total; bestEnd = i; }
        }
        if (bestEnd == -1) return 0;

        int[] orderIdx = new int[m];
        int curMask = full - 1, curI = bestEnd;
        for (int k = m - 1; k >= 0; k--) {
            orderIdx[k] = curI;
            int prevI = parent[curMask][curI];
            curMask &= ~(1 << curI);
            curI = prevI;
        }

        Set<Long> cells = new HashSet<>();
        int firstShelf = shelves[orderIdx[0]];
        addPathCells(cells, distFromStart, parentDirFromStart, pickX[firstShelf], pickY[firstShelf]);
        for (int k = 1; k < m; k++) {
            int prevShelf = shelves[orderIdx[k - 1]];
            int curShelf  = shelves[orderIdx[k]];
            addPathCells(cells, distFromPickup[prevShelf], parentDirFromPickup[prevShelf], pickX[curShelf], pickY[curShelf]);
        }
        int lastShelf = shelves[orderIdx[m - 1]];
        addPathCells(cells, distFromExit, parentDirFromExit, pickX[lastShelf], pickY[lastShelf]);

        int seen = 0;
        for (long c : cells) {
            List<Integer> here = pickupToShelf.get(c);
            if (here != null) seen += here.size();
        }
        return seen;
    }
}
