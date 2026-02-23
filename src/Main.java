import java.io.File;
import java.util.Scanner;

void main() {
    String fileName = "tr.csv";
    File file = new File(fileName);

    if (!file.exists()) {
        System.out.printf("%s can not be found.", fileName);
        System.exit(1);
    }

    try (Scanner inputFile = new Scanner(file)) {
        System.out.printf("OK: %s opened.%n", fileName);

        int capitalIndex = 6;
        while (inputFile.hasNextLine()){
            String line = inputFile.nextLine();
            String[] values = line.split(",");


            String capital = values[capitalIndex].trim();
            if ("admin".equalsIgnoreCase(capital) || "primary".equalsIgnoreCase(capital)) {
                System.out.println(values[0] + "," + values[1] + "," + values[2]);
            }
        }
    } catch (FileNotFoundException e) {
        System.out.printf("%s can not be opened.%n", fileName);
    }
}
