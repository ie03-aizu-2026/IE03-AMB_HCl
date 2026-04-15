import java.util.*;

public class Task1 {
    public static void main(String[] args) {
        // キーボード（標準入力）からの入力を受け取るための準備
        Scanner scanner = new Scanner(System.in);

        
        // 顧客数 N を読み込む
        int n = scanner.nextInt();
        
        // 商品ごとの購入回数を記録するマップ（HashMap）
        Map<String, Integer> productCount = new HashMap<>();
        
        // N人の顧客の購入履歴を読み込むループ
        for (int i = 0; i < n; i++) {
            int m = scanner.nextInt(); // 顧客が購入した商品の数
            for (int j = 0; j < m; j++) {
                String product = scanner.next(); // 商品名を読み込む
                // 商品がすでにマップにあれば+1、なければ1として登録
                productCount.put(product, productCount.getOrDefault(product, 0) + 1);
            }
        }
        
        // 並べ替え（ソート）をするために、マップのデータをリストに変換する
        List<Map.Entry<String, Integer>> list = new ArrayList<>(productCount.entrySet());
        
        // ソートのルールを決める
        list.sort((item1, item2) -> {
            // 1. 購入回数で比較 (item2 - item1 で降順になる)
            int countCompare = item2.getValue().compareTo(item1.getValue());
            
            // 回数が違うなら、回数の比較結果をそのまま使う
            if (countCompare != 0) {
                return countCompare;
            } else {
                // 2. 回数が同じなら、商品名（アルファベット順）で比較（昇順）
                return item1.getKey().compareTo(item2.getKey());
            }
        });
        
        // クエリ数 Q を読み込む
        int e = scanner.nextInt();
        
        // Q個のクエリを処理するループ
        for (int i = 0; i < e; i++) {
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            
            // a番目からb番目までを出力 
            // ※プログラミングの世界では順番は「0」から数え始めるので -1 しています
            for (int j = a - 1; j < b; j++) {
                System.out.println(list.get(j).getValue() + " " + list.get(j).getKey());
            }
        }
        
        // 入力の受付を終了する
        scanner.close();
    }
}