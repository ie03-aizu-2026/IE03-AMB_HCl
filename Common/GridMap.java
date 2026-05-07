package IE03_AmbHCl.Common;

import java.util.*;

public class GridMap {

    public static int[] dx = {1, -1, 0, 0};

    public static int[] dy = {0, 0, 1, -1};

    public static int[][] bfs(int sx, int sy, boolean[][] blocked, int W, int H) {

        int[][] dist = new int[W][H];

        for (int[] row : dist) Arrays.fill(row, -1);

        Queue<int[]> q = new LinkedList<>();

        q.add(new int[]{sx, sy});

        dist[sx][sy] = 0;

        while (!q.isEmpty()) {

            int[] cur = q.poll();

            int x = cur[0], y = cur[1];

            for (int i = 0; i < 4; i++) {

                int nx = x + dx[i];

                int ny = y + dy[i];

                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;

                if (blocked[nx][ny]) continue;

                if (dist[nx][ny] != -1) continue;

                dist[nx][ny] = dist[x][y] + 1;

                q.add(new int[]{nx, ny});

            }

        }

        return dist;

    }

}