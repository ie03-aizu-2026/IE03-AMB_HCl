import java.util.*;
import java.io.*;

public class Task6 {
    static int N;
    static int[] pickX, pickY;
    static String[] itemNames;

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
        int[][] distFromExit  = GridMap.bfs(W - 2, 0, blocked, W, H);
        int[][][] distFromPickup = new int[N][][];
        for (int i = 0; i < N; i++) {
            distFromPickup[i] = GridMap.bfs(pickX[i], pickY[i], blocked, W, H);
        }

        final int INF = Integer.MAX_VALUE / 2;

        // 商品iから商品jへの距離、開始地点・出口からの距離（-1は到達不可）
        int[] startDist = new int[N];
        int[] exitDist  = new int[N];
        int[][] interDist = new int[N][N];
        for (int i = 0; i < N; i++) {
            startDist[i] = distFromStart[pickX[i]][pickY[i]];
            exitDist[i]  = distFromExit[pickX[i]][pickY[i]];
            for (int j = 0; j < N; j++) {
                interDist[i][j] = distFromPickup[i][pickX[j]][pickY[j]];
            }
        }

        // dp2[mask*N + i] = 商品集合maskを全部訪問し、商品iで終える最短距離
        // 区間ごとに分けず、全商品(最大20個)を対象に一度だけ計算する。
        // こうすることで「複数の区間から取り得る境界上の商品」を区間ごとに
        // 独立に確定させることがなくなり、後戻りなしの誤りを避けられる。
        int full = 1 << N;
        int[] dp2 = new int[full * N];
        Arrays.fill(dp2, INF);
        for (int i = 0; i < N; i++) {
            if (startDist[i] >= 0) dp2[(1 << i) * N + i] = startDist[i];
        }
        for (int mask = 1; mask < full; mask++) {
            int base = mask * N;
            for (int i = 0; i < N; i++) {
                if ((mask & (1 << i)) == 0) continue;
                int di = dp2[base + i];
                if (di >= INF) continue;
                for (int j = 0; j < N; j++) {
                    if ((mask & (1 << j)) != 0) continue;
                    int d = interDist[i][j];
                    if (d < 0) continue;
                    int nm = mask | (1 << j);
                    int cand = di + d;
                    int idx = nm * N + j;
                    if (cand < dp2[idx]) dp2[idx] = cand;
                }
            }
        }

        // routeDist[mask] = 商品集合maskを全部訪問して出口まで抜けるときの最短距離
        int[] routeDist = new int[full];
        for (int mask = 1; mask < full; mask++) {
            int base = mask * N;
            int best = INF;
            for (int i = 0; i < N; i++) {
                if ((mask & (1 << i)) == 0) continue;
                if (exitDist[i] < 0) continue;
                int d = dp2[base + i];
                if (d >= INF) continue;
                int total = d + exitDist[i];
                if (total < best) best = total;
            }
            routeDist[mask] = best;
        }

        int Q = Integer.parseInt(br.readLine().trim());
        StringBuilder sb = new StringBuilder();

        for (int q = 0; q < Q; q++) {
            st = new StringTokenizer(br.readLine());
            int M = Integer.parseInt(st.nextToken());
            int required = 0;
            for (int i = 0; i < M; i++) required |= 1 << nameToIdx.get(st.nextToken());

            int minDist = routeDist[required];

            int bestCnt = -1;
            int bestMask = 0;
            String bestStr = null;
            for (int mask = required; mask < full; mask++) {
                if ((mask & required) != required) continue;
                if (routeDist[mask] != minDist) continue;
                int cnt = Integer.bitCount(mask);
                if (cnt < bestCnt) continue;
                String s = buildStr(mask);
                if (cnt > bestCnt || s.compareTo(bestStr) < 0) {
                    bestCnt = cnt; bestMask = mask; bestStr = s;
                }
            }

            sb.append(minDist);
            List<String> outList = new ArrayList<>();
            for (int p = 0; p < N; p++) {
                if ((bestMask & (1 << p)) != 0) outList.add(itemNames[p]);
            }
            Collections.sort(outList);
            for (String name : outList) sb.append(' ').append(name);
            sb.append('\n');
        }

        System.out.print(sb);
    }

    static String buildStr(int mask) {
        List<String> list = new ArrayList<>();
        for (int p = 0; p < N; p++) {
            if ((mask & (1 << p)) != 0) list.add(itemNames[p]);
        }
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s);
        return sb.toString();
    }
}
