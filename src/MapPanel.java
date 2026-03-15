import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

public class MapPanel extends JPanel {

    private final List<City> cities;

    // Basit Türkiye sınırları (kaba)
    private final double minLon = 26, maxLon = 45;
    private final double minLat = 36, maxLat = 43;

    private int selectedA = -1;
    private int selectedB = -1;
    private List<Integer> currentPath = Collections.emptyList();

    // Yol ağı (graph): her şehir için komşu indeksler
    private final List<List<Integer>> roads;

    // Graph kurma parametreleri
    private static final int K_NEAREST = 3;
    private static final double MAX_EDGE_KM = 450.0;

    // UI
    private static final int CITY_PICK_RADIUS_PX = 16;
    private static final boolean SHOW_CITY_NAMES = true;

    public MapPanel(List<City> cities) {
        this.cities = cities;
        this.roads = buildKnnRoads(K_NEAREST, MAX_EDGE_KM);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = findNearestCityIndex(e.getX(), e.getY(), CITY_PICK_RADIUS_PX);
                if (idx == -1) return;

                if (selectedA == -1 || (selectedA != -1 && selectedB != -1)) {
                    selectedA = idx;
                    selectedB = -1;
                    currentPath = Collections.emptyList();
                } else {
                    selectedB = idx;
                    currentPath = dijkstraPath(selectedA, selectedB);
                }
                repaint();
            }
        });
    }

    public void reset() {
        selectedA = -1;
        selectedB = -1;
        currentPath = Collections.emptyList();
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1) Yolları çiz (ince gri)
            if (roads != null && !roads.isEmpty()) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(190, 190, 190));

                for (int a = 0; a < roads.size(); a++) {
                    Point p1 = cityToPoint(cities.get(a));
                    for (int b : roads.get(a)) {
                        if (b <= a) continue; // çift çizimi engelle
                        Point p2 = cityToPoint(cities.get(b));
                        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    }
                }
            }

            // 2) Bulunan rotayı çiz (kalın siyah)
            if (currentPath != null && currentPath.size() >= 2) {
                g2.setStroke(new BasicStroke(3f));
                g2.setColor(new Color(30, 30, 30));

                for (int i = 0; i < currentPath.size() - 1; i++) {
                    Point p1 = cityToPoint(cities.get(currentPath.get(i)));
                    Point p2 = cityToPoint(cities.get(currentPath.get(i + 1)));
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }

                // Rota yazısı
                g2.setColor(Color.BLACK);
                int y = 20;
                g2.drawString("Rota:", 10, y);
                y += 16;

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < currentPath.size(); i++) {
                    if (i > 0) sb.append(" -> ");
                    sb.append(cities.get(currentPath.get(i)).getName());
                }
                g2.drawString(sb.toString(), 10, y);
            }

            // 3) Şehirleri çiz (nokta)
            for (int i = 0; i < cities.size(); i++) {
                City c = cities.get(i);
                Point p = cityToPoint(c);

                if (i == selectedA) g2.setColor(new Color(0, 128, 255));
                else if (i == selectedB) g2.setColor(new Color(255, 140, 0));
                else g2.setColor(Color.RED);

                g2.fillOval(p.x - 4, p.y - 4, 8, 8);
            }

            // 4) Şehir isimleri (harita açılır açılmaz hepsi görünsün)
            if (SHOW_CITY_NAMES) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(new Color(20, 20, 20));

                for (int i = 0; i < cities.size(); i++) {
                    City c = cities.get(i);
                    Point p = cityToPoint(c);
                    g2.drawString(c.getName(), p.x + 6, p.y - 6);
                }
            }

        } finally {
            g2.dispose();
        }
    }

    private Point cityToPoint(City c) {
        double lon = c.lon();
        double lat = c.lat();

        int x = (int) ((lon - minLon) / (maxLon - minLon) * getWidth());
        int y = (int) ((maxLat - lat) / (maxLat - minLat) * getHeight());
        return new Point(x, y);
    }

    private int findNearestCityIndex(int x, int y, int maxPixelDist) {
        int best = -1;
        double bestD2 = maxPixelDist * maxPixelDist;

        for (int i = 0; i < cities.size(); i++) {
            Point p = cityToPoint(cities.get(i));
            double dx = p.x - x;
            double dy = p.y - y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    // KNN yol ağı üret (çift yön)
    private List<List<Integer>> buildKnnRoads(int k, double maxEdgeKm) {
        int n = cities.size();
        ArrayList<List<Integer>> g = new ArrayList<>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            double[] dist = new double[n];
            Integer[] idx = new Integer[n];

            for (int j = 0; j < n; j++) {
                idx[j] = j;
                dist[j] = (i == j) ? Double.POSITIVE_INFINITY : distanceKm(cities.get(i), cities.get(j));
            }

            Arrays.sort(idx, (a, b) -> Double.compare(dist[a], dist[b]));

            int added = 0;
            for (int t = 0; t < n && added < k; t++) {
                int j = idx[t];
                double d = dist[j];
                if (Double.isInfinite(d)) continue;
                if (d > maxEdgeKm) break;

                if (!g.get(i).contains(j)) g.get(i).add(j);
                if (!g.get(j).contains(i)) g.get(j).add(i);
                added++;
            }
        }
        return g;
    }

    // Gerçek pathfinder: Dijkstra (ağırlık = km)
    private List<Integer> dijkstraPath(int startIdx, int targetIdx) {
        if (startIdx == targetIdx) return List.of(startIdx);

        int n = cities.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);

        class State {
            final int v;
            final double d;
            State(int v, double d) { this.v = v; this.d = d; }
        }

        PriorityQueue<State> pq = new PriorityQueue<>((a, b) -> Double.compare(a.d, b.d));
        dist[startIdx] = 0.0;
        pq.add(new State(startIdx, 0.0));

        while (!pq.isEmpty()) {
            State cur = pq.poll();
            int v = cur.v;
            if (visited[v]) continue;
            visited[v] = true;

            if (v == targetIdx) break;

            for (int to : roads.get(v)) {
                double w = distanceKm(cities.get(v), cities.get(to));
                double nd = dist[v] + w;
                if (nd < dist[to]) {
                    dist[to] = nd;
                    prev[to] = v;
                    pq.add(new State(to, nd));
                }
            }
        }

        if (prev[targetIdx] == -1) {
            // bağlantı yoksa en azından direkt çiz
            return List.of(startIdx, targetIdx);
        }

        ArrayList<Integer> path = new ArrayList<>();
        for (int v = targetIdx; v != -1; v = prev[v]) path.add(v);
        Collections.reverse(path);
        return path;
    }

    // Haversine (km)
    private double distanceKm(City a, City b) {
        double R = 6371.0;
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.lon() - a.lon());

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(h)));
        return R * c;
    }
}