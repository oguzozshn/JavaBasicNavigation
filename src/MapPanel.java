import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MapPanel extends JPanel {

    private final List<City> cities;

    public MapPanel(List<City> cities) {
        this.cities = cities;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Basit sınırlar (Türkiye için kaba aralıklar)
        double minLon = 26, maxLon = 45;
        double minLat = 36, maxLat = 42;

        g.setColor(Color.RED);

        for (City c : cities) {
            double lat = Double.parseDouble(c.getLat());
            double lon = Double.parseDouble(c.getLng());

            int x = (int) ((lon - minLon) / (maxLon - minLon) * getWidth());
            int y = (int) ((maxLat - lat) / (maxLat - minLat) * getHeight());

            g.fillOval(x - 4, y - 4, 8, 8);

            g.setColor(Color.BLACK);
            g.drawString(c.getName(), x + 6, y);
            g.setColor(Color.RED);
        }
    }
}