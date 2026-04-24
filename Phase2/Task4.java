package IE03_AmbHCl.Phase2;

import java.io.*;
import java.util.*;

public class Task4 {
    // 【変更点1】例外処理（エラーが起きた時のための保険）の宣言を追加
    public static void main(String[] args) throws IOException {
        
        // 【変更点2】Scannerの代わりに、大量データを爆速で読み込むための道具を準備
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        
        // 顧客数 N を読み込む（使い方が少し変わります）
        int n = Integer.parseInt(st.nextToken());
        
        Map<String, Integer> pairCount = new HashMap<>();
        
        for (int i = 0; i < n; i++) {
            // 新しい行を読み込む
            st = new StringTokenizer(br.readLine());
            int m = Integer.parseInt(st.nextToken());
            
            String[] items = new String[m];
            for (int j = 0; j < m; j++) {
                items[j] = st.nextToken();
            }
            
            // 辞書順に並べ替え
            Arrays.sort(items);
            
            for (int j = 0; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    
                    // 【変更点3】Stringの足し算（+）は遅いので、高速に合体させる専用ツールを使う
                    StringBuilder sb = new StringBuilder();
                    sb.append(items[j]).append(" ").append(items[k]);
                    String pair = sb.toString(); // 完成したペアの名前
                    
                    // HashMapへの記録（Task 2と全く同じ）
                    pairCount.put(pair, pairCount.getOrDefault(pair, 0) + 1);
                }
            }
        }
        
        // ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
        // ▼ ここから下は、Task 2 から【完全に無変更】で使い回せます！ ▼
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
        
        // クエリ（Q）の読み込み
        st = new StringTokenizer(br.readLine());
        int q = Integer.parseInt(st.nextToken());
        
        for (int i = 0; i < q; i++) {
            st = new StringTokenizer(br.readLine());
            int a = Integer.parseInt(st.nextToken());
            int b = Integer.parseInt(st.nextToken());
            
            for (int j = a - 1; j < b; j++) {
                System.out.println(list.get(j).getValue() + " " + list.get(j).getKey());
            }
        }
    }
}
