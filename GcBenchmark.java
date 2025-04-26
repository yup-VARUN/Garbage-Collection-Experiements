import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @ Author: Varun Ahlawat
 * GcBenchmark: allocates two pools of objects in parallel threads:
 * - Two threads generate load:
 * 1) ShortLivedThread: churns many small objects (young‐gen pressure)
 * 2) LongLivedThread: grows a list of medium objects, pruning periodically (old‐gen pressure)
 *
 * After a warm-up period, each thread counts how many allocations it can perform
 * in a fixed measurement window.  We report:
 * - Number of short-lived allocs/sec
 * - Number of long-lived allocs/sec
 */
public class GcBenchmark {
    // Durations in ms
    private static final long WARMUP_MS  = 30_000;  // 30 seconds
    private static final long MEASURE_MS = 120_000; // 2 minutes
    private static final long TOTAL_DURATION_MS = WARMUP_MS + MEASURE_MS;

    /**
     * Thread allocating small, short-lived byte arrays.
     */
    static class ShortLivedThread extends Thread {
        volatile long measuredAllocs = 0; // Allocations during measurement phase

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            long endWarmup = startTime + WARMUP_MS;
            long endMeasure = startTime + TOTAL_DURATION_MS;
            Random rnd = new Random(42);
            long currentAllocs = 0;
            boolean inMeasurement = false;

            System.out.println("ShortLivedThread starting.");
            while (System.currentTimeMillis() < endMeasure) {
                long now = System.currentTimeMillis();

                // Check if measurement phase started
                if (!inMeasurement && now >= endWarmup) {
                    System.out.println("ShortLivedThread entering measurement phase.");
                    currentAllocs = 0; // Reset count for measurement
                    inMeasurement = true;
                }

                // Allocate a small array
                byte[] buf = new byte[rnd.nextInt(512)];

                if (inMeasurement) {
                    currentAllocs++;
                }
                // Add a small sleep to prevent pure CPU spinning if alloc is too fast
                // and to yield CPU occasionally, making it slightly more realistic
                // try { Thread.sleep(0, 1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            this.measuredAllocs = currentAllocs; // Store final measurement count
            System.out.println("ShortLivedThread finished.");
        }
    }

    /**
     * Thread allocating medium-lived byte arrays, keeping many in a list.
     */
    static class LongLivedThread extends Thread {
        volatile long measuredAllocs = 0; // Allocations during measurement phase

        // Constants for list management
        private static final int MAX_LIST_SIZE = 80_000; // Keep list size below this
        private static final int ITEMS_TO_PRUNE = 30_000; // Remove this many when pruning

        @Override
        public void run() {
            List<byte[]> list = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            long endWarmup = startTime + WARMUP_MS;
            long endMeasure = startTime + TOTAL_DURATION_MS;
            Random rnd = new Random(99);
            long currentAllocs = 0;
            boolean inMeasurement = false;

            System.out.println("LongLivedThread starting.");
            while (System.currentTimeMillis() < endMeasure) {
                long now = System.currentTimeMillis();

                 // Check if measurement phase started
                 if (!inMeasurement && now >= endWarmup) {
                    System.out.println("LongLivedThread entering measurement phase.");
                    currentAllocs = 0; // Reset count for measurement
                    inMeasurement = true;
                }

                // Allocate and add
                try {
                    list.add(new byte[rnd.nextInt(10_000) + 5_000]);
                    if (inMeasurement) {
                        currentAllocs++;
                    }
                } catch (OutOfMemoryError oom) {
                    System.err.printf("!!! Long-lived OOME at %d ms (List size: %,d)%n",
                                      (System.currentTimeMillis() - startTime), list.size());
                    // Record allocations made *before* OOM if it happened during measurement
                    if (inMeasurement) {
                        this.measuredAllocs = currentAllocs;
                    }
                    return; // Exit thread on OOM
                }

                // Pruning logic: remove from the beginning if size exceeds max
                if (list.size() > MAX_LIST_SIZE) {
                    try {
                        // System.out.printf("Pruning %d items from list (current size: %d)%n", ITEMS_TO_PRUNE, list.size());
                        list.subList(0, ITEMS_TO_PRUNE).clear(); // Efficiently remove first N items
                    } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                        // Should not happen with correct bounds, but good practice
                        System.err.println("Error during pruning: " + e.getMessage() + " List size: " + list.size());
                    }
                }
                 // Optional: Add a small sleep to prevent pure CPU spinning if alloc is too fast
                 // try { Thread.sleep(0, 1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            this.measuredAllocs = currentAllocs; // Store final measurement count
             System.out.println("LongLivedThread finished.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.printf("Starting benchmark: Warmup=%dms, Measure=%dms, Total=%dms%n",
                          WARMUP_MS, MEASURE_MS, TOTAL_DURATION_MS);
        System.out.printf("Heap settings: %s %s%n", System.getProperty("java.vm.initialHeapSize"), System.getProperty("java.vm.maxHeapSize")); // Check heap in use

        ShortLivedThread shortTh = new ShortLivedThread();
        LongLivedThread  longTh  = new LongLivedThread();

        long benchmarkStart = System.currentTimeMillis();
        longTh.start();
        shortTh.start();

        // Wait for both threads to complete their full duration
        shortTh.join();
        longTh.join();
        long benchmarkEnd = System.currentTimeMillis();

        System.out.println("Benchmark threads finished.");

        // Calculate and print throughput based on MEASUREMENT duration
        System.out.printf(
            "Short-lived allocations/sec: %,.0f%n" +
            "Long-lived allocations/sec:  %,.0f%n",
            (double)shortTh.measuredAllocs * 1000.0 / MEASURE_MS, // allocations per second
            (double)longTh.measuredAllocs  * 1000.0 / MEASURE_MS  // allocations per second
        );
        System.out.printf("Total benchmark runtime: %.2f seconds%n", (benchmarkEnd - benchmarkStart) / 1000.0);
    }
}