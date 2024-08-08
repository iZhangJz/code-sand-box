import java.io.*;
import java.util.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String args[]) throws Exception {
        Scanner cin = new Scanner(System.in);
        int a = cin.nextInt(), b = cin.nextInt();
        List<byte[]> bytes = new ArrayList<>();
        System.out.println(a + b);
        while (true) {
            bytes.add(new byte[10000]);
        }
    }
}