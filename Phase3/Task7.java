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
    static Map<Long, Integer> pickupToShelf; // 取得マス座標 -> 棚インデックス

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
        for (int i = 0; i < S; i++) pickupToShelf.put(key(pickX[i], pickY[i]), i);

        // ---- Stage A: 棚ごとの露出スコア（単独で買いに行った時に何棚分「見える」か） ----
        int[] exposure = new int[S];
        for (int i = 0; i < S; i++) {
            Set<Long> cells = new HashSet<>();
            addPathCells(cells, distFromStart, pickX[i], pickY[i]); // 入口 -> 棚i
            addPathCells(cells, distFromExit,  pickX[i], pickY[i]); // 棚i -> 出口
            int cnt = 0;
            for (long c : cells) {
                Integer sh = pickupToShelf.get(c);
                if (sh != null && sh != i) cnt++;
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

    // dist上のセルをtxtyから逆順に辿ってリスト化する（順序付き版）
    static List<int[]> pathCellsList(int[][] dist, int tx, int ty) {
        List<int[]> list = new ArrayList<>();
        int x = tx, y = ty;
        list.add(new int[]{x, y});
        while (dist[x][y] != 0) {
            int cur = dist[x][y];
            boolean moved = false;
            for (int d = 0; d < 4; d++) {
                int nx = x + GridMap.dx[d], ny = y + GridMap.dy[d];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                if (dist[nx][ny] == cur - 1) { x = nx; y = ny; list.add(new int[]{x, y}); moved = true; break; }
            }
            if (!moved) break;
        }
        return list;
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
        List<int[]> seg = pathCellsList(distFromStart, pickX[firstShelf], pickY[firstShelf]);
        Collections.reverse(seg);
        path.addAll(seg);
        for (int k = 1; k < m; k++) {
            int prevShelf = shelves[orderIdx[k - 1]];
            int curShelf  = shelves[orderIdx[k]];
            seg = pathCellsList(distFromPickup[prevShelf], pickX[curShelf], pickY[curShelf]);
            Collections.reverse(seg);
            path.addAll(seg.subList(1, seg.size()));
        }
        int lastShelf = shelves[orderIdx[m - 1]];
        seg = pathCellsList(distFromExit, pickX[lastShelf], pickY[lastShelf]);
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

    // dist: あるBFS始点からの距離マップ。(tx,ty)からdist=0の始点まで逆順に辿り、通過セルを全てcellsへ追加する
    static void addPathCells(Set<Long> cells, int[][] dist, int tx, int ty) {
        int x = tx, y = ty;
        cells.add(key(x, y));
        while (dist[x][y] != 0) {
            int cur = dist[x][y];
            boolean moved = false;
            for (int d = 0; d < 4; d++) {
                int nx = x + GridMap.dx[d];
                int ny = y + GridMap.dy[d];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                if (dist[nx][ny] == cur - 1) {
                    x = nx; y = ny;
                    cells.add(key(x, y));
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
        }
    }

    // Task6と同じ形のbitmask DPで、与えられた棚集合を全部訪問する最短順序を求め、
    // 実際に歩く経路セルを復元して、その上に乗る棚（=商品）の総数を返す
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
        addPathCells(cells, distFromStart, pickX[firstShelf], pickY[firstShelf]);
        for (int k = 1; k < m; k++) {
            int prevShelf = shelves[orderIdx[k - 1]];
            int curShelf  = shelves[orderIdx[k]];
            addPathCells(cells, distFromPickup[prevShelf], pickX[curShelf], pickY[curShelf]);
        }
        int lastShelf = shelves[orderIdx[m - 1]];
        addPathCells(cells, distFromExit, pickX[lastShelf], pickY[lastShelf]);

        int seen = 0;
        for (long c : cells) if (pickupToShelf.containsKey(c)) seen++;
        return seen;
    }
}
