def factorial(n: int) -> int:
    if n <= 1:
        return 1
    return n * factorial(n - 1)


if __name__ == "__main__":
    n = 10
    print(f"{n}! = {factorial(n)}")
