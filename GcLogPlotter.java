import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.Color;
import java.awt.Font;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;   // <<<<<< ADD THIS!!!
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.category.StandardBarPainter;


public class GcLogPlotter {
    private static final Pattern PAUSE_PATTERN =
        Pattern.compile("Pause.*? ([0-9]+\\.[0-9]+)ms");

    public static void main(String[] args) throws Exception {
        String[] collectors = { "parallel", "g1" };
        double[] throughputs = new double[collectors.length];
        double[] avgPauses = new double[collectors.length];
        double[] maxPauses = new double[collectors.length];

        for (int i = 0; i < collectors.length; i++) {
            String name = collectors[i];
            throughputs[i] = parseThroughput("gc-logs/result-" + name + ".txt");
            List<Double> pauses = parsePauses("gc-logs/gc-" + name + ".log");
            avgPauses[i] = pauses.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            maxPauses[i] = pauses.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }

        createCharts(collectors, throughputs, avgPauses, maxPauses);
        System.out.println("Charts saved: throughput.png, pause_times.png");
    }

    private static double parseThroughput(String resultFile) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(resultFile));
        for (String line : lines) {
            if (line.toLowerCase().contains("allocations/sec")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String num = parts[1].trim().replaceAll(",", "");
                    try {
                        return Double.parseDouble(num);
                    } catch (NumberFormatException e) {
                        // Ignore and fallback
                    }
                }
            }
        }
        // fallback: first parsable number
        for (String line : lines) {
            try {
                return Double.parseDouble(line.trim().replaceAll(",", ""));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static List<Double> parsePauses(String logFile) throws IOException {
        List<Double> pauses = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(Path.of(logFile))) {
            String l;
            while ((l = r.readLine()) != null) {
                Matcher m = PAUSE_PATTERN.matcher(l);
                if (m.find()) {
                    pauses.add(Double.parseDouble(m.group(1)));
                }
            }
        }
        return pauses;
    }

    private static void createCharts(String[] collectors, double[] throughputs,
                                     double[] avgPauses, double[] maxPauses) throws IOException {
        DefaultCategoryDataset throughputData = new DefaultCategoryDataset();
        DefaultCategoryDataset pauseData = new DefaultCategoryDataset();

        for (int i = 0; i < collectors.length; i++) {
            throughputData.addValue(throughputs[i], "Throughput (allocs/sec)", collectors[i]);
            pauseData.addValue(avgPauses[i], "Avg Pause (ms)", collectors[i]);
            pauseData.addValue(maxPauses[i], "Max Pause (ms)", collectors[i]);
        }

        JFreeChart throughputChart = ChartFactory.createBarChart(
            "GC Throughput", "Collector", "Allocations/sec",
            throughputData, PlotOrientation.VERTICAL, true, true, false);

        JFreeChart pauseChart = ChartFactory.createBarChart(
            "GC Pauses", "Collector", "Milliseconds",
            pauseData, PlotOrientation.VERTICAL, true, true, false);

        beautifyChart(throughputChart, new Color(79, 129, 189));
        beautifyChart(pauseChart, new Color(192, 80, 77));

        ChartUtils.saveChartAsPNG(new File("throughput.png"), throughputChart, 600, 400);
        ChartUtils.saveChartAsPNG(new File("pause_times.png"), pauseChart, 600, 400);
    }

    private static void beautifyChart(JFreeChart chart, Color seriesColor) {
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setSeriesPaint(0, seriesColor);

        Font labelFont = new Font("SansSerif", Font.PLAIN, 14);
        plot.getDomainAxis().setTickLabelFont(labelFont);
        plot.getRangeAxis().setTickLabelFont(labelFont);
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 16));

        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(labelFont);
        }
    }
}
