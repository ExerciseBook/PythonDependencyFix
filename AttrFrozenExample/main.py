from attr import frozen


@frozen
class MyImmutableClass:
    x: int


if __name__ == '__main__':
    obj = MyImmutableClass(x=1)
    obj.x = 2
