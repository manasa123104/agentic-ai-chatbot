function binarySearch(values: number[], target: number): number {
  let low = 0;
  let high = values.length - 1;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (values[mid] === target) {
      return mid;
    } else if (values[mid] < target) {
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return -1;
}

const values: number[] = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91];
const target: number = 23;
const index: number = binarySearch(values, target);
if (index >= 0) {
  console.log(`Found ${target} at index ${index}`);
} else {
  console.log(`${target} not found`);
}
