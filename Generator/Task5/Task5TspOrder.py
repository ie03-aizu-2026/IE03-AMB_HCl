"""
Task5 TSP optimal order test generator
検証内容: 商品の訪問順序を最適化するTSPが正しく機能するか

グリッド構造 (W=10, H=5):
  entrance (1,0),  exit (8,0)
  apple  : shelf (2,2) 向き S → pick-up (2,1)  入口に近い
  banana : shelf (7,2) 向き S → pick-up (7,1)  出口に近い
  cherry : shelf (5,2) 向き S → pick-up (5,1)  中間

BFS 距離 (y=1 の通路を直進、障害物なし):
  entrance→apple=2,  entrance→cherry=5,  entrance→banana=7
  apple→cherry=3,    cherry→banana=2,    apple→banana=5
  apple→exit=7,      cherry→exit=4,      banana→exit=2

最適経路:  apple→cherry→banana = 2+3+2+2 = 9
入力逆順: banana→cherry→apple = 7+2+3+7 = 19  ← 素朴実装はこちらを出力してしまう

Usage:
  python3 Task5TspOrder.py [--case {m2|m3|both}]

--case m2  : M=2, 入力順 banana apple（非最適）→ 正解 9 / 素朴実装 19
--case m3  : M=3, 入力順 banana cherry apple（非最適）→ 正解 9 / 素朴実装 19
--case both: 上記 2 クエリを 1 入力にまとめる。出力 2 行（どちらも 9）
"""
import argparse
import sys

_HEADER = "10 5 3"
_PRODUCTS = """\
2 2 apple S
7 2 banana S
5 2 cherry S"""

CASES = {
    'm2': {
        'input': f"""\
{_HEADER}
{_PRODUCTS}
1
2 banana apple""",
        'expected': "9",
        # 素朴に banana→apple 順で巡ると
        #   entrance→banana=7, banana→apple=5, apple→exit=7  合計 19
        # 最適順 apple→banana:
        #   entrance→apple=2,  apple→banana=5, banana→exit=2 合計 9
    },
    'm3': {
        'input': f"""\
{_HEADER}
{_PRODUCTS}
1
3 banana cherry apple""",
        'expected': "9",
        # 素朴に banana→cherry→apple 順で巡ると
        #   entrance→banana=7, banana→cherry=2, cherry→apple=3, apple→exit=7  合計 19
        # 最適順 apple→cherry→banana:
        #   entrance→apple=2,  apple→cherry=3,  cherry→banana=2, banana→exit=2 合計 9
    },
    'both': {
        'input': f"""\
{_HEADER}
{_PRODUCTS}
2
2 banana apple
3 banana cherry apple""",
        'expected': "9\n9",
    },
}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task5 TSP optimal order test generator')
    p.add_argument('--case', choices=list(CASES.keys()), default='both',
                   help='test case to output (default: both)')
    args = p.parse_args()

    case = CASES[args.case]
    print(case['input'])
    print('--- expected output ---', file=sys.stderr)
    print(case['expected'],          file=sys.stderr)
