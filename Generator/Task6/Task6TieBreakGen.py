"""
Task6 lexicographic tie-break test generator
検証内容: 「同じ最短距離になる商品集合が複数あるとき、個数最大 → 名前の連結文字列が
         辞書順最小」というタイブレークルール（Task6.java 119〜128行目）が正しく
         機能するか。

── グリッド設計の原理 ──
Task6のグリッドは棚セルと下端の壁以外はすべて通行可能な開放空間なので、障害物さえ
避ければ2点間のBFS距離はマンハッタン距離 |dx|+|dy| に一致する。
このとき、始点 A・終点 C の間に点 P を挟んでも

    dist(A,P) + dist(P,C) == dist(A,C)

が成り立つのは、P の x, y 座標がそれぞれ A, C の間（矩形内）にあるとき。この性質を
使うと「無料で拾える追加商品（＝距離を全く増やさない商品）」を狙って配置できる。

さらに、この矩形内の2点 P1, P2 が「片方は x が小さく y が大きい／もう片方は x が
大きく y が小さい」という関係（比較不能＝反鎖）にあると、単独では無料でもP1・P2を
同時に回収しようとすると単調な経路が組めず、必ず余分な距離がかかる。
これを利用して「個別には同じ距離になる複数の商品集合」を意図的に作り出す。

── 共通の骨格（全ケース共通） ──
grid: W=15, H=6 / entrance=(1,0), exit=(13,0)
必須商品 rock: shelf(7,5) S → pickup(7,4)
  entrance→rock→exit の最短距離 = 20 (= 1 + dist((1,1),(7,4)) + dist((7,4),(13,1)) + 1
                                        = 1 + 9 + 9 + 1)
  このルートの「入口→rock」区間の矩形は x∈[1,7], y∈[1,4] で、この中の任意の点は
  単独なら無料で追加できる。

── 各ケースの追加商品 ──
  zeta/yankee : pickup(2,4)  矩形の「左上寄り」(x小・y大)
  alpha       : pickup(6,1)  矩形の「右下寄り」(x大・y小) → zetaと反鎖
  mike        : pickup(6,1)  three_wayでのalpha相当（座標は同じ役割）
  zulu        : pickup(5,4)  yankeeと同じ y=4 上にあり、yankeeとは矩形内で
                              単調に並ぶため「同時に無料」で拾える

Usage:
  python3 Task6TieBreakGen.py [--case {two_way|three_way|count_priority}]

--case two_way         : 無料で追加できる商品が2つ(zeta, alpha)あり、片方しか
                          同時に選べない。→ 名前の辞書順で alpha が勝つべき。
                          期待値: "20 alpha rock"
--case three_way       : 無料で追加できる商品が3つ(zeta, alpha, mike)に増える。
                          3すべてが互いに反鎖の関係で同時に選べない。
                          最初に見つかった候補で打ち切る素朴な実装ではなく、
                          全候補を比較しているかを確認する。
                          期待値: "20 alpha rock"
--case count_priority  : yankee・zulu は矩形内で単調な位置関係にあるため同時に
                          無料で拾える(個数2で追加)。一方 alpha は単独でのみ無料
                          (個数1で追加)。alpha の名前が辞書順で最も若いにも関わらず、
                          「個数優先」で yankee+zulu の組が選ばれるべきことを確認する。
                          期待値: "20 rock yankee zulu"

各ケースの期待値は、Task6.javaのロジックをそのまま再実装した独立のPython参照実装
(../../../../scratchpad 等で実行したBFS+ビットDP)、および実際にコンパイルした
Task6.javaの出力と突き合わせて確認済み。
"""
import argparse
import sys

CASES = {
    'two_way': {
        'input': """\
15 6 3
7 5 rock S
2 5 zeta S
6 2 alpha S
1
1 rock""",
        'expected': "20 alpha rock",
        # {rock,zeta} と {rock,alpha} はどちらも距離20(=追加コスト0)で並ぶ。
        # 素朴に「最初に見つかった距離一致の集合」を採用する実装だと
        # (商品を読み込み順にビット0=rock,1=zeta,2=alphaとすると mask=3 の
        #  {rock,zeta} が先に見つかってしまい) "20 rock zeta" と誤答する。
    },
    'three_way': {
        'input': """\
15 6 4
7 5 rock S
2 5 zeta S
4 3 alpha S
6 2 mike S
1
1 rock""",
        'expected': "20 alpha rock",
        # {rock,zeta} {rock,alpha} {rock,mike} の3集合が全て距離20で並ぶ。
        # 2択のときだけ正しく、3択に増えると崩れるような「隣同士だけ比較する」
        # 実装のバグを検出できる。
    },
    'count_priority': {
        'input': """\
15 6 4
7 5 rock S
2 5 yankee S
5 5 zulu S
6 2 alpha S
1
1 rock""",
        'expected': "20 rock yankee zulu",
        # {rock,yankee,zulu}(個数3, 距離20) と {rock,alpha}(個数2, 距離20) が
        # 両方とも距離20で並ぶ。alpha は名前の辞書順で最も若いが、個数が少ない
        # ため負ける必要がある。「個数比較より先に文字列比較をしてしまう」
        # 実装のバグを検出できる。
    },
}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task6 lexicographic tie-break test generator')
    p.add_argument('--case', choices=list(CASES.keys()), default='two_way',
                    help='test case to output (default: two_way)')
    args = p.parse_args()

    case = CASES[args.case]
    print(case['input'])
    print('--- expected output ---', file=sys.stderr)
    print(case['expected'],          file=sys.stderr)
