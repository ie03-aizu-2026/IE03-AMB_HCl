import java.io.*;
import java.util.*;

public class Task7haya {
    static int W, H, S, P, N;
    static int[] shelfX, shelfY;
    static char[] shelfD;
    static int[] pickX, pickY; // 各棚に対するアクセス位置
    
    // 商品名と配置された棚インデックスのマップ
    static Map<String, Integer> productToShelf = new HashMap<>();
    
    public static void main(String[] args) throws IOException {
        // 高速入出力 (Task 4ベース)
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st;

        // 1. 店舗形状と棚情報の読み込み
        String firstLine = br.readLine();
        if (firstLine == null) return;
        st = new StringTokenizer(firstLine);
        W = Integer.parseInt(st.nextToken());
        H = Integer.parseInt(st.nextToken());
        S = Integer.parseInt(st.nextToken());

        shelfX = new int[S]; shelfY = new int[S]; shelfD = new char[S];
        pickX = new int[S];  pickY = new int[S];
        boolean[][] blocked = new boolean[W][H];

        for (int i = 0; i < S; i++) {
            st = new StringTokenizer(br.readLine());
            shelfX[i] = Integer.parseInt(st.nextToken());
            shelfY[i] = Integer.parseInt(st.nextToken());
            shelfD[i] = st.nextToken().charAt(0);
            blocked[shelfX[i]][shelfY[i]] = true; // 棚自体は通行不可

            // アクセス位置の計算 (Task 3ベース)
            if      (shelfD[i] == 'E') { pickX[i] = shelfX[i] + 1; pickY[i] = shelfY[i];     }
            else if (shelfD[i] == 'W') { pickX[i] = shelfX[i] - 1; pickY[i] = shelfY[i];     }
            else if (shelfD[i] == 'N') { pickX[i] = shelfX[i];     pickY[i] = shelfY[i] + 1; }
            else                       { pickX[i] = shelfX[i];     pickY[i] = shelfY[i] - 1; }
        }

        // 下端の制限（入口・出口の設定）
        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0]     = false; // 入口
        blocked[W - 2][0] = false; // 出口

        // 2. 商品一覧の読み込み
        st = new StringTokenizer(br.readLine());
        P = Integer.parseInt(st.nextToken());
        List<String> products = new ArrayList<>();
        Map<String, Integer> singleCount = new HashMap<>();
        for (int i = 0; i < P; i++) {
            String pName = br.readLine().trim();
            products.add(pName);
            singleCount.put(pName, 0);
        }

        // 元の客の購入履歴データを一時保存
        st = new StringTokenizer(br.readLine());
        N = Integer.parseInt(st.nextToken());
        List<List<String>> customerOrders = new ArrayList<>();
        Map<String, Integer> pairCount = new HashMap<>();

