using System;

class BinarySearch
{
    static int Search(int[] values, int target)
    {
        int low = 0;
        int high = values.Length - 1;
        while (low <= high)
        {
            int mid = low + (high - low) / 2;
            if (values[mid] == target)
            {
                return mid;
            }
            else if (values[mid] < target)
            {
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }
        return -1;
    }

    static void Main()
    {
        int[] values = { 2, 5, 8, 12, 16, 23, 38, 56, 72, 91 };
        int target = 23;
        int index = Search(values, target);
        if (index >= 0)
        {
            Console.WriteLine($"Found {target} at index {index}");
        }
        else
        {
            Console.WriteLine($"{target} not found");
        }
    }
}
