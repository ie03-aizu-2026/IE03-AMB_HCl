package IE03_AmbHCl.Phase2;

import java.util.*;
import IE03_AmbHCl.Common.GridMap;

public class Task5 {
    static int W, H;
    static boolean[][] blocked;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        W = sc.nextInt();
        H = sc.nextInt();
        int N = sc.nextInt();

        int[] pickX = new int[N], pickY = new int[N];
        Map<String, Integer> nameToIdx = new HashMap<>();
        blocked = new boolean[W][H];

        for (int i = 0; i < N; i++) {
            int x = sc.nextInt();
            int y = sc.nextInt();
            String name = sc.next();
            char d = sc.next().charAt(0);

            nameToIdx.put(name, i);
            blocked[x][y] = true;

            if      (d == 'E') { pickX[i] = x + 1; pickY[i] = y;     }
            else if (d == 'W') { pickX[i] = x - 1; pickY[i] = y;     }
            else if (d == 'N') { pickX[i] = x;     pickY[i] = y + 1; }
            else               { pickX[i] = x;     pickY[i] = y - 1; }
        }

        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0]     = false; // 入口
        blocked[W - 2][0] = false; // 出口

        int[][] distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        int[][] distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);

        // 各商品の取得位置からのBFS距離を事前計算
        int[][][] distFromPickup = new int[N][][];
        for (int i = 0; i < N; i++) {
            distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);
        }

        int Q = sc.nextInt();
        StringBuilder sb = new StringBuilder();

        for (int q = 0; q < Q; q++) {
            int M = sc.nextInt();
            int[] prods = new int[M];
            for (int i = 0; i < M; i++) {
                prods[i] = nameToIdx.get(sc.next());
            }

            // TSPビットDP
            // dp[mask][i] = maskの商品を回収済み、最後に商品iの取得位置にいるときの最短距離
            final int INF = Integer.MAX_VALUE / 2;
            int[][] dp = new int[1 << M][M];
            for (int[] row : dp) Arrays.fill(row, INF);

            // 初期化: 入口から各商品へ直行
            for (int i = 0; i < M; i++) {
                int d = distFromStart[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) dp[1 << i][i] = d;
            }

            // DP遷移
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

            // 全商品回収後、出口への最短距離
            int fullMask = (1 << M) - 1;
            int ans = INF;
            for (int i = 0; i < M; i++) {
                if (dp[fullMask][i] >= INF) continue;
                int d = distFromExit[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) ans = Math.min(ans, dp[fullMask][i] + d);
            }

            sb.append(ans).append("\n");
        }

        System.out.print(sb);
        sc.close();
    }
}
