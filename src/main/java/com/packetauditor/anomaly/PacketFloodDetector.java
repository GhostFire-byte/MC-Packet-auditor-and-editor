package com.packetauditor.anomaly;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags an abnormally high rate of a single packet type (a flood/burst) within a
 * one-second sliding window, per packet type.
 */
public final class PacketFloodDetector extends AbstractDetector {

    private static final long WINDOW_MILLIS = 1_000;
    private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEmit = new ConcurrentHashMap<>();

    public PacketFloodDetector() {
        super("packet-flood");
    }

    @Override
    public Anomaly inspect(CapturedPacket packet, StatisticsCollector stats) {
        String name = packet.meta().packetName();
        long now = System.currentTimeMillis();
        Deque<Long> window = recent.computeIfAbsent(name, k -> new ArrayDeque<>());

        int count;
        synchronized (window) {
            window.addLast(now);
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            count = window.size();
        }

        // 500 per second at low sensitivity .. 100 per second at high sensitivity.
        double threshold = thresholdBetween(500, 100);
        if (count < threshold) {
            return null;
        }
        Long last = lastEmit.get(name);
        if (last != null && now - last < WINDOW_MILLIS) {
            return null;
        }
        lastEmit.put(name, now);
        Severity sev = count >= threshold * 2 ? Severity.CRITICAL : Severity.WARN;
        return anomaly(sev, "Packet flood: " + count + "x " + name + "/s",
                packet.meta().sequence(), name);
    }
}
