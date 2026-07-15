import java.io.*;
import java.util.*;

public class Task7jabu {
    static int W, H, S, P, N;
    static boolean[][] blocked;
    
    // 評価式の重みパラメータ（店舗の戦略に合わせて調整可能）
    static final double ALPHA = 1.0;  // 視認性ボーナスの重み
    static final double BETA  = 0.5;  // 移動コストペナルティの重み（距離の負担を軽減）
    static final double GAMMA = 2.0;  // ペア配置ボーナスの重み
    static final double DELTA = 1.5;  // 人気商品配置ボーナスの重み

    static class Shelf {
        int id;
        int x, y;
        char dir;
        int pickX, pickY;
        int distFromStart;
        String assignedProduct = null;

        public Shelf(int id, int x, int y, char dir) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.dir = dir;
         if (dir == 'E') { 
    this.pickX = x + 1; this.pickY = y;     // 東向き：棚の右側から取る
} else if (dir == 'W') { 
    this.pickX = x - 1; this.pickY = y;     // 西向き：棚の左側から取る
} else if (dir == 'N') { 
    this.pickX = x;     this.pickY = y + 1; // 北向き：棚の上側から取る
} else if (dir == 'S') { 
    this.pickX = x;     this.pickY = y - 1; // 南向き：棚の下側から取る
} else {
    // 想定外の向きが来た場合の安全対策
    this.pickX = x; this.pickY = y; 
}   
        }
    }

    static class ProductPair {
        String p1, p2;
        int count;
        public ProductPair(String p1, String p2, int count) {
            this.p1 = p1; this.p2 = p2; this.count = count;
        }
    }

    // 最短経路上のマスを復元するためのBFS
    public static List<int[]> getPath(int sx, int sy, int tx, int ty, boolean[][] blocked) {
        List<int[]> path = new ArrayList<>();
        if (sx == tx && sy == ty) {
            path.add(new int[]{sx, sy});
            return path;
        }
        int[][] dist = new int[W][H];
        for (int[] row : dist) Arrays.fill(row, -1);
        int[][][] parent = new int[W][H][2];

        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sx, sy});
        dist[sx][sy] = 0;

        boolean found = false;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1];
            if (x == tx && y == ty) { found = true; break; }

            for (int i = 0; i < 4; i++) {
                int nx = x + GridMap.dx[i];
                int ny = y + GridMap.dy[i];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                if (blocked[nx][ny] && !(nx == tx && ny == ty)) continue; 
                if (dist[nx][ny] != -1) continue;

                dist[nx][ny] = dist[x][y] + 1;
                parent[nx][ny][0] = x;
                parent[nx][ny][1] = y;
                q.add(new int[]{nx, ny});
            }
        }
        if (!found) return path;
        int cx = tx, cy = ty;
        while (cx != sx || cy != sy) {
            path.add(new int[]{cx, cy});
            int px = parent[cx][cy][0];
            int py = parent[cx][cy][1];
            cx = px; cy = py;
        }
        path.add(new int[]{sx, sy});
        Collections.reverse(path);
        return path;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        W = Integer.parseInt(st.nextToken());
        H = Integer.parseInt(st.nextToken());
        S = Integer.parseInt(st.nextToken());

        blocked = new boolean[W][H];
        List<Shelf> shelves = new ArrayList<>();
        Map<String, Shelf> shelfMap = new HashMap<>(); // 商品名 -> 棚

        for (int i = 0; i < S; i++) {
            st = new StringTokenizer(br.readLine());
            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());
            char d = st.nextToken().charAt(0);
            shelves.add(new Shelf(i, x, y, d));
            blocked[x][y] = true;
        }

        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0] = false;     // 入口 (1,0)
        blocked[W - 2][0] = false; // 出口 (W-2,0)

        st = new StringTokenizer(br.readLine());
        P = Integer.parseInt(st.nextToken());
        List<String> allProducts = new ArrayList<>();
        Map<String, Integer> singleCount = new HashMap<>();

        for (int i = 0; i < P; i++) {
            String name = br.readLine().trim();
            allProducts.add(name);
            singleCount.put(name, 0);
        }

        st = new StringTokenizer(br.readLine());
        N = Integer.parseInt(st.nextToken());
        Map<String, Integer> pairCount = new HashMap<>();
        List<String[]> histories = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            st = new StringTokenizer(br.readLine());
            int m = Integer.parseInt(st.nextToken());
            String[] items = new String[m];
            for (int j = 0; j < m; j++) {
                items[j] = st.nextToken();
                if (singleCount.containsKey(items[j])) {
                    singleCount.put(items[j], singleCount.get(items[j]) + 1);
                }
            }
            histories.add(items);

            Arrays.sort(items);
            for (int j = 0; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    String pair = items[j] + " " + items[k];
                    pairCount.put(pair, pairCount.getOrDefault(pair, 0) + 1);
                }
            }
        }

        // 1. 初期配置決定 ( Greedyアルゴリズムによる最適化 )
        int[][] distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        for (Shelf shelf : shelves) {
            if (shelf.pickX >= 0 && shelf.pickX < W && shelf.pickY >= 0 && shelf.pickY < H) {
                shelf.distFromStart = distFromStart[shelf.pickX][shelf.pickY];
            } else {
                shelf.distFromStart = -1;
            }
        }
        shelves.sort((s1, s2) -> Integer.compare(s2.distFromStart, s1.distFromStart));

        List<ProductPair> sortedPairs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pairCount.entrySet()) {
            String[] parts = entry.getKey().split(" ");
            sortedPairs.add(new ProductPair(parts[0], parts[1], entry.getValue()));
        }
        sortedPairs.sort((p1, p2) -> Integer.compare(p2.count, p1.count));

        List<Map.Entry<String, Integer>> sortedSingles = new ArrayList<>(singleCount.entrySet());
        sortedSingles.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

        Set<String> assignedProducts = new HashSet<>();
        int shelfIdx = 0;

        for (ProductPair pair : sortedPairs) {
            if (shelfIdx >= shelves.size() - 1) break;
            if (!assignedProducts.contains(pair.p1) && !assignedProducts.contains(pair.p2)) {
                shelves.get(shelfIdx++).assignedProduct = pair.p1;
                shelves.get(shelfIdx++).assignedProduct = pair.p2;
                assignedProducts.add(pair.p1); assignedProducts.add(pair.p2);
            }
        }
        for (Map.Entry<String, Integer> entry : sortedSingles) {
            if (shelfIdx >= shelves.size()) break;
            if (!assignedProducts.contains(entry.getKey())) {
                shelves.get(shelfIdx++).assignedProduct = entry.getKey();
                assignedProducts.add(entry.getKey());
            }
        }

        for (Shelf s : shelves) {
            if (s.assignedProduct != null) shelfMap.put(s.assignedProduct, s);
        }

        // 2. 総合評価式 (Score) に基づく定量シミュレーションの実行
        int totalMovementCost = 0;
        int totalVisibilityCount = 0;
        double pairBonusTotal = 0;
        double popularityBonusTotal = 0;

        // 全顧客の移動経路をシミュレーション (Task5のTSPビットDPロジックベース)
        for (String[] history : histories) {
            int M = history.length;
            List<Shelf> targets = new ArrayList<>();
            Set<String> shoppingList = new HashSet<>();
            for (String item : history) {
                if (shelfMap.containsKey(item)) {
                    targets.add(shelfMap.get(item));
                    shoppingList.add(item);
                }
            }
            if (targets.isEmpty()) continue;

            M = targets.size();
            final int INF = Integer.MAX_VALUE / 2;
            int[][] dp = new int[1 << M][M];
            int[][] parentDP = new int[1 << M][M]; // 経路復元用
            for (int[] row : dp) Arrays.fill(row, INF);
            for (int[] row : parentDP) Arrays.fill(row, -1);

            int[][] distFromStartLocal = GridMap.bfs(1, 0, blocked, W, H);
            for (int i = 0; i < M; i++) {
                int d = distFromStartLocal[targets.get(i).pickX][targets.get(i).pickY];
                if (d >= 0) dp[1 << i][i] = d;
            }

            int[][][] distFromPickup = new int[M][][];
            for (int i = 0; i < M; i++) {
                distFromPickup[i] = GridMap.bfs(targets.get(i).pickX, targets.get(i).pickY, blocked, W, H);
            }

            for (int mask = 1; mask < (1 << M); mask++) {
                for (int i = 0; i < M; i++) {
                    if ((mask & (1 << i)) == 0 || dp[mask][i] >= INF) continue;
                    for (int j = 0; j < M; j++) {
                        if ((mask & (1 << j)) != 0) continue;
                        int d = distFromPickup[i][targets.get(j).pickX][targets.get(j).pickY];
                        if (d < 0) continue;
                        if (dp[mask][i] + d < dp[mask | (1 << j)][j]) {
                            dp[mask | (1 << j)][j] = dp[mask][i] + d;
                            parentDP[mask | (1 << j)][j] = i;
                        }
                    }
                }
            }

            int fullMask = (1 << M) - 1;
            int bestEndIdx = -1;
            int minTotalDist = INF;
            int[][] distFromExit = GridMap.bfs(W - 2, 0, blocked, W, H);

            for (int i = 0; i < M; i++) {
                if (dp[fullMask][i] >= INF) continue;
                int d = distFromExit[targets.get(i).pickX][targets.get(i).pickY];
                if (d >= 0 && dp[fullMask][i] + d < minTotalDist) {
                    minTotalDist = dp[fullMask][i] + d;
                    bestEndIdx = i;
                }
            }

            if (bestEndIdx == -1) continue;
            totalMovementCost += minTotalDist;

            // 経路復元による「視認性」の精密シミュレーション
            List<int[]> fullRouteCells = new ArrayList<>();
            int currMask = fullMask;
            int currIdx = bestEndIdx;

            // 各訪問区間のセル経路を網羅
            List<int[]> toExit = getPath(targets.get(currIdx).pickX, targets.get(currIdx).pickY, W - 2, 0, blocked);
            fullRouteCells.addAll(toExit);

            while (currIdx != -1) {
                int prevIdx = parentDP[currMask][currIdx];
                if (prevIdx != -1) {
                    List<int[]> segment = getPath(targets.get(prevIdx).pickX, targets.get(prevIdx).pickY, targets.get(currIdx).pickX, targets.get(currIdx).pickY, blocked);
                    fullRouteCells.addAll(segment);
                    currMask ^= (1 << currIdx);
                    currIdx = prevIdx;
                } else {
                    List<int[]> fromStart = getPath(1, 0, targets.get(currIdx).pickX, targets.get(currIdx).pickY, blocked);
                    fullRouteCells.addAll(fromStart);
                    break;
                }
            }

            
            // 客ごとの買い物リストと移動ルートの表示
            System.out.printf("\n [顧客のシミュレーション] 買い物リスト: %s\n", Arrays.toString(history));
            System.out.print("  移動ルート: ");
            for (int k = 0; k < fullRouteCells.size(); k++) {
                int[] cell = fullRouteCells.get(k);
                System.out.printf("(%d,%d)", cell[0], cell[1]);
                if (k < fullRouteCells.size() - 1) {
                    System.out.print(" -> ");
                }
            }
            System.out.println();
            System.out.printf("   移動距離: %d マス\n", minTotalDist);

            // 経路上の全マスから周囲の棚にある「買わない商品」をカウント
            for (int[] cell : fullRouteCells) {
                for (int i = 0; i < 4; i++) {
                    int nx = cell[0] + GridMap.dx[i];
                    int ny = cell[1] + GridMap.dy[i];
                    if (nx >= 0 && nx < W && ny >= 0 && ny < H) {
                        // 隣接マスが棚であり、そこに商品があるか確認
                        for (Shelf s : shelves) {
                            if (s.x == nx && s.y == ny && s.assignedProduct != null) {
                                if (!shoppingList.contains(s.assignedProduct)) {
                                    totalVisibilityCount++; // 視認ボーナス獲得
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. マーケティングボーナス（ペア・人気商品）の計算
        for (ProductPair pair : sortedPairs) {
            if (shelfMap.containsKey(pair.p1) && shelfMap.containsKey(pair.p2)) {
                Shelf s1 = shelfMap.get(pair.p1);
                Shelf s2 = shelfMap.get(pair.p2);
                int dist = Math.abs(s1.x - s2.x) + Math.abs(s1.y - s2.y);
                if (dist >= 1 && dist <= 4) { // 適切な距離（同じ通路や近隣エリア）にある場合
                    pairBonusTotal += pair.count * s1.distFromStart;
                }
            }
        }
        for (Map.Entry<String, Integer> entry : sortedSingles) {
            if (shelfMap.containsKey(entry.getKey())) {
                Shelf s = shelfMap.get(entry.getKey());
                popularityBonusTotal += entry.getValue() * s.distFromStart;
            }
        }

        // 総合評価スコア (Score) の算出
        double finalScore = (ALPHA * totalVisibilityCount) - (BETA * totalMovementCost) 
                            + (GAMMA * pairBonusTotal) + (DELTA * popularityBonusTotal);

        // 4. 結果の出力
        System.out.println("=== ペア併売分析＆回遊性優先 プロダクトレイアウト提案 ===");
        shelves.sort((s1, s2) -> Integer.compare(s1.id, s2.id));
        for (Shelf s : shelves) {
            String prod = (s.assignedProduct != null) ? s.assignedProduct : "[空き棚]";
            System.out.printf("棚ID:%2d 座標:(%d, %d) 向き:%c [入口からの距離:%2d] -> 配置商品: %s\n",
                    s.id, s.x, s.y, s.dir, s.distFromStart, prod);
        }

        System.out.println("\n=== 総合評価式(Score)による定量シミュレーション結果 ===");
        System.out.printf("・総視認数 (Visibility)             : %d 個 (重み α=%.1f)\n", totalVisibilityCount, ALPHA);
        System.out.printf("・総移動コスト (Movement Cost)        : %d マス (重み β=%.1f)\n", totalMovementCost, BETA);
        System.out.printf("・ペア配置ボーナス (Pair Bonus)        : %.1f 点 (重み γ=%.1f)\n", pairBonusTotal, GAMMA);
        System.out.printf("・人気商品配置ボーナス (Popularity)   : %.1f 点 (重み δ=%.1f)\n", popularityBonusTotal, DELTA);
        System.out.println("---------------------------------------------------------");
        System.out.printf(" 最終総合評価スコア (Score)       : %.2f 点\n", finalScore);
    }
}