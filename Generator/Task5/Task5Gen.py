"""
Task3/Task5 grid map input generator
Task3: 4<=W,H<=100, 1<=N<=20, 1<=Q<=20, M=1  (--task3)
Task5: 4<=W,H<=100, 1<=N<=20, 1<=Q<=20, 1<=M<=N

Grid rules reproduced from source:
  - bottom row (y=0) is all-blocked except entrance (1,0) and exit (W-2,0)
  - each product cell is blocked
  - pick-up position = cell adjacent to product in the shelf's facing direction
"""
import random
import string
import argparse

# direction -> (dx, dy) to reach pick-up cell from shelf cell
PICK_DELTA = {'N': (0, 1), 'S': (0, -1), 'E': (1, 0), 'W': (-1, 0)}


def rand_name(rng: random.Random, used: set) -> str:
    for _ in range(100_000):
        name = ''.join(rng.choices(string.ascii_lowercase, k=rng.randint(1, 10)))
        if name not in used:
            return name
    raise RuntimeError('could not generate a unique product name')


def build_candidates(W: int, H: int) -> dict:
    """Precompute valid shelf positions per direction."""
    candidates: dict[str, list] = {}
    for d, (dx, dy) in PICK_DELTA.items():
        pts = []
        for x in range(W):
            for y in range(1, H):          # y>=1: products not in bottom wall
                px, py = x + dx, y + dy
                if 0 <= px < W and 1 <= py < H:   # pick-up in bounds and not in wall
                    pts.append((x, y))
        candidates[d] = pts
    return candidates


def generate(seed=None, W=None, H=None, N=None, Q=None, task3=False) -> str:
    rng = random.Random(seed)

    W = W if W is not None else rng.randint(4, 100)
    H = H if H is not None else rng.randint(4, 100)
    N = N if N is not None else rng.randint(1, 20)
    Q = Q if Q is not None else rng.randint(1, 20)

    candidates = build_candidates(W, H)
    dirs = list(PICK_DELTA.keys())

    product_cells: set = set()   # cells occupied by shelf units (blocked)
    pickup_cells: set = set()    # pick-up positions of already-placed products
    products: list = []          # (x, y, name, dir)
    used_names: set = set()

    for _ in range(N):
        placed = False
        for _ in range(2000):
            d = rng.choice(dirs)
            pool = candidates[d]
            if not pool:
                continue
            x, y = rng.choice(pool)
            dx, dy = PICK_DELTA[d]
            px, py = x + dx, y + dy
            if (x, y) in product_cells:
                continue
            if (x, y) in pickup_cells:   # new shelf would block an existing pick-up
                continue
            if (px, py) in product_cells:  # new pick-up blocked by existing shelf
                continue
            name = rand_name(rng, used_names)
            product_cells.add((x, y))
            pickup_cells.add((px, py))
            used_names.add(name)
            products.append((x, y, name, d))
            placed = True
            break
        if not placed:
            break   # grid too dense; use however many were placed

    n_actual = len(products)
    lines = [f"{W} {H} {n_actual}"]
    for x, y, name, d in products:
        lines.append(f"{x} {y} {name} {d}")

    lines.append(str(Q))
    names = [p[2] for p in products]
    for _ in range(Q):
        m = 1 if task3 else rng.randint(1, n_actual)
        chosen = rng.sample(names, m)
        lines.append(f"{m} {' '.join(chosen)}")

    return '\n'.join(lines)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description='Task3/Task5 grid map input generator')
    p.add_argument('--seed',   type=int,            default=None)
    p.add_argument('--W',      type=int,            default=None, help='grid width  (4-100)')
    p.add_argument('--H',      type=int,            default=None, help='grid height (4-100)')
    p.add_argument('--N',      type=int,            default=None, help='number of products (1-20)')
    p.add_argument('--Q',      type=int,            default=None, help='number of queries  (1-20)')
    p.add_argument('--task3',  action='store_true',              help='Task3 mode: M=1 per query')
    args = p.parse_args()
    print(generate(seed=args.seed, W=args.W, H=args.H, N=args.N, Q=args.Q, task3=args.task3))
