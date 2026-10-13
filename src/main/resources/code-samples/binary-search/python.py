def binary_search(values: list[int], target: int) -> int:
    low, high = 0, len(values) - 1
    while low <= high:
        mid = (low + high) // 2
        if values[mid] == target:
            return mid
        if values[mid] < target:
            low = mid + 1
        else:
            high = mid - 1
    return -1


if __name__ == "__main__":
    values = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91]
    target = 23
    index = binary_search(values, target)
    if index >= 0:
        print(f"Found {target} at index {index}")
    else:
        print(f"{target} not found")
