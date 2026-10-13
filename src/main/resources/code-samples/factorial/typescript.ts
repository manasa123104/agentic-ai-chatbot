function factorial(n: number): number {
  if (n <= 1) {
    return 1;
  }
  return n * factorial(n - 1);
}

const n: number = 10;
console.log(`${n}! = ${factorial(n)}`);
