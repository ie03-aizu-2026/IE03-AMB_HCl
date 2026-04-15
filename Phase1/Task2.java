import java.util.*;

public class Task2 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int n = scanner.nextInt();
        
        // 【変更なし】メモ帳の準備（分かりやすく名前を pairCount に変えました）
        Map<String, Integer> pairCount = new HashMap<>();
        
        // N人の顧客の購入履歴を読み込むループ
        for (int i = 0; i < n; i++) {
            int m = scanner.nextInt();
            
            // 【★変更点1】
            // 今回は「ペア」を作る必要があるので、いきなりメモ帳には書かず、
            // まずは1人のお客さんが買った商品をすべて一時的な配列（箱）に入れます。
            String[] items = new String[m];
            for (int j = 0; j < m; j++) {
                items[j] = scanner.next();
            }
            
            // 【★変更点2】
            // ペアの中の商品名もアルファベット順にするというルールがあるので、
            // ペアを作る前に、ここで買った商品の配列自体をソート（並べ替え）しておきます。
            Arrays.sort(items);
            
            // 【★変更点3】総当たり戦でペアを作る（二重ループ）
            // 例：[a, b, c] なら (a,b), (a,c), (b,c) という組み合わせを作ります。
            for (int j = 0; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    
                    // 2つの商品名をスペースでくっつけて、1つの「ペア名」にします。
                    String pair = items[j] + " " + items[k];
                    
                    // 【★変更点4】
                    // 単品ではなく、完成した「ペア名」をメモ帳に記録します。
                    pairCount.put(pair, pairCount.getOrDefault(pair, 0) + 1);
                }
            }
        }
        
        // ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
        // ▼ ここから下は、Task 1 から【完全に無変更】で使い回せます！ ▼
        // ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
        
        List<Map.Entry<String, Integer>> list = new ArrayList<>(pairCount.entrySet());
        
        list.sort((item1, item2) -> {
            int countCompare = item2.getValue().compareTo(item1.getValue());
            if (countCompare != 0) {
                return countCompare;
            } else {
                return item1.getKey().compareTo(item2.getKey());
            }
        });
        
        int q = scanner.nextInt();
        for (int i = 0; i < q; i++) {
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            for (int j = a - 1; j < b; j++) {
                System.out.println(list.get(j).getValue() + " " + list.get(j).getKey());
            }
        }
        scanner.close();
    }
}