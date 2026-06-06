"""
Task4 input generator (large-scale version of Task2)
Constraints: 1<=N<=10000, 1<=M<=20, 1<=len(p)<=10, 1<=Q<=10, 1<=a<=b<=num_distinct_pairs

Input format is identical to Task2; only constraint ceilings differ.
"""
import sys
import os
import argparse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'Task2'))
from Task2Gen import generate


if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task4 random input generator')
    p.add_argument('--seed',  type=int, default=None, help='random seed')
    p.add_argument('--N',     type=int, default=None, help='customers (1-10000)')
    p.add_argument('--max-M', type=int, default=20,   help='max items per customer')
    p.add_argument('--max-Q', type=int, default=10,   help='max queries')
    p.add_argument('--pool',  type=int, default=50,   help='product pool size')
    args = p.parse_args()
    print(generate(seed=args.seed, N=args.N, max_N=10000,
                   max_M=args.max_M, max_Q=args.max_Q, pool_size=args.pool))
