import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;

public class Tester {
    public static void main(String[] args) throws FileNotFoundException {
        FileReader file = new FileReader("/home/012/q/qx/qxw170003/validation.txt");
        Scanner scanner = new Scanner(file);
        String prevLine = "";
        String currLine = "";
        while (scanner.hasNextLine()) {
            currLine = scanner.nextLine();
            if (prevLine.compareTo(currLine) >= 0) {
                System.out.println("Violate");
                break;
            }
            prevLine = currLine;
        }
        System.out.println("All correct, no violation");
    }
}