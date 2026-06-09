"""
Task1 tie-break test generator
検証内容: カウントが同じ商品が複数あるとき、商品名のアルファベット昇順で出力されるか

Usage:
  python3 Task1TieBreak.py [--case {all_tied|partial_tie}]

--case all_tied   : 全商品が同カウント。入力は逆アルファベット順に並べ、
                    出力がアルファベット順になるか確認する。
--case partial_tie: 上位2商品が同カウント、3位は異なるカウント。
                    タイブレークが必要な部分だけに絞って確認する。
"""
import argparse
import sys

CASES = {
    'all_tied': {
        'input': """\
1
4 delta cherry banana apple
1
1 4""",
        'expected': """\
1 apple
1 banana
1 cherry
1 delta""",
    },
    'partial_tie': {
        'input': """\
3
2 cherry banana
2 cherry banana
1 apple
1
1 3""",
        'expected': """\
2 banana
2 cherry
1 apple""",
    },
}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task1 tie-break test generator')
    p.add_argument('--case', choices=list(CASES.keys()), default='all_tied',
                   help='test case to output (default: all_tied)')
    args = p.parse_args()

    case = CASES[args.case]
    print(case['input'])
    print('--- expected output ---', file=sys.stderr)
    print(case['expected'],          file=sys.stderr)
