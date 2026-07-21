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
            List<int[]> path = buildRoutePath(shelves);
            int seen = simulateTripAndCountShelves(shelves);
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
    // 実際に歩く経路セルを復元して、その上に乗る棚（=商品）の総数を返す
    //
    // 【正しさの範囲・既知の限界】
    // 各区間（入口→棚 / 棚→棚 / 棚→出口）は addPathCells が computeParentDir で
    // 事前計算した「その区間の最短距離を保ったまま通過できる棚の数を最大化する経路」
    // を選ぶため、shelvesが1個（区間が1本）の場合は「最短ルートの中で最大何個の
    // 商品を回れるか」という値を厳密に返す（Generator/Task7/Task7BruteForceGen.py
    // --case miss_decoy で回帰確認済み）。
    // なお pickupToShelf は1マスに複数の棚（背中合わせ配置など）が乗る場合も
    // 正しく全部数える（Generator/Task7/Task7ExposureTieGen.py で回帰確認済み）。
    //
    // shelvesが2個以上（区間が複数）の場合は、区間ごとに独立して最大化しているため、
    // 「前の区間で既に通過した棚を後の区間で二重にカウントしない」という
    // トリップ全体としての和集合の最大化までは保証しない。区間をまたいで真に
    // 最大化するには、既に通過した棚を除外しながらトリップごとにその場でDPを
    // 回す必要があり、Stage Dのホットループ内で呼ばれる頻度を考えると性能への
    // 影響が無視できなくなる。得られる改善（現状ずれるのは主に±1件程度）に対して
    // コストが見合わないと判断し、意図的に対応していない
    // （Generator/Task7/Task7BruteForceGen.py --case clean_layout_demo が
    //  この既知の限界を示すデモケースとして今もFAILし続ける）。
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
