public class Factorial {
    static long factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        int n = 10;
        System.out.println(n + "! = " + factorial(n));
    }
}
