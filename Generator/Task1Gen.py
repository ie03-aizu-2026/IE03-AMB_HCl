"""
Task1 input generator
Constraints: 1<=N<=100, 1<=M<=10, 1<=len(product)<=10, 1<=Q<=10, 1<=a<=b<=num_distinct_products
"""
import random
import string
import argparse


def rand_name(rng: random.Random) -> str:
    length = rng.randint(1, 10)
    return ''.join(rng.choices(string.ascii_lowercase, k=length))


def generate(seed=None, N=None, max_M=10, max_Q=10, pool_size=20) -> str:
    rng = random.Random(seed)

    n = N if N is not None else rng.randint(1, 100)

    # Build a product pool (allow fewer unique names due to collisions)
    pool = list({rand_name(rng) for _ in range(pool_size)})
    if not pool:
        pool = [rand_name(rng)]

    lines = [str(n)]
    purchased: set[str] = set()

    for _ in range(n):
        m = rng.randint(1, max_M)
        items = [rng.choice(pool) for _ in range(m)]
        purchased.update(items)
        lines.append(f"{m} {' '.join(items)}")

    num_distinct = len(purchased)

    q = rng.randint(1, max_Q)
    lines.append(str(q))
    for _ in range(q):
        a = rng.randint(1, num_distinct)
        b = rng.randint(a, num_distinct)
        lines.append(f"{a} {b}")

    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Task1 random input generator')
    parser.add_argument('--seed',     type=int, default=None, help='random seed for reproducibility')
    parser.add_argument('--N',        type=int, default=None, help='number of customers (1-100)')
    parser.add_argument('--max-M',    type=int, default=10,   help='max items per customer (default 10)')
    parser.add_argument('--max-Q',    type=int, default=10,   help='max number of queries (default 10)')
    parser.add_argument('--pool',     type=int, default=20,   help='product pool size (default 20)')
    args = parser.parse_args()

    print(generate(
        seed=args.seed,
        N=args.N,
        max_M=args.max_M,
        max_Q=args.max_Q,
        pool_size=args.pool,
    ))
