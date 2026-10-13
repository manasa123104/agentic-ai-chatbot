fun factorial(n: Int): Long {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}

fun main() {
    val n = 10
    println("$n! = ${factorial(n)}")
}
