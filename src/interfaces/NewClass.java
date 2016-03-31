package interfaces;


import java.util.stream.IntStream;

public class NewClass {
    static int[] numbers = {2, 6, 8, 9, 4, 6, 8, 8};
    
    public static void main(String[] args) {
        int result = IntStream.of(numbers).parallel().map((x) -> x*x).reduce((x, y) -> x+y).getAsInt();
        System.out.println(result);
    }
}
