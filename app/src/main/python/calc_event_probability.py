"""Solve two-event probabilities using exact constraints on the four Venn regions."""
from fractions import Fraction
from itertools import combinations
from calc_shared import require

REGIONS = {
    "pa": (1, 1, 0, 0),
    "pb": (1, 0, 1, 0),
    "intersection": (1, 0, 0, 0),
    "union": (1, 1, 1, 0),
    "onlyA": (0, 1, 0, 0),
    "neither": (0, 0, 0, 1),
}
LABELS = {
    "pa": "P(A)", "pb": "P(B)", "intersection": "P(A ∩ B)",
    "union": "P(A ∪ B)", "conditional": "P(A | B)",
    "reverse": "P(B | A)", "onlyA": "P(A ∩ Bᶜ)", "neither": "P(Aᶜ ∩ Bᶜ)",
}


def _unique_solution(rows):
    matrix = [[Fraction(v) for v in row] for row in rows]
    pivots = []
    for column in range(4):
        pivot = next((i for i in range(len(pivots), len(matrix)) if matrix[i][column]), None)
        if pivot is None:
            continue
        index = len(pivots)
        matrix[index], matrix[pivot] = matrix[pivot], matrix[index]
        divisor = matrix[index][column]
        matrix[index] = [v / divisor for v in matrix[index]]
        for i in range(len(matrix)):
            if i != index:
                multiplier = matrix[i][column]
                matrix[i] = [v - multiplier * p for v, p in zip(matrix[i], matrix[index])]
        pivots.append(column)
    if any(not any(row[:4]) and row[4] for row in matrix) or len(pivots) != 4:
        return None
    return tuple(matrix[i][4] for i in range(4))


def solve_events(known, operation):
    require(operation in LABELS, "Unsupported probability operation")
    require(bool(known), "Choose at least one given probability")
    require(all(key in LABELS for key in known), "Invalid probability inputs")
    rows = [(1, 1, 1, 1, 1)]
    for key, value in known.items():
        if key in ("conditional", "reverse"):
            # x = P(A|B) (x+z), or x = P(B|A) (x+y).
            rows.append((1-value, -value if key == "reverse" else 0,
                         -value if key == "conditional" else 0, 0, 0))
        else:
            rows.append((*REGIONS[key], value))
    # The feasible set is a bounded simplex slice. Its vertices occur when
    # enough Venn regions are zero; exact arithmetic also catches tiny conflicts.
    vertices = set()
    for count in range(4):
        for zeros in combinations(range(4), count):
            candidate = _unique_solution(rows + [tuple(int(i == j) for i in range(4)) + (0,) for j in zeros])
            if candidate is not None and all(v >= 0 for v in candidate):
                vertices.add(candidate)
    require(bool(vertices), "Given probabilities are inconsistent")
    for key in ("conditional", "reverse"):
        if key in known:
            index = 2 if key == "conditional" else 1
            require(any(v[0]+v[index] > 0 for v in vertices), "Conditioning event has zero probability")
    if operation in ("conditional", "reverse"):
        index = 2 if operation == "conditional" else 1
        answers = {v[0]/(v[0]+v[index]) for v in vertices if v[0]+v[index] > 0}
        require(bool(answers), "Conditioning event has zero probability")
    else:
        answers = {sum(c*v for c, v in zip(REGIONS[operation], vertex)) for vertex in vertices}
    require(len(answers) == 1, "Given probabilities do not determine a unique answer")
    return answers.pop()
