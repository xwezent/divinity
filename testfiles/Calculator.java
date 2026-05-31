public class Calculator {
    public int add(int a, int b) { return a + b; }
    public int sub(int a, int b) { return a - b; }
    public int mul(int a, int b) { return a * b; }
    public int div(int a, int b) { return a / b; }
    public int mod(int a, int b) { return a % b; }
    public boolean gt(int a, int b) { return a > b; }
    public int and(int a, int b) { return a & b; }
    
    public static int square(int x) { return x * x; }
    
    public double avg(int[] arr) {
        double sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum / arr.length;
    }
}