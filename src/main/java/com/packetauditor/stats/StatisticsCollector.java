package com.packetauditor.stats;

import com.packetauditor.model.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects live traffic statistics (spec §4): totals, per-type frequency, a
 * chronological per-second timeline, and instantaneous rates. Lightweight and
 * lock-light so it can be fed from the networking thread; expensive formatting
 * happens only when a snapshot is requested.
 */
public final class StatisticsCollector {

    private static final int TIMELINE_SECONDS = 300; // 5 minutes of per-second buckets

    private final AtomicLong packetsIn = new AtomicLong();
    private final AtomicLong packetsOut = new AtomicLong();
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    private final Map<String, LongAdder> perTypeCounts = new ConcurrentHashMap<>();

    private final Object timelineLock = new Object();
    private final TrafficBucket[] ring = new TrafficBucket[TIMELINE_SECONDS];

    /** Record a packet. Safe to call from the networking thread. */
    public void record(Direction direction, String packetName, int sizeBytes) {
        int bytes = Math.max(0, sizeBytes);
        if (direction == Direction.INBOUND) {
            packetsIn.incrementAndGet();
            bytesIn.addAndGet(bytes);
        } else {
            packetsOut.incrementAndGet();
            bytesOut.addAndGet(bytes);
        }
        perTypeCounts.computeIfAbsent(packetName == null ? "Unknown" : packetName,
                k -> new LongAdder()).increment();

        long second = System.currentTimeMillis() / 1000L;
        synchronized (timelineLock) {
            TrafficBucket bucket = bucketFor(second);
            if (direction == Direction.INBOUND) {
                bucket.addIn(bytes);
            } else {
                bucket.addOut(bytes);
            }
        }
    }

    private TrafficBucket bucketFor(long second) {
        int idx = (int) (second % TIMELINE_SECONDS);
        TrafficBucket b = ring[idx];
        if (b == null || b.epochSecond() != second) {
            b = new TrafficBucket(second);
            ring[idx] = b;
        }
        return b;
    }

    public long totalPacketsIn()  { return packetsIn.get(); }
    public long totalPacketsOut() { return packetsOut.get(); }
    public long totalBytesIn()    { return bytesIn.get(); }
    public long totalBytesOut()   { return bytesOut.get(); }

    /** Per-type frequency, highest first. */
    public List<Map.Entry<String, Long>> topPacketTypes(int limit) {
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (Map.Entry<String, LongAdder> e : perTypeCounts.entrySet()) {
            list.add(Map.entry(e.getKey(), e.getValue().sum()));
        }
        list.sort(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed());
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    /** The most recent {@code seconds} of the timeline, oldest first. */
    public List<TrafficBucket> timeline(int seconds) {
        int n = Math.min(Math.max(1, seconds), TIMELINE_SECONDS);
        long nowSecond = System.currentTimeMillis() / 1000L;
        List<TrafficBucket> result = new ArrayList<>(n);
        synchronized (timelineLock) {
            for (long s = nowSecond - n + 1; s <= nowSecond; s++) {
                int idx = (int) ((s % TIMELINE_SECONDS + TIMELINE_SECONDS) % TIMELINE_SECONDS);
                TrafficBucket b = ring[idx];
                result.add(b != null && b.epochSecond() == s ? b : new TrafficBucket(s));
            }
        }
        return result;
    }

    /** Snapshot with instantaneous rates computed over the last {@code windowSeconds}. */
    public TrafficSnapshot snapshot(int windowSeconds) {
        int w = Math.max(1, windowSeconds);
        List<TrafficBucket> recent = timeline(w);
        long pIn = 0, pOut = 0, bIn = 0, bOut = 0;
        for (TrafficBucket b : recent) {
            pIn += b.packetsIn();
            pOut += b.packetsOut();
            bIn += b.bytesIn();
            bOut += b.bytesOut();
        }
        return new TrafficSnapshot(
                packetsIn.get(), packetsOut.get(), bytesIn.get(), bytesOut.get(),
                pIn / (double) w, pOut / (double) w, bIn / (double) w, bOut / (double) w);
    }

    public void reset() {
        packetsIn.set(0);
        packetsOut.set(0);
        bytesIn.set(0);
        bytesOut.set(0);
        perTypeCounts.clear();
        synchronized (timelineLock) {
            for (int i = 0; i < ring.length; i++) {
                ring[i] = null;
            }
        }
    }
}