        for (int i = 0; i < N; i++) {
            st = new StringTokenizer(br.readLine());
            int m = Integer.parseInt(st.nextToken());
            List<String> order = new ArrayList<>();
            String[] items = new String[m];
            for (int j = 0; j < m; j++) {
                items[j] = st.nextToken();
                order.add(items[j]);
                singleCount.put(items[j], singleCount.getOrDefault(items[j], 0) + 1);
            }
            customerOrders.add(order);
            
            Arrays.sort(items); // ペアの一意化のためソート (Task 2ベース)
            for (int j = 0; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    String pair = items[j] + " " + items[k];
                    pairCount.put(pair, pairCount.getOrDefault(pair, 0) + 1);
                }
            }
        }

        // 3. 棚の物理的トポロジー分析
        int[][] distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        int[][] distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);
        
        Integer[] shelfIndices = new Integer[S];
        int[] shelfDepth = new int[S];
        for (int i = 0; i < S; i++) {
            shelfIndices[i] = i;
            int ds = distFromStart[pickX[i]][pickY[i]];
            int de = distFromExit[pickX[i]][pickY[i]];
            shelfDepth[i] = (ds >= 0 && de >= 0) ? (ds + de) : -1; 
        }

        // 棚を「奥深い順」にソート
        Arrays.sort(shelfIndices, (a, b) -> Integer.compare(shelfDepth[b], shelfDepth[a]));

        // 4. 配置アルゴリズム（マグネット商品＆ジャカード係数動線配置）
        products.sort((a, b) -> Integer.compare(singleCount.get(b), singleCount.get(a)));

        boolean[] shelfOccupied = new boolean[S];
        String[] shelfToProduct = new String[S];

        int magnetLimit = Math.max(1, P / 4);
        int shelfPtr = 0;

        for (int i = 0; i < magnetLimit && i < products.size(); i++) {
            String magProd = products.get(i);
            while (shelfPtr < S && (shelfOccupied[shelfIndices[shelfPtr]] || shelfDepth[shelfIndices[shelfPtr]] == -1)) {
                shelfPtr++;
            }
            if (shelfPtr < S) {
                int targetShelf = shelfIndices[shelfPtr];
                shelfToProduct[targetShelf] = magProd;
                shelfOccupied[targetShelf] = true;
                productToShelf.put(magProd, targetShelf);
            }
        }

        for (String prod : products) {
            if (productToShelf.containsKey(prod)) continue;

            String bestPartner = null;
            double maxJaccard = -1.0;
            for (String placed : productToShelf.keySet()) {
                double jaccard = calculateJaccard(prod, placed, singleCount, pairCount);
                if (jaccard > maxJaccard) {
                    maxJaccard = jaccard;
                    bestPartner = placed;
                }
            }

            int chosenShelf = -1;
            if (bestPartner != null && maxJaccard > 0.0) {
                int partnerShelf = productToShelf.get(bestPartner);
                for (int idx : shelfIndices) {
                    if (!shelfOccupied[idx] && shelfDepth[idx] != -1 && shelfDepth[idx] < shelfDepth[partnerShelf]) {
                        chosenShelf = idx;
                        break;
                    }
                }
            }

            if (chosenShelf == -1) {
                for (int i = S - 1; i >= 0; i--) {
                    int idx = shelfIndices[i];
                    if (!shelfOccupied[idx] && shelfDepth[idx] != -1) {
                        chosenShelf = idx;
                        break;
                    }
                }
            }

            if (chosenShelf != -1) {
                shelfToProduct[chosenShelf] = prod;
                shelfOccupied[chosenShelf] = true;
                productToShelf.put(prod, chosenShelf);
            }
        }

        // 5. 【第一出力】最終的なレイアウト結果の出力[cite: 1]
        StringBuilder sbLayout = new StringBuilder();
        for (int i = 0; i < S; i++) {
            if (shelfToProduct[i] != null) {
                sbLayout.append(shelfX[i]).append(" ")
                        .append(shelfY[i]).append(" ")
                        .append(shelfToProduct[i]).append(" ")
                        .append(shelfD[i]).append("\n");
            }
        }
        System.out.print(sbLayout.toString());

        // 6. 【第二出力】配置確定後のシミュレーション（Task 5/6ベースの評価機能）
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < S; i++) {
            if (shelfToProduct[i] != null) {
                nameToIdx.put(shelfToProduct[i], i);
            }
        }

        int[][][] distFromPickup = new int[S][][];
        for (int i = 0; i < S; i++) {
            if (shelfToProduct[i] != null) {
                distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);
            }
        }

        final int INF = Integer.MAX_VALUE / 2;
        System.out.println("--- Customer Simulation Results ---");

        for (int c = 0; c < N; c++) {
            List<String> order = customerOrders.get(c);
            int M = order.size();
            int[] prods = new int[M];

            for (int i = 0; i < M; i++) {
                prods[i] = nameToIdx.get(order.get(i));
            }

            int[][] dp = new int[1 << M][M];
            for (int[] row : dp) Arrays.fill(row, INF);

            for (int i = 0; i < M; i++) {
                int d = distFromStart[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) dp[1 << i][i] = d;
            }

            for (int mask = 1; mask < (1 << M); mask++) {
                for (int i = 0; i < M; i++) {
                    if ((mask & (1 << i)) == 0 || dp[mask][i] >= INF) continue;
                    for (int j = 0; j < M; j++) {
                        if ((mask & (1 << j)) != 0) continue;
                        int d = distFromPickup[prods[i]][pickX[prods[j]]][pickY[prods[j]]];
                        if (d < 0) continue;
                        int nm = mask | (1 << j);
                        dp[nm][j] = Math.min(dp[nm][j], dp[mask][i] + d);
                    }
                }
            }

            int fullMask = (1 << M) - 1;
            int minPathLen = INF;
            for (int i = 0; i < M; i++) {
                if (dp[fullMask][i] >= INF) continue;
                int d = distFromExit[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) minPathLen = Math.min(minPathLen, dp[fullMask][i] + d);
            }

            int totalSeenItems = 0;
            for (int sIdx = 0; sIdx < S; sIdx++) {
                if (shelfToProduct[sIdx] == null) continue;
                
                int dStartToS = distFromStart[pickX[sIdx]][pickY[sIdx]];
                int dSToExit = distFromExit[pickX[sIdx]][pickY[sIdx]];
                if (dStartToS < 0 || dSToExit < 0) continue;

                boolean reachableOnShortest = false;
                for (int i = 0; i < M; i++) {
                    if (dp[fullMask][i] >= INF) continue;
                    int dBetween = distFromPickup[prods[i]][pickX[sIdx]][pickY[sIdx]];
                    if (dBetween >= 0 && (dp[fullMask][i] + dBetween + dSToExit == minPathLen)) {
                        reachableOnShortest = true;
                        break;
                    }
                }
                
                for (int i = 0; i < M; i++) {
                    int dBetween = distFromPickup[sIdx][pickX[prods[i]]][pickY[prods[i]]];
                    if (dBetween >= 0 && (dStartToS + dBetween + (dp[fullMask ^ (1 << i)][i] == INF ? 0 : dp[fullMask ^ (1 << i)][i]) + distFromExit[pickX[prods[i]]][pickY[prods[i]]] == minPathLen)) {
                        if (dStartToS + dBetween == distFromStart[pickX[prods[i]]][pickY[prods[i]]]) {
                            reachableOnShortest = true;
                        }
                    }
                }

                if (reachableOnShortest || order.contains(shelfToProduct[sIdx])) {
                    totalSeenItems++;
                }
            }

            System.out.println("Customer " + (c + 1) + ": Bought " + M + " items, Saw " + totalSeenItems + " items in total (Shortest Path: " + minPathLen + " steps).");
        }

        // -------------------------------------------------------------
        // 7. 【追加機能】商品単体・ペアの購入数ランキング出力 (Task 1, 2の統合)
        // -------------------------------------------------------------
        System.out.println("\n--- Single Product Purchase Ranking ---");
        List<Map.Entry<String, Integer>> singleRankList = new ArrayList<>(singleCount.entrySet());
        singleRankList.sort((item1, item2) -> {
            int countCompare = item2.getValue().compareTo(item1.getValue());
            return countCompare != 0 ? countCompare : item1.getKey().compareTo(item2.getKey());
        });
        for (Map.Entry<String, Integer> entry : singleRankList) {
            System.out.println(entry.getValue() + " " + entry.getKey());
        }

        System.out.println("\n--- Product Pair Purchase Ranking ---");
        List<Map.Entry<String, Integer>> pairRankList = new ArrayList<>(pairCount.entrySet());
        pairRankList.sort((item1, item2) -> {
            int countCompare = item2.getValue().compareTo(item1.getValue());
            return countCompare != 0 ? countCompare : item1.getKey().compareTo(item2.getKey());
        });
        for (Map.Entry<String, Integer> entry : pairRankList) {
            System.out.println(entry.getValue() + " " + entry.getKey());
        }
    }

    // ジャカード係数計算
    private static double calculateJaccard(String a, String b, Map<String, Integer> singleCount, Map<String, Integer> pairCount) {
        int countA = singleCount.getOrDefault(a, 0);
        int countB = singleCount.getOrDefault(b, 0);
        if (countA == 0 || countB == 0) return 0.0;

        String pairKey = a.compareTo(b) < 0 ? (a + " " + b) : (b + " " + a);
        int countBoth = pairCount.getOrDefault(pairKey, 0);

        if (countBoth == 0) return 0.0;
        return (double) countBoth / (countA + countB - countBoth);
    }
}