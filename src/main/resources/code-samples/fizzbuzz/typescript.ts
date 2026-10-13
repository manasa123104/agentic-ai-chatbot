function fizzBuzz(i: number): string {
  if (i % 15 === 0) {
    return "FizzBuzz";
  } else if (i % 3 === 0) {
    return "Fizz";
  } else if (i % 5 === 0) {
    return "Buzz";
  }
  return String(i);
}

for (let i: number = 1; i <= 15; i++) {
  console.log(fizzBuzz(i));
}
