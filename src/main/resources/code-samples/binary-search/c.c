#include <stdio.h>

int binary_search(const int values[], int length, int target) {
    int low = 0;
    int high = length - 1;
    while (low <= high) {
        int mid = low + (high - low) / 2;
        if (values[mid] == target) {
            return mid;
        } else if (values[mid] < target) {
            low = mid + 1;
        } else {
            high = mid - 1;
        }
    }
    return -1;
}

int main(void) {
    int values[] = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
    int length = sizeof(values) / sizeof(values[0]);
    int target = 23;
    int index = binary_search(values, length, target);
    if (index >= 0) {
        printf("Found %d at index %d\n", target, index);
    } else {
        printf("%d not found\n", target);
    }
    return 0;
}
