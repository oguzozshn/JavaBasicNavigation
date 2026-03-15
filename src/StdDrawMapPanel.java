import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StdDrawMapPanel {
    private final List<City> cities;
    private final List<List<double[]>> turkeyBorders;

    private final double minLon = 25, maxLon = 45;
    private final double minLat = 35, maxLat = 43;
    private final double refLat = (minLat + maxLat) / 2.0;

    private int selectedA = -1;
    private int selectedB = -1;
    private List<Integer> currentPath = Collections.emptyList();
    private boolean pathUnreachable = false;

    private List<List<Integer>> roads;

    // Unreachable şehirler kümesi
    private final Set<Integer> unreachableCities = new HashSet<>();

    private static final int K_NEAREST = 3;

    // UI
    private static final int CANVAS_WIDTH  = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final int CITY_PICK_RADIUS_PX = 16;

    // Buton alanları (piksel koordinatları)
    private static final int BTN_Y       = CANVAS_HEIGHT - 45;
    private static final int BTN_H       = 30;
    private static final int BTN_MAKE_X  = 10;
    private static final int BTN_MAKE_W  = 160;
    private static final int BTN_RESET_X = 180;
    private static final int BTN_RESET_W = 80;

    private boolean makeUnreachableMode = false;
    private boolean previousMousePressed = false;

    public StdDrawMapPanel(List<City> cities) {
        this.cities = cities;
        this.roads = buildKnnRoads(K_NEAREST);
        this.turkeyBorders = loadTurkeyBorders("src/Turkey.json");
    }

    // -----------------------------------------------------------------------
    // Çizim
    // -----------------------------------------------------------------------

    private void drawCities() {
        double dotRadius = 4.0;
        double labelOffsetY = 10.0;

        for (int i = 0; i < cities.size(); i++) {
            Point p = cityToPoint(cities.get(i));

            if (unreachableCities.contains(i)) {
                StdDraw.setPenColor(new Color(160, 0, 200));
            } else if (i == selectedA) {
                StdDraw.setPenColor(new Color(0, 128, 255));
            } else if (i == selectedB) {
                StdDraw.setPenColor(new Color(255, 140, 0));
            } else {
                StdDraw.setPenColor(Color.RED);
            }

            StdDraw.filledCircle(p.x, p.y, dotRadius);

            // Şehir ismi noktanın üstünde
            StdDraw.setPenColor(Color.BLACK);
            StdDraw.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            StdDraw.text(p.x, p.y - labelOffsetY, cities.get(i).getName());
        }
    }

    private void drawButtons() {
        // "Make Unreachable" butonu
        Color btnColor = makeUnreachableMode
                ? new Color(160, 0, 200)
                : new Color(70, 70, 70);
        StdDraw.setPenColor(btnColor);
        StdDraw.filledRectangle(
                BTN_MAKE_X + BTN_MAKE_W / 2.0,
                BTN_Y + BTN_H / 2.0,
                BTN_MAKE_W / 2.0,
                BTN_H / 2.0);

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 12));
        StdDraw.text(BTN_MAKE_X + BTN_MAKE_W / 2.0, BTN_Y + BTN_H / 2.0,
                makeUnreachableMode ? "Make Unreachable ✓" : "Make Unreachable");

        // "Reset" butonu
        StdDraw.setPenColor(new Color(30, 120, 60));
        StdDraw.filledRectangle(
                BTN_RESET_X + BTN_RESET_W / 2.0,
                BTN_Y + BTN_H / 2.0,
                BTN_RESET_W / 2.0,
                BTN_H / 2.0);

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 12));
        StdDraw.text(BTN_RESET_X + BTN_RESET_W / 2.0, BTN_Y + BTN_H / 2.0, "Reset");
    }

    private void draw() {
        StdDraw.clear(new Color(245, 245, 245));

        drawTurkeyBorders();
        drawRoads();
        drawPath();
        drawCities();
        drawButtons();

        StdDraw.show();
    }

    // -----------------------------------------------------------------------
    // Ana döngü
    // -----------------------------------------------------------------------

    public void run() {
        StdDraw.setCanvasSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        StdDraw.setXscale(0, CANVAS_WIDTH);
        StdDraw.setYscale(CANVAS_HEIGHT, 0);
        StdDraw.enableDoubleBuffering();

        while (true) {
            handleMouseClick();
            draw();
            StdDraw.pause(20);
        }
    }

    // -----------------------------------------------------------------------
    // Fare etkileşimi
    // -----------------------------------------------------------------------

    private void handleMouseClick() {
        boolean mousePressed = StdDraw.isMousePressed();

        if (mousePressed && !previousMousePressed) {
            int mouseX = (int) Math.round(StdDraw.mouseX());
            int mouseY = (int) Math.round(StdDraw.mouseY());

            // Buton tıklaması mı?
            if (isInButton(mouseX, mouseY, BTN_MAKE_X, BTN_Y, BTN_MAKE_W, BTN_H)) {
                makeUnreachableMode = !makeUnreachableMode;
            } else if (isInButton(mouseX, mouseY, BTN_RESET_X, BTN_Y, BTN_RESET_W, BTN_H)) {
                resetAll();
            } else {
                // Şehir tıklaması
                int idx = findNearestCityIndex(mouseX, mouseY, CITY_PICK_RADIUS_PX);
                if (idx != -1) {
                    if (makeUnreachableMode) {
                        toggleUnreachable(idx);
                    } else {
                        if (selectedA == -1 || selectedB != -1) {
                            selectedA = idx;
                            selectedB = -1;
                            currentPath = Collections.emptyList();
                            pathUnreachable = false;
                        } else {
                            selectedB = idx;
                            List<Integer> path = dijkstraPath(selectedA, selectedB);
                            if (path == null) {
                                currentPath = Collections.emptyList();
                                pathUnreachable = true;
                            } else {
                                currentPath = path;
                                pathUnreachable = false;
                            }
                        }
                    }
                }
            }
        }

        previousMousePressed = mousePressed;
    }

    private boolean isInButton(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    // -----------------------------------------------------------------------
    // Unreachable / Reset
    // -----------------------------------------------------------------------

    private void toggleUnreachable(int idx) {
        if (unreachableCities.contains(idx)) {
            unreachableCities.remove(idx);
        } else {
            unreachableCities.add(idx);
        }
        // Yolları yeniden hesapla (unreachable şehirler hariç)
        roads = buildKnnRoads(K_NEAREST);
        // Mevcut rota geçersiz olabilir, sıfırla
        currentPath = Collections.emptyList();
        pathUnreachable = false;
        selectedA = -1;
        selectedB = -1;
    }

    private void resetAll() {
        unreachableCities.clear();
        roads = buildKnnRoads(K_NEAREST);
        selectedA = -1;
        selectedB = -1;
        currentPath = Collections.emptyList();
        pathUnreachable = false;
        makeUnreachableMode = false;
    }

    // -----------------------------------------------------------------------
    // Yol çizimi
    // -----------------------------------------------------------------------

    private void drawRoads() {
        if (roads.isEmpty()) return;

        StdDraw.setPenRadius(0.0015);
        StdDraw.setPenColor(new Color(190, 190, 190));

        for (int a = 0; a < roads.size(); a++) {
            if (unreachableCities.contains(a)) continue;
            Point p1 = cityToPoint(cities.get(a));
            for (int b : roads.get(a)) {
                if (b <= a) continue;
                if (unreachableCities.contains(b)) continue;
                Point p2 = cityToPoint(cities.get(b));
                StdDraw.line(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

    private void drawPath() {
        // Unreachable uyarısı
        if (pathUnreachable) {
            StdDraw.setPenColor(new Color(200, 0, 0));
            StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 22));
            StdDraw.text(CANVAS_WIDTH / 2.0, CANVAS_HEIGHT / 2.0, "UNREACHABLE");
            return;
        }

        if (currentPath == null || currentPath.size() < 2) return;

        StdDraw.setPenRadius(0.004);
        StdDraw.setPenColor(new Color(30, 30, 30));

        for (int i = 0; i < currentPath.size() - 1; i++) {
            Point p1 = cityToPoint(cities.get(currentPath.get(i)));
            Point p2 = cityToPoint(cities.get(currentPath.get(i + 1)));
            StdDraw.line(p1.x, p1.y, p2.x, p2.y);
        }

        StdDraw.setPenColor(Color.BLACK);
        StdDraw.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        StdDraw.textLeft(10, 20, "Rota:");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentPath.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(cities.get(currentPath.get(i)).getName());
        }
        StdDraw.textLeft(10, 40, sb.toString());

        double totalKm = pathDistanceKm(currentPath);
        StdDraw.textLeft(10, CANVAS_HEIGHT - 60, String.format("Toplam Mesafe: %.2f km", totalKm));
    }

    // -----------------------------------------------------------------------
    // Graf / Dijkstra
    // -----------------------------------------------------------------------

    /**
     * KNN graf oluşturur; unreachable şehirleri tamamen dışarıda bırakır.
     */
    private List<List<Integer>> buildKnnRoads(int k) {
        int n = cities.size();
        ArrayList<List<Integer>> graph = new ArrayList<>(n);
        for (int i = 0; i < n; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            if (unreachableCities.contains(i)) continue;

            double[] dist = new double[n];
            Integer[] idx = new Integer[n];

            for (int j = 0; j < n; j++) {
                idx[j] = j;
                dist[j] = (i == j || unreachableCities.contains(j))
                        ? Double.POSITIVE_INFINITY
                        : distanceKm(cities.get(i), cities.get(j));
            }

            Arrays.sort(idx, (a, b) -> Double.compare(dist[a], dist[b]));

            int added = 0;
            for (int t = 0; t < n && added < k; t++) {
                int j = idx[t];
                if (Double.isInfinite(dist[j])) continue;
                if (!graph.get(i).contains(j)) graph.get(i).add(j);
                if (!graph.get(j).contains(i)) graph.get(j).add(i);
                added++;
            }
        }

        return graph;
    }

    /**
     * Dijkstra — unreachable şehirlerden geçmez.
     * Hedefe ulaşılamazsa null döner.
     */
    private List<Integer> dijkstraPath(int startIdx, int targetIdx) {
        if (startIdx == targetIdx) return List.of(startIdx);

        // Başlangıç veya hedef unreachable ise direkt null
        if (unreachableCities.contains(startIdx) || unreachableCities.contains(targetIdx)) {
            return null;
        }

        int n = cities.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);

        record State(int v, double d) {}

        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(State::d));
        dist[startIdx] = 0.0;
        pq.add(new State(startIdx, 0.0));

        while (!pq.isEmpty()) {
            State cur = pq.poll();
            int v = cur.v();

            if (visited[v]) continue;
            visited[v] = true;

            if (v == targetIdx) break;

            for (int to : roads.get(v)) {
                if (unreachableCities.contains(to)) continue;
                double w = distanceKm(cities.get(v), cities.get(to));
                double nd = dist[v] + w;
                if (nd < dist[to]) {
                    dist[to] = nd;
                    prev[to] = v;
                    pq.add(new State(to, nd));
                }
            }
        }

        if (prev[targetIdx] == -1 && startIdx != targetIdx) {
            return null; // ulaşılamaz
        }

        ArrayList<Integer> path = new ArrayList<>();
        for (int v = targetIdx; v != -1; v = prev[v]) path.add(v);
        Collections.reverse(path);
        return path;
    }

    private double pathDistanceKm(List<Integer> path) {
        if (path == null || path.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += distanceKm(cities.get(path.get(i)), cities.get(path.get(i + 1)));
        }
        return total;
    }

    private double distanceKm(City a, City b) {
        double r = 6371.0;
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.lon() - a.lon());
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        return r * 2 * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    // -----------------------------------------------------------------------
    // Yardımcı
    // -----------------------------------------------------------------------

    private int findNearestCityIndex(int x, int y, int maxPixelDist) {
        int best = -1;
        double bestD2 = (double) maxPixelDist * maxPixelDist;
        for (int i = 0; i < cities.size(); i++) {
            Point p = cityToPoint(cities.get(i));
            double dx = p.x - x, dy = p.y - y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) { bestD2 = d2; best = i; }
        }
        return best;
    }

    private Point cityToPoint(City c) {
        return lonLatToPoint(c.lon(), c.lat());
    }

    private Point lonLatToPoint(double lon, double lat) {
        double cosLat = Math.cos(Math.toRadians(refLat));
        double projectedMinLon = minLon * cosLat;
        double projectedMaxLon = maxLon * cosLat;
        double projectedLon   = lon   * cosLat;
        double mapWidth  = projectedMaxLon - projectedMinLon;
        double mapHeight = maxLat - minLat;
        double scaleX = CANVAS_WIDTH  / mapWidth;
        double scaleY = CANVAS_HEIGHT / mapHeight;
        double scale  = Math.min(scaleX, scaleY);
        double drawnWidth  = mapWidth  * scale;
        double drawnHeight = mapHeight * scale;
        double offsetX = (CANVAS_WIDTH  - drawnWidth)  / 2.0;
        double offsetY = (CANVAS_HEIGHT - drawnHeight) / 2.0;
        int x = (int) Math.round(offsetX + (projectedLon - projectedMinLon) * scale);
        int y = (int) Math.round(offsetY + (maxLat - lat) * scale);
        return new Point(x, y);
    }

    private static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    // -----------------------------------------------------------------------
    // Türkiye sınırları
    // -----------------------------------------------------------------------

    private void drawTurkeyBorders() {
        if (turkeyBorders == null || turkeyBorders.isEmpty()) return;
        StdDraw.setPenRadius(0.002);
        StdDraw.setPenColor(new Color(80, 80, 80));
        for (List<double[]> ring : turkeyBorders) {
            if (ring.size() < 2) continue;
            for (int i = 0; i < ring.size() - 1; i++) {
                Point p1 = lonLatToPoint(ring.get(i)[0],     ring.get(i)[1]);
                Point p2 = lonLatToPoint(ring.get(i + 1)[0], ring.get(i + 1)[1]);
                StdDraw.line(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

    private List<List<double[]>> loadTurkeyBorders(String filePath) {
        try {
            String json = Files.readString(Path.of(filePath));
            return parseMultiPolygonOuterRings(json);
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<List<double[]>> parseMultiPolygonOuterRings(String json) {
        int coordinatesIndex = json.indexOf("\"coordinates\"");
        if (coordinatesIndex == -1) return Collections.emptyList();
        int start = json.indexOf('[', coordinatesIndex);
        if (start == -1) return Collections.emptyList();

        List<List<double[]>> rings = new ArrayList<>();
        List<double[]> currentRing = null;
        int depth = 0, pointStart = -1;

        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') {
                depth++;
                if (depth == 3) currentRing = new ArrayList<>();
                else if (depth == 4) pointStart = i;
            } else if (ch == ']') {
                if (depth == 4 && currentRing != null && pointStart != -1) {
                    double[] pt = parsePoint(json.substring(pointStart, i + 1));
                    if (pt != null) currentRing.add(pt);
                    pointStart = -1;
                } else if (depth == 3 && currentRing != null) {
                    if (!currentRing.isEmpty()) rings.add(currentRing);
                    currentRing = null;
                }
                depth--;
                if (depth == 0) break;
            }
        }
        return rings;
    }

    private double[] parsePoint(String pointText) {
        Matcher m = Pattern.compile(
                        "\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]")
                .matcher(pointText);
        if (m.find()) return new double[]{
                Double.parseDouble(m.group(1)),
                Double.parseDouble(m.group(2))};
        return null;
    }
}