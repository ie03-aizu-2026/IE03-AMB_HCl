import java.util.*;

public class Main {
    static int W, H; //グリッドの幅と高さ
    static boolean[][] blocked; //そのマスに入れるかどうか　trueなら通れない
    static int[] dx = {1, -1, 0, 0};
    static int[] dy = {0, 0, 1, -1};

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        W = sc.nextInt(); //幅
        H = sc.nextInt(); //高さ
        int N = sc.nextInt(); //商品数

        Map<String, int[]> map = new HashMap<>();
        blocked = new boolean[W][H];

        // 商品情報
        for (int i = 0; i < N; i++) {
            int x = sc.nextInt(); //x座標
            int y = sc.nextInt(); //y座標
            String name = sc.next(); //商品名
            char d = sc.next().charAt(0); //方角 sc.nextは文字列を返すのでcharAtでchar型に変更

            map.put(name, new int[]{x, y, d}); //商品名、x、y、方角
            blocked[x][y] = true; // 商品の場所は通れない
        }

        for (int x = 0; x < W; x++) {
            blocked[x][0] = true; //入口出口以外の下の列は通れない
        }
        blocked[1][0] = false;       // 入口
        blocked[W - 2][0] = false;   // 出口

        // BFS（入口と出口から）
        int[][] distFromStart = bfs(1, 0); //入り口からBFS
        int[][] distFromExit = bfs(W - 2, 0); //出口からBFS

        int Q = sc.nextInt(); //クエリ（お客の数)を取得

        // 出力をためる
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Q; i++) {
            sc.nextInt(); // 取る商品の数、Mは使わないので読み捨て
            String product = sc.next(); //なんの商品を取るか

            int[] info = map.get(product); //商品情報取得 a -> "x,y,d"
            int x = info[0];
            int y = info[1];
            char d = (char) info[2]; //intからcharに変更

            // 取得位置を計算
            int tx = x, ty = y;
            if (d == 'E') tx++; //Eはひとつ右からしか取れない
            if (d == 'W') tx--; //左から
            if (d == 'N') ty++; //上から
            if (d == 'S') ty--; //下から

            int result = distFromStart[tx][ty] + distFromExit[tx][ty]; //合計距離計算

            sb.append(result).append("\n"); //結果を取得、保持
        }

        System.out.print(sb.toString()); //出力

        sc.close();
    }

    static int[][] bfs(int sx, int sy) {
        int[][] dist = new int[W][H]; //各マスまでの距離を保存
        for (int[] row : dist) Arrays.fill(row, -1); //もし-1なら未探索

        Queue<int[]> q = new LinkedList<>(); //キューの設定、qは探索待ちのマス
        q.add(new int[]{sx, sy});
        dist[sx][sy] = 0;

        while (!q.isEmpty()) { //キューが空になるまで
            int[] cur = q.poll();
            int x = cur[0], y = cur[1]; //今いるマス

            for (int i = 0; i < 4; i++) { //上下左右を試す
                int nx = x + dx[i];
                int ny = y + dy[i];

                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue; //範囲外かチェック
                if (blocked[nx][ny]) continue; //マスに入れるかどうかチェック
                if (dist[nx][ny] != -1) continue; //訪問済みかチェック

                dist[nx][ny] = dist[x][y] + 1; //1マス移動したから+1
                q.add(new int[]{nx, ny}); //次に探索するマスに追加
            }
        }

        return dist;
    }
}