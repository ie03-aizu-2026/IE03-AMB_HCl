# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ビルドと実行

パッケージ宣言があるファイル（Task3以降）はリポジトリルートから実行する。

```bash
# コンパイル（ルートから）
javac -d . Common/GridMap.java Phase1/Task3.java
javac -d . Phase2/Task5.java

# 実行（標準入力から）
java IE03_AmbHCl.Phase1.Task3 < input.txt

# パッケージ宣言なし（Task1, Task2）
javac Phase1/Task1.java && java -cp Phase1 Task1 < input.txt
```

## プロジェクト構成と設計

スーパーマーケットの動線最適化をテーマにしたアルゴリズム演習（競技プログラミング形式）。各タスクは標準入力から読み込み、標準出力へ結果を出力する。

### フェーズ構成

| フェーズ | タスク | 内容 |
|----------|--------|------|
| Phase1 | Task1 | 商品の購入頻度集計・ランキング出力（単品） |
| Phase1 | Task2 | Task1の応用：商品ペアの共起頻度集計 |
| Phase1 | Task3 | グリッドマップ上のBFS最短経路（入口→商品棚→出口） |
| Phase2 | Task4 | Task2の高速化（BufferedReader + StringTokenizer） |
| Phase2 | Task5 | Task3と同内容（パッケージ化済み） |
| Phase2 | Task6 | 未実装 |
| Phase3 | Task7 | 未実装 |

### 共通ライブラリ

`Common/GridMap.java`（パッケージ: `IE03_AmbHCl.Common`）
- `bfs(sx, sy, blocked, W, H)`: 指定座標からの4方向BFS距離マップを返す
- グリッド座標系: `blocked[x][y] == true` は通行不可

### パッケージとクラスの対応

- Task1, Task2: パッケージ宣言なし（デフォルトパッケージ）
- Task3以降: `IE03_AmbHCl.<フェーズ名>` パッケージを使用、`GridMap`をimportして利用

### タスク内の共通パターン（Task3/Task5）

グリッドマップ問題の解法：
1. グリッド（W×H）と商品情報（座標・向き）を読み込む
2. 下端の行全体をブロック、入口`(1,0)`と出口`(W-2,0)`のみ開放
3. 入口・出口それぞれからBFSで全マスへの距離を事前計算
4. 各クエリで「向き（N/S/E/W）」から商品棚の取得位置を計算し、`distFromStart + distFromExit` を出力

### パフォーマンス最適化（Phase2）

大量データ向けに `Scanner` → `BufferedReader + StringTokenizer` へ置き換え、文字列連結を `StringBuilder` に変更。
