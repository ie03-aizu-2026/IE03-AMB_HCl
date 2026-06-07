"""
Task1 count aggregation test generator
検証内容: カウントが正しく集計されるか

Usage:
  python3 Task1CountAgg.py [--case {single|multi|mixed}]

--case single : 1人の顧客が同じ商品を3回購入 → count=3 になるか
--case multi  : 3人の顧客がそれぞれ同じ商品を1回購入 → count=3 になるか
                (single と同じ出力になるはず)
--case mixed  : 1人が2回 + 別の1人が1回 購入 → count=3 になるか
                同時に別商品と比較してソートも確認する
"""
import argparse
import sys

CASES = {
    'single': {
        'input': """\
1
3 apple apple apple
1
1 1""",
        'expected': """\
3 apple""",
    },
    'multi': {
        'input': """\
3
1 apple
1 apple
1 apple
1
1 1""",
        'expected': """\
3 apple""",
    },
    'mixed': {
        'input': """\
3
2 apple apple
1 apple
1 banana
1
1 2""",
        'expected': """\
3 apple
1 banana""",
    },
}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task1 count aggregation test generator')
    p.add_argument('--case', choices=list(CASES.keys()), default='single',
                   help='test case to output (default: single)')
    args = p.parse_args()

    case = CASES[args.case]
    print(case['input'])
    print('--- expected output ---', file=sys.stderr)
    print(case['expected'],          file=sys.stderr)
