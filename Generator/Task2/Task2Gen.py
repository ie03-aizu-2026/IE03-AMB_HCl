"""
Task2 input generator
Constraints: 1<=N<=100, 1<=M<=10, 1<=len(p)<=10, 1<=Q<=10, 1<=a<=b<=num_distinct_pairs
"""
import random
import string
import argparse


def rand_name(rng: random.Random) -> str:
    return ''.join(rng.choices(string.ascii_lowercase, k=rng.randint(1, 10)))


def generate(seed=None, N=None, max_N=100, max_M=10, max_Q=10, pool_size=20) -> str:
    rng = random.Random(seed)

    n = N if N is not None else rng.randint(1, max_N)
    pool = list({rand_name(rng) for _ in range(pool_size)}) or [rand_name(rng)]

    lines = [str(n)]
    pairs: set[str] = set()

    for i in range(n):
        # 先頭の顧客は必ず2品以上購入してペアが最低1件生成されるようにする
        m = rng.randint(2 if i == 0 else 1, max_M)
        items = [rng.choice(pool) for _ in range(m)]
        lines.append(f"{m} {' '.join(items)}")
        s = sorted(items)
        for j in range(len(s)):
            for k in range(j + 1, len(s)):
                pairs.add(f"{s[j]} {s[k]}")

    num_pairs = len(pairs)
    q = rng.randint(1, max_Q)
    lines.append(str(q))
    for _ in range(q):
        a = rng.randint(1, num_pairs)
        b = rng.randint(a, num_pairs)
        lines.append(f"{a} {b}")

    return '\n'.join(lines)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task2 random input generator')
    p.add_argument('--seed',  type=int, default=None, help='random seed')
    p.add_argument('--N',     type=int, default=None, help='customers (1-100)')
    p.add_argument('--max-M', type=int, default=10,   help='max items per customer')
    p.add_argument('--max-Q', type=int, default=10,   help='max queries')
    p.add_argument('--pool',  type=int, default=20,   help='product pool size')
    args = p.parse_args()
    print(generate(seed=args.seed, N=args.N, max_M=args.max_M, max_Q=args.max_Q, pool_size=args.pool))
