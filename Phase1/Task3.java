import java.util.*;

public class Task3 {
    // x座標、y座標、そこまでの歩数(dist)をまとめた「地点データ」の設計図
    static class Point {
        int x, y, dist;
        Point(int x, int y, int dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }
    }

    // マップの横幅(W)と縦幅(H)
    static int W, H;
    // 障害物（通れない場所）を記録するマップ
    static boolean[][] obstacles;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        W = scanner.nextInt();
        H = scanner.nextInt();
        int N = scanner.nextInt();

        obstacles = new boolean[W][H];

        // 1. 角（四隅）を通行不可（障害物）にするルール
        obstacles[0][0] = true;
        obstacles[W - 1][0] = true;
        obstacles[0][H - 1] = true;
        obstacles[W - 1][H - 1] = true;

        // 2. 入口(1,0)と出口(W-2,0)の間のセルを通行不可にするルール
        for (int x = 2; x <= W - 3; x++) {
            obstacles[x][0] = true;
        }

        // 商品の「取り出し口の座標」を記憶するメモ帳
        Map<String, Point> productTargets = new HashMap<>();

        // 3. 商品の情報を読み込む
        for (int i = 0; i < N; i++) {
            int x = scanner.nextInt();
            int y = scanner.nextInt();
            String name = scanner.next();
            String dir = scanner.next(); // 棚の向き (N, S, E, W)

            // 商品が置かれている場所自体は進入禁止
            obstacles[x][y] = true;

            // 棚の向きに合わせて、お客さんが立つ「取り出し口」の座標を計算
            int targetX = x;
            int targetY = y;
            if (dir.equals("N")) targetY += 1; // 北向きなら1つ上
            else if (dir.equals("S")) targetY -= 1; // 南向きなら1つ下
            else if (dir.equals("E")) targetX += 1; // 東向きなら1つ右
            else if (dir.equals("W")) targetX -= 1; // 西向きなら1つ左

            productTargets.put(name, new Point(targetX, targetY, 0));
        }

        int Q = scanner.nextInt();
        
        // 4. 顧客ごとのルート計算
        for (int i = 0; i < Q; i++) {
            int m = scanner.nextInt(); // 今回は常に1個
            String product = scanner.next();

            // 欲しい商品の「取り出し口」の座標を取得
            Point target = productTargets.get(product);

            // ① 入口(1,0)から、商品の取り出し口までの最短距離を計算
            int distToProduct = bfs(1, 0, target.x, target.y);
            
            // ② 商品の取り出し口から、出口(W-2,0)までの最短距離を計算
            int distToExit = bfs(target.x, target.y, W - 2, 0);

            // ①と②の合計距離を出力
            System.out.println(distToProduct + distToExit);
        }
        scanner.close();
    }

    // 【必殺技】幅優先探索（BFS）で最短距離を計算するメソッド
    static int bfs(int startX, int startY, int goalX, int goalY) {
        // すでにゴールにいる場合は0歩
        if (startX == goalX && startY == goalY) return 0;

        boolean[][] visited = new boolean[W][H]; // 足跡（行ったことあるか）の記録
        Queue<Point> queue = new LinkedList<>(); // 次に行く場所の予定表

        queue.add(new Point(startX, startY, 0)); // スタート地点を予定表に入れる
        visited[startX][startY] = true;

        // 上下左右に移動するための魔法の数字
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        // 予定表が空になるまで探索を続ける
        while (!queue.isEmpty()) {
            Point p = queue.poll(); // 予定表から1つ取り出す

            // ゴールに到達したら、そこまでの歩数を返して終了！
            if (p.x == goalX && p.y == goalY) {
                return p.dist;
            }

            // 今いる場所から「上下左右」の4方向を調べる
            for (int i = 0; i < 4; i++) {
                int nx = p.x + dx[i];
                int ny = p.y + dy[i];

                // グリッドの外にはみ出さないか ＆ 障害物がないか ＆ まだ行ってない場所か をチェック
                if (nx >= 0 && nx < W && ny >= 0 && ny < H) {
                    if (!obstacles[nx][ny] && !visited[nx][ny]) {
                        visited[nx][ny] = true; // 足跡をつける
                        queue.add(new Point(nx, ny, p.dist + 1)); // 次の予定表に追加（歩数を+1する）
                    }
                }
            }
        }
        return -1; // 辿り着けない場合
    }
}