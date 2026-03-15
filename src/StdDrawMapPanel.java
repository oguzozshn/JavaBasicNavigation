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

    private final double minLat = 35, maxLat = 43;

    private int firstSelectedCity = -1;
    private int secondSelectedCity = -1;
    private List<Integer> currentPath = new ArrayList<>();
    private boolean pathUnreachable = false;

    private List<List<Integer>> roads;

    private final List<Integer> unreachableCities = new ArrayList<>();

    private static final int NEAREST_ROADS = 3;

    private static final int CANVAS_WIDTH  = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final int CITY_PICK_RADIUS_PX = 16;

    private static final int BTN_OFFSET_Y = CANVAS_HEIGHT - 45;
    private static final int BTN_HEIGHT = 30;
    private static final int BTN_MAKE_UNREACHABLE_OFFSET_x = 10;
    private static final int BTN_MAKE_UNREACHABLE_WIDTH = 160;
    private static final int BTN_RESET_OFFSET_X = 180;
    private static final int BTN_RESET_WIDTH = 80;

    private boolean makeUnreachableMode = false;
    private boolean previousMousePressed = false;

    private static final double CAR_SPEED_PX_PER_FRAME = 2.5;
    private boolean animationRunning = false;
    private int animationSegmentIndex = 0;
    private double carX = -1;
    private double carY = -1;
    private double carAngle = 0.0;

    public StdDrawMapPanel(List<City> cities) {
        this.cities = cities;
        this.roads = buildNearestRoads(NEAREST_ROADS);
        this.turkeyBorders = loadTurkeyBorders("src/Turkey.json");
    }

    public void run() {
        StdDraw.setCanvasSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        StdDraw.setXscale(0, CANVAS_WIDTH);
        StdDraw.setYscale(CANVAS_HEIGHT, 0);
        StdDraw.enableDoubleBuffering();

        while (true) {
            handleMouseClick();
            updateCarAnimation();
            draw();
            StdDraw.pause(20);
        }
    }

    private void handleMouseClick() {
        boolean mousePressed = StdDraw.isMousePressed();

        if (mousePressed && !previousMousePressed) {
            int mouseX = (int) Math.round(StdDraw.mouseX());
            int mouseY = (int) Math.round(StdDraw.mouseY());

            if (isInButton(mouseX, mouseY, BTN_MAKE_UNREACHABLE_OFFSET_x, BTN_OFFSET_Y, BTN_MAKE_UNREACHABLE_WIDTH, BTN_HEIGHT)) {
                makeUnreachableMode = !makeUnreachableMode;
            } else if (isInButton(mouseX, mouseY, BTN_RESET_OFFSET_X, BTN_OFFSET_Y, BTN_RESET_WIDTH, BTN_HEIGHT)) {
                resetAll();
            } else {
                int idx = findNearestCityIndex(mouseX, mouseY, CITY_PICK_RADIUS_PX);
                if (idx != -1) {
                    if (makeUnreachableMode) {
                        toggleUnreachable(idx);
                    } else {
                        if (firstSelectedCity == -1 || secondSelectedCity != -1) {
                            firstSelectedCity = idx;
                            secondSelectedCity = -1;
                            currentPath = new ArrayList<>();
                            pathUnreachable = false;
                            resetCarAnimation();
                        } else {
                            secondSelectedCity = idx;
                            List<Integer> path = dijkstraPath(firstSelectedCity, secondSelectedCity);
                            if (path == null) {
                                currentPath = new ArrayList<>();
                                pathUnreachable = true;
                                resetCarAnimation();
                            } else {
                                currentPath = path;
                                pathUnreachable = false;
                                startCarAnimation();
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

    private void toggleUnreachable(int idx) {
        if (unreachableCities.contains(idx)) {
            unreachableCities.remove(idx);
        } else {
            unreachableCities.add(idx);
        }
        roads = buildNearestRoads(NEAREST_ROADS);

        currentPath = Collections.emptyList();
        pathUnreachable = false;
        firstSelectedCity = -1;
        secondSelectedCity = -1;
        resetCarAnimation();
    }

    private void draw() {
        StdDraw.clear(new Color(245, 245, 245));

        drawTurkeyBorders();
        drawRoads();
        drawPath();
        drawCities();
        drawButtons();
        drawCar();

        StdDraw.show();
    }

    private void drawCar() {
        if (currentPath == null || currentPath.isEmpty() || carX < 0 || carY < 0) return;

        StdDraw.picture(carX, carY, "src/car.png", 24, 16);
    }

    private void drawTurkeyBorders() {
        if (turkeyBorders.isEmpty()) return;
        StdDraw.setPenRadius(0.002);
        StdDraw.setPenColor(new Color(75, 75, 75));
        for (List<double[]> borderPoints : turkeyBorders) {
            for (int i = 0; i < borderPoints.size() - 1; i++) {
                Point p1 = lonLatToPoint(borderPoints.get(i)[0], borderPoints.get(i)[1]);
                Point p2 = lonLatToPoint(borderPoints.get(i + 1)[0], borderPoints.get(i + 1)[1]);
                StdDraw.line(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

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

        if (pathUnreachable) {
            StdDraw.setPenColor(new Color(200, 0, 0));
            StdDraw.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            StdDraw.textLeft(10, 20, "Path:");
            StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 13));
            StdDraw.textLeft(10, 40, "Unreachable");
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
        StdDraw.textLeft(10, 20, "Route:");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentPath.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(cities.get(currentPath.get(i)).getName());
        }
        StdDraw.textLeft(10, 40, sb.toString());

        double totalKm = pathDistanceKm(currentPath);
        StdDraw.textLeft(10, CANVAS_HEIGHT - 60, String.format("Total Distance: %.2f km", totalKm));
    }

    private void drawCities() {
        double dotRadius = 4.0;
        double labelOffsetY = 10.0;

        for (int i = 0; i < cities.size(); i++) {
            Point p = cityToPoint(cities.get(i));

            if (unreachableCities.contains(i)) {
                StdDraw.setPenColor(new Color(160, 0, 200));
            } else if (i == firstSelectedCity) {
                StdDraw.setPenColor(new Color(0, 128, 255));
            } else if (i == secondSelectedCity) {
                StdDraw.setPenColor(new Color(255, 140, 0));
            } else {
                StdDraw.setPenColor(Color.RED);
            }

            StdDraw.filledCircle(p.x, p.y, dotRadius);

            StdDraw.setPenColor(Color.BLACK);
            StdDraw.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            StdDraw.text(p.x, p.y - labelOffsetY, cities.get(i).getName());
        }
    }

    private void drawButtons() {

        Color btnColor = makeUnreachableMode
                ? new Color(160, 0, 200)
                : new Color(70, 70, 70);
        StdDraw.setPenColor(btnColor);
        StdDraw.filledRectangle(
                BTN_MAKE_UNREACHABLE_OFFSET_x + BTN_MAKE_UNREACHABLE_WIDTH / 2.0,
                BTN_OFFSET_Y + BTN_HEIGHT / 2.0,
                BTN_MAKE_UNREACHABLE_WIDTH / 2.0,
                BTN_HEIGHT / 2.0);

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 12));
        StdDraw.text(BTN_MAKE_UNREACHABLE_OFFSET_x + BTN_MAKE_UNREACHABLE_WIDTH / 2.0, BTN_OFFSET_Y + BTN_HEIGHT / 2.0,
                makeUnreachableMode ? "Make Unreachable " : "Make Unreachable");

        StdDraw.setPenColor(new Color(30, 120, 60));
        StdDraw.filledRectangle(
                BTN_RESET_OFFSET_X + BTN_RESET_WIDTH / 2.0,
                BTN_OFFSET_Y + BTN_HEIGHT / 2.0,
                BTN_RESET_WIDTH / 2.0,
                BTN_HEIGHT / 2.0);

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Segoe UI", Font.BOLD, 12));
        StdDraw.text(BTN_RESET_OFFSET_X + BTN_RESET_WIDTH / 2.0, BTN_OFFSET_Y + BTN_HEIGHT / 2.0, "Reset");
    }

    private void resetAll() {
        unreachableCities.clear();
        roads = buildNearestRoads(NEAREST_ROADS);
        firstSelectedCity = -1;
        secondSelectedCity = -1;
        currentPath = Collections.emptyList();
        pathUnreachable = false;
        pathUnreachable = false;
        makeUnreachableMode = false;
        resetCarAnimation();
    }

    private void startCarAnimation() {
        if (currentPath == null || currentPath.isEmpty()) {
            resetCarAnimation();
            return;
        }

        Point start = cityToPoint(cities.get(currentPath.get(0)));
        carX = start.x;
        carY = start.y;
        animationSegmentIndex = 0;
        animationRunning = currentPath.size() > 1;

        if (currentPath.size() > 1) {
            Point next = cityToPoint(cities.get(currentPath.get(1)));
            carAngle = Math.atan2(next.y - carY, next.x - carX);
        }
    }

    private void resetCarAnimation() {
        animationRunning = false;
        animationSegmentIndex = 0;
        carX = -1;
        carY = -1;
        carAngle = 0.0;
    }

    private void updateCarAnimation() {
        if (!animationRunning || currentPath == null || currentPath.size() < 2) return;

        if (animationSegmentIndex >= currentPath.size() - 1) {
            animationRunning = false;
            return;
        }

        Point from = cityToPoint(cities.get(currentPath.get(animationSegmentIndex)));
        Point to = cityToPoint(cities.get(currentPath.get(animationSegmentIndex + 1)));

        double dx = to.x - carX;
        double dy = to.y - carY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance <= CAR_SPEED_PX_PER_FRAME) {
            carX = to.x;
            carY = to.y;
            animationSegmentIndex++;

            if (animationSegmentIndex >= currentPath.size() - 1) {
                animationRunning = false;
            } else {
                Point next = cityToPoint(cities.get(currentPath.get(animationSegmentIndex + 1)));
                carAngle = Math.atan2(next.y - carY, next.x - carX);
            }
            return;
        }

        carAngle = Math.atan2(to.y - carY, to.x - carX);
        carX += (dx / distance) * CAR_SPEED_PX_PER_FRAME;
        carY += (dy / distance) * CAR_SPEED_PX_PER_FRAME;
    }

    private List<List<Integer>> buildNearestRoads(int roadNumber) {
        int citySize = cities.size();
        ArrayList<List<Integer>> graph = new ArrayList<>(citySize);
        for (int i = 0; i < citySize; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < citySize; i++) {
            if (unreachableCities.contains(i)) continue;

            double[] dist = new double[citySize];
            Integer[] idx = new Integer[citySize];

            for (int j = 0; j < citySize; j++) {
                idx[j] = j;
                dist[j] = (i == j || unreachableCities.contains(j))
                        ? Double.POSITIVE_INFINITY
                        : distanceKm(cities.get(i), cities.get(j));
            }

            Arrays.sort(idx, (a, b) -> Double.compare(dist[a], dist[b]));

            int added = 0;
            for (int t = 0; t < citySize && added < roadNumber; t++) {
                int j = idx[t];
                if (Double.isInfinite(dist[j])) continue;
                if (!graph.get(i).contains(j)) graph.get(i).add(j);
                if (!graph.get(j).contains(i)) graph.get(j).add(i);
                added++;
            }
        }

        return graph;
    }

    private List<Integer> dijkstraPath(int startIdx, int targetIdx) {
        if (startIdx == targetIdx) return List.of(startIdx);

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
            return null;
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


    private Point cityToPoint(City c) {
        return lonLatToPoint(c.lon(), c.lat());
    }

    private Point lonLatToPoint(double lon, double lat) {
        double cosLat = Math.cos(Math.toRadians(39.0));
        double projectedMinLon = 25 * cosLat;
        double projectedMaxLon = 45 * cosLat;
        double projectedLon = lon * cosLat;

        double mapWidth = projectedMaxLon - projectedMinLon;
        double mapHeight = 43 - 35;

        double pixelPerUnitX = 800 / mapWidth;
        double pixelPerUnitY = 600 / mapHeight;
        double pixelPerUnit = Math.min(pixelPerUnitX, pixelPerUnitY);

        double offsetX = (800 - mapWidth * pixelPerUnit) / 2.0;
        double offsetY = (600 - mapHeight * pixelPerUnit) / 2.0;

        int x = (int) Math.round(offsetX + (projectedLon - projectedMinLon) * pixelPerUnit);
        int y = (int) Math.round(offsetY + (43 - lat) * pixelPerUnit);
        return new Point(x, y);
    }

    private static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
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