import java.util.*;
import java.io.*;

public class Task6 {
    static int N;
    static int[] pickX, pickY;
    static String[] itemNames;
    static int[][][] distFromPickup;
    static int[][] distFromExit;

    // クエリごとの最良解
    static int bestCnt;
    static long bestItems;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        StringTokenizer st = new StringTokenizer(br.readLine());
        int W = Integer.parseInt(st.nextToken());
        int H = Integer.parseInt(st.nextToken());
        N = Integer.parseInt(st.nextToken());

        pickX = new int[N]; pickY = new int[N];
        itemNames = new String[N];
        Map<String, Integer> nameToIdx = new HashMap<>();
        boolean[][] blocked = new boolean[W][H];

        for (int i = 0; i < N; i++) {
            st = new StringTokenizer(br.readLine());
            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());
            itemNames[i] = st.nextToken();
            char d = st.nextToken().charAt(0);
            nameToIdx.put(itemNames[i], i);
            blocked[x][y] = true;
            if      (d == 'E') { pickX[i] = x + 1; pickY[i] = y;     }
            else if (d == 'W') { pickX[i] = x - 1; pickY[i] = y;     }
            else if (d == 'N') { pickX[i] = x;     pickY[i] = y + 1; }
            else               { pickX[i] = x;     pickY[i] = y - 1; }
        }

        for (int x = 0; x < W; x++) blocked[x][0] = true;
        blocked[1][0]     = false;
        blocked[W - 2][0] = false;

        int[][] distFromStart = GridMap.bfs(1, 0, blocked, W, H);
        distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);
        distFromPickup = new int[N][][];
        for (int i = 0; i < N; i++) {
            distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);
        }

        int Q = Integer.parseInt(br.readLine().trim());
        StringBuilder sb = new StringBuilder();

        for (int q = 0; q < Q; q++) {
            st = new StringTokenizer(br.readLine());
            int M = Integer.parseInt(st.nextToken());
            int[] prods = new int[M];
            for (int i = 0; i < M; i++) prods[i] = nameToIdx.get(st.nextToken());

            final int INF = Integer.MAX_VALUE / 2;

            // Phase1: TSP DP で最短距離を確定
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
            int minDist = INF;
            for (int i = 0; i < M; i++) {
                if (dp[fullMask][i] >= INF) continue;
                int d = distFromExit[pickX[prods[i]]][pickY[prods[i]]];
                if (d >= 0) minDist = Math.min(minDist, dp[fullMask][i] + d);
            }

            // Phase2: DFS で最適経路をすべて探索し、商品セットを確定
            bestCnt   = -1;
            bestItems = 0L;
            for (int i = 0; i < M; i++) {
                if (dp[1 << i][i] >= INF) continue;
                long seg = segItems(distFromStart, distFromPickup[prods[i]],
                                    dp[1 << i][i], -1, prods[i], 0L);
                seg |= (1L << prods[i]);
                dfs(1 << i, i, seg, dp, prods, M, fullMask, minDist);
            }

            sb.append(minDist);
            List<String> outList = new ArrayList<>();
            for (int p = 0; p < N; p++) {
                if ((bestItems & (1L << p)) != 0) outList.add(itemNames[p]);
            }
            Collections.sort(outList);
            for (String name : outList) sb.append(' ').append(name);
            sb.append('\n');
        }

        System.out.print(sb);
    }

    // DFS: DP1 の最適遷移のみを辿り、見た商品セットを計算する
    static void dfs(int mask, int last, long currentSeen,
                    int[][] dp, int[] prods, int M, int fullMask, int minDist) {
        if (mask == fullMask) {
            int dToExit = distFromExit[pickX[prods[last]]][pickY[prods[last]]];
            if (dToExit < 0 || dp[fullMask][last] + dToExit != minDist) return;

            long seg = segItems(distFromPickup[prods[last]], distFromExit,
                                dToExit, prods[last], -1, currentSeen);
            long totalSeen = currentSeen | seg;
            int  totalCnt  = Long.bitCount(totalSeen);

            if (totalCnt > bestCnt ||
                (totalCnt == bestCnt && lexSmaller(totalSeen, bestItems))) {
                bestCnt   = totalCnt;
                bestItems = totalSeen;
            }
            return;
        }

        for (int j = 0; j < M; j++) {
            if ((mask & (1 << j)) != 0) continue;
            int d = distFromPickup[prods[last]][pickX[prods[j]]][pickY[prods[j]]];
            if (d < 0) continue;
            int nm = mask | (1 << j);
            if (dp[mask][last] + d != dp[nm][j]) continue; // 最短でない遷移はスキップ

            long seg = segItems(distFromPickup[prods[last]], distFromPickup[prods[j]],
                                d, prods[last], prods[j], currentSeen);
            seg |= (1L << prods[j]);
            dfs(nm, j, currentSeen | seg, dp, prods, M, fullMask, minDist);
        }
    }

    // セグメント A→B 上で「1本の最短経路上で同時に回れる」最大商品セットを返す
    // currentSeen: すでに見た商品（候補から除外）
    static long segItems(int[][] distA, int[][] distB, int totalDist,
                         int excludeSrc, int excludeDst, long currentSeen) {
        // 候補商品を列挙
        int[] cands = new int[N];
        int k = 0;
        for (int p = 0; p < N; p++) {
            if (p == excludeSrc || p == excludeDst) continue;
            if ((currentSeen & (1L << p)) != 0) continue;
            int dA = distA[pickX[p]][pickY[p]];
            int dB = distB[pickX[p]][pickY[p]];
            if (dA >= 0 && dB >= 0 && dA + dB == totalDist) cands[k++] = p;
        }
        if (k == 0) return 0L;

        // 内部DP: 候補を1本の最短経路上で同時に回れる最大部分集合を求める
        // inner[mask][i] = 候補のうちmaskを回収済み、最後がcands[i] のときのAからの距離
        // 遷移条件: inner[mask][i] + dist(cands[i], cands[j]) == dist(A, cands[j])
        final int INF2 = Integer.MAX_VALUE / 2;
        int[][] inner = new int[1 << k][k];
        for (int[] row : inner) Arrays.fill(row, INF2);
        for (int i = 0; i < k; i++) {
            inner[1 << i][i] = distA[pickX[cands[i]]][pickY[cands[i]]];
        }
        for (int mask2 = 1; mask2 < (1 << k); mask2++) {
            for (int i = 0; i < k; i++) {
                if ((mask2 & (1 << i)) == 0 || inner[mask2][i] >= INF2) continue;
                for (int j = 0; j < k; j++) {
                    if ((mask2 & (1 << j)) != 0) continue;
                    int d2 = distFromPickup[cands[i]][pickX[cands[j]]][pickY[cands[j]]];
                    if (d2 < 0) continue;
                    int nd = inner[mask2][i] + d2;
                    // AからcandJまでの最短距離と一致するときのみ有効な遷移
                    if (nd == distA[pickX[cands[j]]][pickY[cands[j]]]) {
                        int nm2 = mask2 | (1 << j);
                        if (nd < inner[nm2][j]) inner[nm2][j] = nd;
                    }
                }
            }
        }

        // 有効な最終状態から最大商品数・辞書順最小を選ぶ
        int    bestCnt2 = 0;
        long   bestBits = 0L;
        String bestStr  = null;
        for (int mask2 = 1; mask2 < (1 << k); mask2++) {
            for (int i = 0; i < k; i++) {
                if ((mask2 & (1 << i)) == 0 || inner[mask2][i] >= INF2) continue;
                if (inner[mask2][i] + distB[pickX[cands[i]]][pickY[cands[i]]] != totalDist) continue;
                int  cnt  = Integer.bitCount(mask2);
                long bits = 0L;
                for (int j = 0; j < k; j++) {
                    if ((mask2 & (1 << j)) != 0) bits |= (1L << cands[j]);
                }
                if (cnt > bestCnt2) {
                    bestCnt2 = cnt; bestBits = bits; bestStr = buildStr(bits);
                } else if (cnt == bestCnt2) {
                    String s = buildStr(bits);
                    if (bestStr == null || s.compareTo(bestStr) < 0) {
                        bestBits = bits; bestStr = s;
                    }
                }
            }
        }
        return bestBits;
    }

    static boolean lexSmaller(long a, long b) {
        return buildStr(a).compareTo(buildStr(b)) < 0;
    }

    static String buildStr(long items) {
        List<String> list = new ArrayList<>();
        for (int p = 0; p < N; p++) {
            if ((items & (1L << p)) != 0) list.add(itemNames[p]);
        }
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s);
        return sb.toString();
    }
}
