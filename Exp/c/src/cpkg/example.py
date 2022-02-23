from apkg.example import succ_v2
from bpkg.example import prev


def add(lhs: int, rhs: int) -> int:
    if rhs < 0:
        raise ValueError()
    if rhs == 0:
        return lhs
    return add(succ_v2(lhs), rhs - 1)


def sub(lhs: int, rhs: int) -> int:
    if rhs > 0:
        raise ValueError()
    if rhs == 0:
        return lhs
    return sub(prev(lhs), rhs - 1)
