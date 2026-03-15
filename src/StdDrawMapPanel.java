import java.awt.*;
import java.util.*;
import java.util.List;

public class StdDrawMapPanel {
    private final List<City> cities;

    private final double minLon = 26, maxLon = 45;
    private final double minLat = 36, maxLat = 43;

    private int selectedA = -1;
    private int selectedB = -1;
    private List<Integer> currentPath = Collections.emptyList();

    private final List<List<Integer>> roads;

    private static final int K_NEAREST = 3;

    // UI
    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final int CITY_PICK_RADIUS_PX = 16;
    private static final boolean SHOW_CITY_NAMES = true;

    private boolean previousMousePressed = false;

    public  StdDrawMapPanel(List<City> cities) {
        this.cities = cities;
        this.roads = buildKnnRoads(K_NEAREST);
    }

    private void drawCities() {
        double radius = 4.0;

        for (int i = 0; i < cities.size(); i++) {
            Point p = cityToPoint(cities.get(i));

            if (i == selectedA) {
                StdDraw.setPenColor(new Color(0, 128, 255));
            } else if (i == selectedB) {
                StdDraw.setPenColor(new Color(255, 140, 0));
            } else {
                StdDraw.setPenColor(Color.RED);
            }

            StdDraw.filledCircle(p.x, p.y, radius);
        }
    }

    private void draw() {
        StdDraw.clear(new Color(245, 245, 245));
//
          drawRoads();
          drawPath();
          drawCities();
//        drawLabels();

        StdDraw.show();
    }

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

    private Point cityToPoint(City c) {
        double lon = c.lon();
        double lat = c.lat();

        int x = (int) ((lon - minLon) / (maxLon - minLon) * CANVAS_WIDTH);
        int y = (int) ((maxLat - lat) / (maxLat - minLat) * CANVAS_HEIGHT);

        return new Point(x, y);
    }

    private static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private void drawRoads() {
        if (roads.isEmpty()) return;

        StdDraw.setPenRadius(0.0015);
        StdDraw.setPenColor(new Color(190, 190, 190));

        for (int a = 0; a < roads.size(); a++) {
            Point p1 = cityToPoint(cities.get(a));
            for (int b : roads.get(a)) {
                if (b <= a) continue;
                Point p2 = cityToPoint(cities.get(b));
                StdDraw.line(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

    private List<List<Integer>> buildKnnRoads(int k) {
        int n = cities.size();
        ArrayList<List<Integer>> graph = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            double[] dist = new double[n];
            Integer[] idx = new Integer[n];

            for (int j = 0; j < n; j++) {
                idx[j] = j;
                dist[j] = (i == j)
                        ? Double.POSITIVE_INFINITY
                        : distanceKm(cities.get(i), cities.get(j));
            }

            Arrays.sort(idx, (a, b) -> Double.compare(dist[a], dist[b]));

            int added = 0;
            for (int t = 0; t < n && added < k; t++) {
                int j = idx[t];
                double d = dist[j];

                if (Double.isInfinite(d)) continue;

                if (!graph.get(i).contains(j)) graph.get(i).add(j);
                if (!graph.get(j).contains(i)) graph.get(j).add(i);
                added++;
            }
        }

        return graph;
    }

    private double distanceKm(City a, City b) {
        double r = 6371.0;
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.lon() - a.lon());

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat
                + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(h)));

        return r * c;
    }

    private void drawPath() {
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
    }

    private void handleMouseClick() {
        boolean mousePressed = StdDraw.isMousePressed();

        if (mousePressed && !previousMousePressed) {
            int mouseX = (int) Math.round(StdDraw.mouseX());
            int mouseY = (int) Math.round(StdDraw.mouseY());

            int idx = findNearestCityIndex(mouseX, mouseY, CITY_PICK_RADIUS_PX);
            if (idx != -1) {
                if (selectedA == -1 || selectedB != -1) {
                    selectedA = idx;
                    selectedB = -1;
                    currentPath = Collections.emptyList();
                } else {
                    selectedB = idx;
                    currentPath = dijkstraPath(selectedA, selectedB);
                }
            }
        }

        previousMousePressed = mousePressed;
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

            State(int v, double d) {
                this.v = v;
                this.d = d;
            }
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
            return List.of(startIdx, targetIdx);
        }

        ArrayList<Integer> path = new ArrayList<>();
        for (int v = targetIdx; v != -1; v = prev[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }
}
