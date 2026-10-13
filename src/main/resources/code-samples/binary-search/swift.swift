func binarySearch(_ values: [Int], _ target: Int) -> Int? {
    var low = 0
    var high = values.count - 1
    while low <= high {
        let mid = low + (high - low) / 2
        if values[mid] == target {
            return mid
        } else if values[mid] < target {
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return nil
}

let values = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91]
let target = 23
if let index = binarySearch(values, target) {
    print("Found \(target) at index \(index)")
} else {
    print("\(target) not found")
}
