from apkg.example import succ
from bpkg.example import prev


def add(lhs: int, rhs: int) -> int:
    if rhs < 0:
        raise ValueError()
    if rhs == 0:
        return lhs
    return add(succ(lhs), rhs - 1)


def sub(lhs: int, rhs: int) -> int:
    if rhs > 0:
        raise ValueError()
    if rhs == 0:
        return lhs
    return sub(prev(lhs), rhs - 1)
