import java.util.*;

// Task5: ルートシミュレーション（一般化）
// 入口からM個の商品をすべて取り出口へ向かう最短経路の距離を求める
public class Task5 {
    static int W, H;       // グリッドの幅と高さ
    static boolean[][] blocked; // trueなら通行不可

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        W = sc.nextInt();
        H = sc.nextInt();
        int N = sc.nextInt(); // 商品の総数

        int[] pickX = new int[N], pickY = new int[N]; // 各商品の取得位置（棚の隣）
        Map<String, Integer> nameToIdx = new HashMap<>(); // 商品名 → インデックス
        blocked = new boolean[W][H];

        // 商品情報を読み込む
        for (int i = 0; i < N; i++) {
            int x = sc.nextInt();
            int y = sc.nextInt();
            String name = sc.next();
            char d = sc.next().charAt(0); // 棚の向き（E/W/N/S）

            nameToIdx.put(name, i);
            blocked[x][y] = true; // 棚自体は通行不可

            // 向きに応じて、棚の隣（取得位置）を計算する
            if      (d == 'E') { pickX[i] = x + 1; pickY[i] = y;     } // 右から取る
            else if (d == 'W') { pickX[i] = x - 1; pickY[i] = y;     } // 左から取る
            else if (d == 'N') { pickX[i] = x;     pickY[i] = y + 1; } // 上から取る
            else               { pickX[i] = x;     pickY[i] = y - 1; } // 下から取る（S）
        }

        // 下端の行は壁とし、入口(1,0)と出口(W-2,0)だけ通れるようにする
        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0]     = false; // 入口
        blocked[W - 2][0] = false; // 出口

        // 入口・出口から全マスへの最短距離をBFSで事前計算
        int[][] distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        int[][] distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);

        // 各商品の取得位置から全マスへの最短距離をBFSで事前計算
        // これにより、商品間の移動距離をO(1)で取得できる
        int[][][] distFromPickup = new int[N][][];
        for (int i = 0; i < N; i++) {
            distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);
        }

        int Q = sc.nextInt(); // クエリ（客）の数
        StringBuilder sb = new StringBuilder();

        for (int q = 0; q < Q; q++) {
            int M = sc.nextInt(); // この客が取る商品の数
            int[] prods = new int[M]; // この客の商品インデックス一覧
            for (int i = 0; i < M; i++) {
                prods[i] = nameToIdx.get(sc.next());
            }

            // ────────────────────────────────────────────
            // TSP（巡回セールスマン問題）のビットDP
            //
            // M個の商品を全部取る順番は M! 通りあるが、
            // ビットDPを使うと 2^M × M^2 に計算量を抑えられる。
            //
            // dp[mask][i] = maskのビットが立っている商品を回収済みで、
            //               最後に商品iの取得位置にいるときの最短距離
            //
            // 例) M=3, mask=0b101 なら商品0と2を回収済みで商品iにいる状態
            // ────────────────────────────────────────────
            final int INF = Integer.MAX_VALUE / 2;
            int[][] dp = new int[1 << M][M];
            for (int[] row : dp) Arrays.fill(row, INF);

            // 初期化: 入口から各商品へ直行した場合の距離
            for (int i = 0; i < M; i++) {
                int d = distFromStart[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) dp[1 << i][i] = d;
            }

            // DP遷移: 商品iにいる状態から、まだ取っていない商品jへ移動する
            for (int mask = 1; mask < (1 << M); mask++) {
                for (int i = 0; i < M; i++) {
                    // 商品iが回収済みでない、またはこの状態が未到達なら飛ばす
                    if ((mask & (1 << i)) == 0 || dp[mask][i] >= INF) continue;
                    for (int j = 0; j < M; j++) {
                        if ((mask & (1 << j)) != 0) continue; // jはすでに回収済みなら飛ばす
                        int d = distFromPickup[prods[i]][pickX[prods[j]]][pickY[prods[j]]];
                        if (d < 0) continue; // 到達不可なら飛ばす
                        int nm = mask | (1 << j);
                        dp[nm][j] = Math.min(dp[nm][j], dp[mask][i] + d);
                    }
                }
            }

            // 全商品回収後（fullMask）、出口への最短距離を求める
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
