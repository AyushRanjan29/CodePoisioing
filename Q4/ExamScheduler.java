import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import javax.imageio.ImageIO;

public class ExamScheduler {

    static class Result {
        String name;
        int[] slotof;
        int slots;
        boolean optimal;
        double millis;

        Result(String name, int[] slotof, boolean optimal, double millis) {
            this.name = name;
            this.slotof = slotof;
            this.optimal = optimal;
            this.millis = millis;
            this.slots = countslots(slotof);
        }
    }

    static int countslots(int[] slotof) {
        int highest = -1;
        for (int slot : slotof) {
            highest = Math.max(highest, slot);
        }
        return highest + 1;
    }

    static boolean[][] randomgraph(int n, double density, Random random) {
        boolean[][] conflict = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < density) {
                    conflict[i][j] = true;
                    conflict[j][i] = true;
                }
            }
        }
        return conflict;
    }

    static int[] degrees(boolean[][] conflict, int n) {
        int[] degree = new int[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (conflict[i][j]) {
                    degree[i]++;
                }
            }
        }
        return degree;
    }

    static Integer[] orderbydegree(int[] degree, int n) {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> degree[b] - degree[a]);
        return order;
    }

    static Result rundsatur(boolean[][] conflict, int n, int cap) {
        long begin = System.nanoTime();
        int[] degree = degrees(conflict, n);
        int[] slotof = new int[n];
        Arrays.fill(slotof, -1);
        int[] slotsize = new int[n + 1];
        boolean[][] seen = new boolean[n][n + 1];
        int[] saturation = new int[n];
        int slotcount = 0;

        for (int step = 0; step < n; step++) {
            int pick = -1;
            for (int v = 0; v < n; v++) {
                if (slotof[v] != -1) {
                    continue;
                }
                if (pick == -1 || saturation[v] > saturation[pick]
                        || (saturation[v] == saturation[pick] && degree[v] > degree[pick])) {
                    pick = v;
                }
            }
            int chosen = 0;
            while (seen[pick][chosen] || slotsize[chosen] >= cap) {
                chosen++;
            }
            slotof[pick] = chosen;
            slotsize[chosen]++;
            if (chosen == slotcount) {
                slotcount++;
            }
            for (int u = 0; u < n; u++) {
                if (conflict[pick][u] && !seen[u][chosen]) {
                    seen[u][chosen] = true;
                    saturation[u]++;
                }
            }
        }
        double millis = (System.nanoTime() - begin) / 1e6;
        return new Result("DSatur", slotof, false, millis);
    }

    static Result runwelshpowell(boolean[][] conflict, int n, int cap) {
        long begin = System.nanoTime();
        int[] degree = degrees(conflict, n);
        Integer[] order = orderbydegree(degree, n);
        int[] slotof = new int[n];
        Arrays.fill(slotof, -1);
        int assigned = 0;
        int slot = 0;

        while (assigned < n) {
            List<Integer> members = new ArrayList<>();
            for (int v : order) {
                if (slotof[v] != -1 || members.size() >= cap) {
                    continue;
                }
                boolean free = true;
                for (int m : members) {
                    if (conflict[v][m]) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    members.add(v);
                    slotof[v] = slot;
                    assigned++;
                }
            }
            slot++;
        }
        double millis = (System.nanoTime() - begin) / 1e6;
        return new Result("Welsh-Powell", slotof, false, millis);
    }

    static int cliquelower(boolean[][] conflict, int n, Integer[] order) {
        int biggest = n > 0 ? 1 : 0;
        for (int start = 0; start < n; start++) {
            List<Integer> clique = new ArrayList<>();
            clique.add(start);
            for (int v : order) {
                if (v == start) {
                    continue;
                }
                boolean fits = true;
                for (int c : clique) {
                    if (!conflict[v][c]) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    clique.add(v);
                }
            }
            biggest = Math.max(biggest, clique.size());
        }
        return biggest;
    }

    static class exactsearch {
        int n;
        int cap;
        int[][] neighbours;
        Integer[] order;
        int[] current;
        int[] slotsize;
        int[][] clash;
        int bestslots;
        int[] bestassign;
        int lowerbound;
        long deadline;
        long nodes;
        boolean timedout;

        void search(int index, int used) {
            if (timedout || bestslots <= lowerbound) {
                return;
            }
            nodes++;
            if ((nodes & 1023) == 0 && System.nanoTime() > deadline) {
                timedout = true;
                return;
            }
            if (index == n) {
                bestslots = used;
                bestassign = current.clone();
                return;
            }
            int v = order[index];
            for (int s = 0; s <= Math.min(used, bestslots - 2); s++) {
                if (slotsize[s] >= cap || clash[v][s] != 0) {
                    continue;
                }
                slotsize[s]++;
                for (int u : neighbours[v]) {
                    clash[u][s]++;
                }
                current[v] = s;
                search(index + 1, Math.max(used, s + 1));
                current[v] = -1;
                for (int u : neighbours[v]) {
                    clash[u][s]--;
                }
                slotsize[s]--;
                if (timedout || bestslots <= lowerbound) {
                    return;
                }
            }
        }
    }

    static Result runexact(boolean[][] conflict, int n, int cap, Result seed, long limitms) {
        long begin = System.nanoTime();
        int[] degree = degrees(conflict, n);
        Integer[] order = orderbydegree(degree, n);

        int capacitybound = (n + cap - 1) / cap;
        int lower = Math.max(capacitybound, cliquelower(conflict, n, order));

        exactsearch engine = new exactsearch();
        engine.n = n;
        engine.cap = cap;
        engine.order = order;
        engine.neighbours = new int[n][];
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (conflict[i][j]) {
                    count++;
                }
            }
            engine.neighbours[i] = new int[count];
            int pos = 0;
            for (int j = 0; j < n; j++) {
                if (conflict[i][j]) {
                    engine.neighbours[i][pos++] = j;
                }
            }
        }
        engine.current = new int[n];
        Arrays.fill(engine.current, -1);
        engine.slotsize = new int[n + 1];
        engine.clash = new int[n][n + 1];
        engine.bestslots = seed.slots;
        engine.bestassign = seed.slotof.clone();
        engine.lowerbound = lower;
        engine.deadline = begin + limitms * 1_000_000L;

        engine.search(0, 0);

        boolean proven = !engine.timedout;
        double millis = (System.nanoTime() - begin) / 1e6;
        return new Result("Exact (B&B)", engine.bestassign, proven, millis);
    }

    static boolean isvalid(boolean[][] conflict, int n, int cap, int[] slotof) {
        int[] size = new int[n + 1];
        for (int i = 0; i < n; i++) {
            if (slotof[i] < 0) {
                return false;
            }
            size[slotof[i]]++;
            if (size[slotof[i]] > cap) {
                return false;
            }
            for (int j = i + 1; j < n; j++) {
                if (conflict[i][j] && slotof[i] == slotof[j]) {
                    return false;
                }
            }
        }
        return true;
    }

    static List<List<Integer>> groupbyslot(int[] slotof) {
        int total = countslots(slotof);
        List<List<Integer>> groups = new ArrayList<>();
        for (int s = 0; s < total; s++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < slotof.length; i++) {
            groups.get(slotof[i]).add(i + 1);
        }
        return groups;
    }

    static void writecsv(String path, int[] slotof, int cap) throws Exception {
        List<List<Integer>> groups = groupbyslot(slotof);
        try (PrintWriter writer = new PrintWriter(path)) {
            StringBuilder header = new StringBuilder("slot");
            for (int h = 1; h <= cap; h++) {
                header.append(",hall").append(h);
            }
            writer.println(header);
            for (int s = 0; s < groups.size(); s++) {
                StringBuilder line = new StringBuilder("slot" + (s + 1));
                for (int h = 0; h < cap; h++) {
                    line.append(",");
                    if (h < groups.get(s).size()) {
                        line.append(groups.get(s).get(h));
                    }
                }
                writer.println(line);
            }
        }
    }

    static Color slotcolor(int slot) {
        float hue = (float) ((slot * 0.618034) % 1.0);
        return Color.getHSBColor(hue, 0.55f, 0.95f);
    }

    static void centertext(Graphics2D g, String text, int cx, int cy) {
        FontMetrics metrics = g.getFontMetrics();
        int x = cx - metrics.stringWidth(text) / 2;
        int y = cy + (metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(text, x, y);
    }

    static Graphics2D preparecanvas(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    static void drawgraph(String path, boolean[][] conflict, int n, int[] slotof, String title) throws Exception {
        int size = 900;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preparecanvas(image);
        int radius = n > 60 ? 8 : n > 30 ? 12 : 18;
        int ring = size / 2 - 70;
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            px[i] = (int) (size / 2 + ring * Math.cos(angle));
            py[i] = (int) (size / 2 + ring * Math.sin(angle) + 15);
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(170, 170, 170, 140));
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (conflict[i][j]) {
                    g.drawLine(px[i], py[i], px[j], py[j]);
                }
            }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, radius - 4)));
        for (int i = 0; i < n; i++) {
            g.setColor(slotcolor(slotof[i]));
            g.fillOval(px[i] - radius, py[i] - radius, radius * 2, radius * 2);
            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(px[i] - radius, py[i] - radius, radius * 2, radius * 2);
            g.setColor(Color.BLACK);
            centertext(g, String.valueOf(i + 1), px[i], py[i]);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(Color.BLACK);
        centertext(g, title, size / 2, 28);
        g.dispose();
        ImageIO.write(image, "png", new File(path));
    }

    static void drawtimetable(String path, int[] slotof, int cap) throws Exception {
        List<List<Integer>> groups = groupbyslot(slotof);
        int cellwidth = 70;
        int cellheight = 46;
        int labelwidth = 100;
        int width = labelwidth + cap * cellwidth + 40;
        int height = (groups.size() + 1) * cellheight + 90;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preparecanvas(image);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(Color.BLACK);
        centertext(g, "Exam timetable", width / 2, 28);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        int top = 60;
        for (int h = 0; h < cap; h++) {
            centertext(g, "Hall " + (h + 1), 20 + labelwidth + h * cellwidth + cellwidth / 2, top + cellheight / 2);
        }
        for (int s = 0; s < groups.size(); s++) {
            int y = top + (s + 1) * cellheight;
            g.setColor(Color.BLACK);
            centertext(g, "Slot " + (s + 1), 20 + labelwidth / 2, y + cellheight / 2);
            for (int h = 0; h < cap; h++) {
                int x = 20 + labelwidth + h * cellwidth;
                boolean filled = h < groups.get(s).size();
                g.setColor(filled ? slotcolor(s) : new Color(238, 238, 238));
                g.fillRect(x, y, cellwidth, cellheight);
                g.setColor(Color.DARK_GRAY);
                g.setStroke(new BasicStroke(1.2f));
                g.drawRect(x, y, cellwidth, cellheight);
                if (filled) {
                    g.setColor(Color.BLACK);
                    centertext(g, String.valueOf(groups.get(s).get(h)), x + cellwidth / 2, y + cellheight / 2);
                }
            }
        }
        g.dispose();
        ImageIO.write(image, "png", new File(path));
    }

    static void drawchart(Graphics2D g, int ox, int oy, int w, int h, String title,
                          int[] xs, double[][] values, String[] names, boolean logscale) {
        int left = ox + 75;
        int top = oy + 60;
        int plotwidth = w - 110;
        int plotheight = h - 130;
        Color[] palette = {new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44)};

        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (double[] row : values) {
            for (double v : row) {
                double t = logscale ? Math.log10(Math.max(v, 0.001)) : v;
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
            }
        }
        if (logscale) {
            lo = Math.floor(lo);
            hi = Math.ceil(hi);
        } else {
            lo = 0;
            hi = Math.ceil((hi + 1) / 5.0) * 5;
        }
        if (hi <= lo) {
            hi = lo + 1;
        }
        int tickcount = logscale ? (int) (hi - lo) : 5;

        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(Color.BLACK);
        centertext(g, title, ox + w / 2, oy + 25);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int k = 0; k <= tickcount; k++) {
            double value = lo + (hi - lo) * k / tickcount;
            int y = top + plotheight - plotheight * k / tickcount;
            g.setColor(new Color(225, 225, 225));
            g.drawLine(left, y, left + plotwidth, y);
            g.setColor(Color.BLACK);
            String label = logscale ? String.format("%.3g", Math.pow(10, value)) : String.format("%.0f", value);
            g.drawString(label, left - 55, y + 4);
        }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(left, top, left, top + plotheight);
        g.drawLine(left, top + plotheight, left + plotwidth, top + plotheight);
        centertext(g, "Number of courses (N)", left + plotwidth / 2, top + plotheight + 42);

        int[] px = new int[xs.length];
        for (int i = 0; i < xs.length; i++) {
            px[i] = left + (xs.length == 1 ? plotwidth / 2 : plotwidth * i / (xs.length - 1));
            centertext(g, String.valueOf(xs[i]), px[i], top + plotheight + 18);
        }
        for (int a = 0; a < values.length; a++) {
            g.setColor(palette[a % palette.length]);
            g.setStroke(new BasicStroke(2.4f));
            int lastx = 0;
            int lasty = 0;
            for (int i = 0; i < xs.length; i++) {
                double t = logscale ? Math.log10(Math.max(values[a][i], 0.001)) : values[a][i];
                int y = (int) (top + plotheight - plotheight * (t - lo) / (hi - lo));
                if (i > 0) {
                    g.drawLine(lastx, lasty, px[i], y);
                }
                g.fillOval(px[i] - 4, y - 4, 8, 8);
                lastx = px[i];
                lasty = y;
            }
            int legendy = top + 14 + a * 20;
            g.fillRect(left + 12, legendy - 8, 14, 10);
            g.setColor(Color.BLACK);
            g.drawString(names[a], left + 32, legendy + 2);
        }
    }

    static void drawcomparison(String path, int[] sizes, double[][] slotvalues, double[][] timevalues,
                               String[] names) throws Exception {
        int width = 1300;
        int height = 520;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preparecanvas(image);
        drawchart(g, 0, 0, width / 2, height, "Slots used vs N", sizes, slotvalues, names, false);
        drawchart(g, width / 2, 0, width / 2, height, "Running time (ms, log scale) vs N", sizes, timevalues, names, true);
        g.dispose();
        ImageIO.write(image, "png", new File(path));
    }

    static void printresult(Result result, boolean exactmethod) {
        String status;
        if (exactmethod) {
            status = result.optimal ? "proven optimal" : "not proven (time limit reached)";
        } else {
            status = "heuristic, optimality not guaranteed";
        }
        System.out.printf("%-14s slots = %-3d time = %9.3f ms  [%s]%n", result.name, result.slots, result.millis, status);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Scanner input = new Scanner(System.in);
        long exactlimit = 5000;

        System.out.println("Choose input option (1 = read conflicts, 2 = random graph):");
        int option = input.nextInt();
        int n;
        int cap;
        boolean[][] conflict;
        double density;

        if (option == 1) {
            System.out.println("Enter: N M R, then M lines of: u v (1-indexed)");
            n = input.nextInt();
            int edgecount = input.nextInt();
            cap = input.nextInt();
            conflict = new boolean[n][n];
            for (int e = 0; e < edgecount; e++) {
                int u = input.nextInt() - 1;
                int v = input.nextInt() - 1;
                if (u != v && u >= 0 && v >= 0 && u < n && v < n) {
                    conflict[u][v] = true;
                    conflict[v][u] = true;
                }
            }
            int realedges = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (conflict[i][j]) {
                        realedges++;
                    }
                }
            }
            density = n > 1 ? realedges / (n * (n - 1) / 2.0) : 0;
        } else {
            System.out.println("Enter: N R");
            n = input.nextInt();
            cap = input.nextInt();
            System.out.println("Enter edge density (0 to 1):");
            density = input.nextDouble();
            conflict = randomgraph(n, density, new Random(42));
        }

        if (n < 1 || cap < 1) {
            System.out.println("N and R must be at least 1");
            return;
        }

        Result dsatur = rundsatur(conflict, n, cap);
        Result welsh = runwelshpowell(conflict, n, cap);
        Result seed = dsatur.slots <= welsh.slots ? dsatur : welsh;
        Result exact = runexact(conflict, n, cap, seed, exactlimit);

        System.out.println();
        System.out.println("Output 1: minimum slots per algorithm");
        printresult(dsatur, false);
        printresult(welsh, false);
        printresult(exact, true);
        System.out.println("All timetables valid: "
                + (isvalid(conflict, n, cap, dsatur.slotof)
                && isvalid(conflict, n, cap, welsh.slotof)
                && isvalid(conflict, n, cap, exact.slotof)));

        System.out.println();
        System.out.println("Output 2: timetable from " + exact.name + " (" + exact.slots + " slots)");
        List<List<Integer>> groups = groupbyslot(exact.slotof);
        for (int s = 0; s < groups.size(); s++) {
            StringBuilder line = new StringBuilder("Slot " + (s + 1) + ": ");
            for (int k = 0; k < groups.get(s).size(); k++) {
                if (k > 0) {
                    line.append(", ");
                }
                line.append(groups.get(s).get(k));
            }
            System.out.println(line);
        }

        writecsv("timetable.csv", exact.slotof, cap);
        drawgraph("conflict_graph.png", conflict, n, exact.slotof,
                "Conflict graph coloured by slot (" + exact.slots + " slots)");
        drawtimetable("timetable_grid.png", exact.slotof, cap);
        System.out.println();
        System.out.println("Output 3: timetable.csv written");

        System.out.println();
        System.out.println("Running benchmark for the comparison chart...");
        int[] sizes = {10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        double[][] slotvalues = new double[3][sizes.length];
        double[][] timevalues = new double[3][sizes.length];
        System.out.printf("%-5s %-12s %-14s %-14s%n", "N", "dsatur", "welsh-powell", "exact");
        for (int k = 0; k < sizes.length; k++) {
            boolean[][] sample = randomgraph(sizes[k], density, new Random(1000 + sizes[k]));
            Result a = rundsatur(sample, sizes[k], cap);
            Result b = runwelshpowell(sample, sizes[k], cap);
            Result c = runexact(sample, sizes[k], cap, a.slots <= b.slots ? a : b, 2000);
            slotvalues[0][k] = a.slots;
            slotvalues[1][k] = b.slots;
            slotvalues[2][k] = c.slots;
            timevalues[0][k] = a.millis;
            timevalues[1][k] = b.millis;
            timevalues[2][k] = c.millis;
            System.out.printf("%-5d %-12d %-14d %-4d%s%n", sizes[k], a.slots, b.slots, c.slots,
                    c.optimal ? " (optimal)" : " (time limit)");
        }
        String[] names = {"DSatur", "Welsh-Powell", "Exact (B&B)"};
        drawcomparison("comparison_chart.png", sizes, slotvalues, timevalues, names);
        System.out.println("Images written: conflict_graph.png, timetable_grid.png, comparison_chart.png");
    }
}
