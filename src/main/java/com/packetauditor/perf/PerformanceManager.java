package com.packetauditor.perf;

import com.packetauditor.util.AuditorLog;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive performance/resource management (spec §15): a bounded background
 * worker for all expensive work (decoding, anomaly analysis, disk I/O), a
 * configurable GUI refresh rate, resource monitors, and an automatic throttle
 * for non-essential analysis under load. Nothing here ever runs on the
 * networking or main thread.
 */
public final class PerformanceManager {

    private static final AuditorLog LOG = AuditorLog.of(PerformanceManager.class);

    private final ThreadPoolExecutor background;
    private final int queueCapacity;
    private final AtomicInteger dropped = new AtomicInteger();

    private volatile int guiFps = 60;           // D11 default, configurable
    private volatile int analysisBacklogThreshold = 2_000;

    public PerformanceManager() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() / 2), 8_192);
    }

    public PerformanceManager(int threads, int queueCapacity) {
        this.queueCapacity = queueCapacity;
        this.background = new ThreadPoolExecutor(
                threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "packet-auditor-worker");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                },
                (r, exec) -> {
                    // Overflow: drop non-essential background work rather than block.
                    dropped.incrementAndGet();
                });
    }

    /** Submit expensive work to the background pool. */
    public void submit(Runnable task) {
        try {
            background.execute(task);
        } catch (Throwable t) {
            dropped.incrementAndGet();
        }
    }

    public int guiFps() {
        return guiFps;
    }

    public void setGuiFps(int fps) {
        this.guiFps = Math.max(5, Math.min(240, fps));
    }

    /** Milliseconds between GUI frames at the configured refresh rate. */
    public long frameIntervalMillis() {
        return 1000L / guiFps;
    }

    /**
     * True when non-essential analysis (e.g. anomaly detection) should be skipped
     * because the background backlog is high — automatic throttling (§15).
     */
    public boolean shouldThrottleAnalysis() {
        return background.getQueue().size() > analysisBacklogThreshold;
    }

    public void setAnalysisBacklogThreshold(int threshold) {
        this.analysisBacklogThreshold = Math.max(1, threshold);
    }

    public ResourceSnapshot sampleResources(int queuePressure) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        return new ResourceSnapshot(used, max, background.getQueue().size(), queueCapacity,
                queuePressure, dropped.get());
    }

    public void shutdown() {
        background.shutdown();
        try {
            if (!background.awaitTermination(2, TimeUnit.SECONDS)) {
                background.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            background.shutdownNow();
        }
    }

    /** Immutable resource snapshot for the dashboard monitors (§15). */
    public static final class ResourceSnapshot {
        private final long usedMemoryBytes;
        private final long maxMemoryBytes;
        private final int backgroundBacklog;
        private final int backgroundCapacity;
        private final int queuePressure;
        private final int droppedTasks;

        public ResourceSnapshot(long usedMemoryBytes, long maxMemoryBytes, int backgroundBacklog,
                                int backgroundCapacity, int queuePressure, int droppedTasks) {
            this.usedMemoryBytes = usedMemoryBytes;
            this.maxMemoryBytes = maxMemoryBytes;
            this.backgroundBacklog = backgroundBacklog;
            this.backgroundCapacity = backgroundCapacity;
            this.queuePressure = queuePressure;
            this.droppedTasks = droppedTasks;
        }

        public long usedMemoryBytes()   { return usedMemoryBytes; }
        public long maxMemoryBytes()    { return maxMemoryBytes; }
        public int backgroundBacklog()  { return backgroundBacklog; }
        public int backgroundCapacity() { return backgroundCapacity; }
        public int queuePressure()      { return queuePressure; }
        public int droppedTasks()       { return droppedTasks; }

        public double memoryUsedRatio() {
            return maxMemoryBytes <= 0 ? 0 : (double) usedMemoryBytes / maxMemoryBytes;
        }
    }
}
