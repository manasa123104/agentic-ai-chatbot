fun binarySearch(values: IntArray, target: Int): Int {
    var low = 0
    var high = values.size - 1
    while (low <= high) {
        val mid = low + (high - low) / 2
        when {
            values[mid] == target -> return mid
            values[mid] < target -> low = mid + 1
            else -> high = mid - 1
        }
    }
    return -1
}

fun main() {
    val values = intArrayOf(2, 5, 8, 12, 16, 23, 38, 56, 72, 91)
    val target = 23
    val index = binarySearch(values, target)
    if (index >= 0) {
        println("Found $target at index $index")
    } else {
        println("$target not found")
    }
}
