"""
Task4 large-scale input generator
制約の上限値（N=10000, M=20）で固定した入力を生成する。
Task4Gen.py との違い: N・M がデフォルトで上限値に固定されており、
引数なしで実行するだけで必ずワーストケースの入力が得られる。

Usage:
  python3 Task4StressGen.py [--seed S] [--N n] [--M m] [--pool p]
"""
import sys
import os
import random
import string
import argparse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'Task2'))
from Task2Gen import rand_name


def generate(seed=None, N=10000, M=20, pool_size=50) -> str:
    rng = random.Random(seed)

    pool = list({rand_name(rng) for _ in range(pool_size)}) or [rand_name(rng)]

    lines = [str(N)]
    pairs: set[str] = set()

    for _ in range(N):
        items = [rng.choice(pool) for _ in range(M)]
        lines.append(f"{M} {' '.join(items)}")
        s = sorted(items)
        for j in range(len(s)):
            for k in range(j + 1, len(s)):
                pairs.add(f"{s[j]} {s[k]}")

    num_pairs = len(pairs)
    lines.append("10")   # Q = 10（上限固定）
    for _ in range(10):
        a = rng.randint(1, num_pairs)
        b = rng.randint(a, num_pairs)
        lines.append(f"{a} {b}")

    return '\n'.join(lines)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task4 large-scale input generator')
    p.add_argument('--seed', type=int, default=None, help='random seed')
    p.add_argument('--N',    type=int, default=10000, help='customers (default: 10000)')
    p.add_argument('--M',    type=int, default=20,    help='items per customer (default: 20)')
    p.add_argument('--pool', type=int, default=50,    help='product pool size (default: 50)')
    args = p.parse_args()
    print(generate(seed=args.seed, N=args.N, M=args.M, pool_size=args.pool))
