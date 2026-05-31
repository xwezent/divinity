public class TestClass {
    private String name;
    private int count;

    public TestClass(String name) {
        this.name = name;
        this.count = 0;
    }

    public int increment() {
        int old = count;
        count = count + 1;
        return old;
    }

    public String greet(String greeting) {
        if (greeting == null) {
            return "Hello, " + name;
        } else {
            return greeting + ", " + name;
        }
    }

    public boolean isPositive(int value) {
        return value > 0;
    }
}