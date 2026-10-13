using System;

class Factorial
{
    static long Compute(int n)
    {
        if (n <= 1)
        {
            return 1;
        }
        return n * Compute(n - 1);
    }

    static void Main()
    {
        int n = 10;
        Console.WriteLine($"{n}! = {Compute(n)}");
    }
}
