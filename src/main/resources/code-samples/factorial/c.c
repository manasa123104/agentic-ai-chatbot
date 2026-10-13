#include <stdio.h>

long long factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int main(void) {
    int n = 10;
    printf("%d! = %lld\n", n, factorial(n));
    return 0;
}
