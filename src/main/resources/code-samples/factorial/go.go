package main

import "fmt"

func factorial(n int) int64 {
	if n <= 1 {
		return 1
	}
	return int64(n) * factorial(n-1)
}

func main() {
	n := 10
	fmt.Printf("%d! = %d\n", n, factorial(n))
}
