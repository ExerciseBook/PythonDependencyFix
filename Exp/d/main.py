import apkg.example
from bpkg.example import prev
from cpkg.example import add
from cpkg.example import sub

if __name__ == '__main__':
    b = apkg.example.succ_v2(2)
    print(b)

    c = prev(2)
    print(c)

    m = add(b, c)
    print(m)

    n = sub(5, 3)
    print(n)
