import java.util.*;

// グリッドマップ上のBFS（幅優先探索）を提供するユーティリティクラス
public class GridMap {

    // 右・左・上・下 の4方向への移動量
    public static int[] dx = {1, -1, 0, 0};
    public static int[] dy = {0, 0, 1, -1};

    /**
     * 指定したスタート地点から全マスへの最短距離をBFSで求める
     *
     * @param sx      スタート地点のx座標
     * @param sy      スタート地点のy座標
     * @param blocked 通行不可マスの情報（trueなら通れない）
     * @param W       グリッドの幅
     * @param H       グリッドの高さ
     * @return        dist[x][y] = スタートから(x,y)への最短距離（到達不可なら-1）
     */
    public static int[][] bfs(int sx, int sy, boolean[][] blocked, int W, int H) {

        // 全マスを未訪問(-1)で初期化
        int[][] dist = new int[W][H];
        for (int[] row : dist) Arrays.fill(row, -1);

        // BFS用のキュー。スタート地点を入れて探索開始
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sx, sy});
        dist[sx][sy] = 0;

        while (!q.isEmpty()) {

            int[] cur = q.poll();
            int x = cur[0], y = cur[1];

            // 4方向に隣接するマスを調べる
            for (int i = 0; i < 4; i++) {

                int nx = x + dx[i];
                int ny = y + dy[i];

                // グリッド外に出る場合はスキップ
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;

                // 通行不可マスはスキップ
                if (blocked[nx][ny]) continue;

                // すでに訪問済みのマスはスキップ
                if (dist[nx][ny] != -1) continue;

                // 距離を記録してキューに追加
                dist[nx][ny] = dist[x][y] + 1;
                q.add(new int[]{nx, ny});
            }
        }

        return dist;
    }
}
