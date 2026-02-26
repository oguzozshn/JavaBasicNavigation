import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        String fileName = "tr.csv";
        File file = new File(fileName);

        if (!file.exists()) {
            System.out.printf("%s can not be found.%n", fileName);
            return;
        }

        List<City> cities = new ArrayList<>();

        try (Scanner inputFile = new Scanner(file)) {
            System.out.printf("OK: %s opened.%n", fileName);

            int capitalIndex = 6;
            while (inputFile.hasNextLine()) {
                String line = inputFile.nextLine();
                String[] values = line.split(",");

                if (values.length <= capitalIndex) continue;

                String capital = values[capitalIndex].trim();
                if ("admin".equalsIgnoreCase(capital) || "primary".equalsIgnoreCase(capital)) {
                    cities.add(new City(values[0], values[1], values[2]));
                }
            }
        } catch (FileNotFoundException e) {
            System.out.printf("%s can not be opened.%n", fileName);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Harita");
            frame.setSize(800, 600);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.add(new MapPanel(cities));
            frame.setVisible(true);
        });
    }
}

