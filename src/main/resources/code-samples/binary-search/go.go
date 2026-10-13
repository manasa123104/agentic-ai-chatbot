package main

import "fmt"

func binarySearch(values []int, target int) int {
	low, high := 0, len(values)-1
	for low <= high {
		mid := low + (high-low)/2
		if values[mid] == target {
			return mid
		} else if values[mid] < target {
			low = mid + 1
		} else {
			high = mid - 1
		}
	}
	return -1
}

func main() {
	values := []int{2, 5, 8, 12, 16, 23, 38, 56, 72, 91}
	target := 23
	index := binarySearch(values, target)
	if index >= 0 {
		fmt.Printf("Found %d at index %d\n", target, index)
	} else {
		fmt.Printf("%d not found\n", target)
	}
}
