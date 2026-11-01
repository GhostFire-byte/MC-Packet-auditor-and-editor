package com.packetauditor.netsim;

import com.packetauditor.model.Direction;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory bandwidth throttling and simulated packet loss primitives
 * (D13/D14). Off by default; hard caps and a maximum throttle delay bound the
 * effect. Loss is <i>simulated locally</i> — we simply don't forward our copy;
 * we never corrupt the socket.
 *
 * <p>Protection: whether a given packet may be affected is decided by the
 * pipeline/{@code ProtectionPolicy} before these primitives are consulted.
 * Essential packets bypass unless "Unlock ALL" is on (D14).</p>
 */
public final class NetworkSimulator {

    private final Random random = new Random();

    private volatile boolean enabled = false;

    // Simulated loss (probability 0..1), per direction.
    private volatile double inboundLossProbability = 0.0;
    private volatile double outboundLossProbability = 0.0;

    // Bandwidth throttle (bytes/second, 0 == unlimited), per direction.
    private volatile long inboundBytesPerSecond = 0;
    private volatile long outboundBytesPerSecond = 0;

    // Safety: never delay a single packet by more than this (timeout protection).
    private volatile long maxThrottleDelayMillis = 2_000;

    // Throttle state (next-available-time per direction).
    private final AtomicLong inboundNextAvailable = new AtomicLong();
    private final AtomicLong outboundNextAvailable = new AtomicLong();

    // Statistics
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong totalThrottled = new AtomicLong();
    private final AtomicLong totalThrottleDelayMillis = new AtomicLong();

    public boolean enabled()                { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setInboundLossProbability(double p)  { this.inboundLossProbability = clampProbability(p); }
    public void setOutboundLossProbability(double p) { this.outboundLossProbability = clampProbability(p); }
    public double inboundLossProbability()  { return inboundLossProbability; }
    public double outboundLossProbability() { return outboundLossProbability; }

    public void setInboundBytesPerSecond(long bps)  { this.inboundBytesPerSecond = Math.max(0, bps); }
    public void setOutboundBytesPerSecond(long bps) { this.outboundBytesPerSecond = Math.max(0, bps); }
    public long inboundBytesPerSecond()  { return inboundBytesPerSecond; }
    public long outboundBytesPerSecond() { return outboundBytesPerSecond; }

    public void setMaxThrottleDelayMillis(long v) { this.maxThrottleDelayMillis = Math.max(0, v); }
    public long maxThrottleDelayMillis()          { return maxThrottleDelayMillis; }

    /** Decide whether to simulate dropping this packet. */
    public boolean shouldDrop(Direction direction) {
        if (!enabled) {
            return false;
        }
        double p = direction == Direction.INBOUND ? inboundLossProbability : outboundLossProbability;
        if (p <= 0) {
            return false;
        }
        boolean drop = random.nextDouble() < p;
        if (drop) {
            totalDropped.incrementAndGet();
        }
        return drop;
    }

    /**
     * Compute how long to delay this packet to honour the throttle rate, clamped
     * to {@link #maxThrottleDelayMillis}. Returns 0 if throttling is off for the
     * direction.
     */
    public long throttleDelayMillis(Direction direction, int packetBytes) {
        if (!enabled) {
            return 0;
        }
        long bps = direction == Direction.INBOUND ? inboundBytesPerSecond : outboundBytesPerSecond;
        if (bps <= 0 || packetBytes <= 0) {
            return 0;
        }
        AtomicLong nextAvail = direction == Direction.INBOUND ? inboundNextAvailable : outboundNextAvailable;
        long now = System.currentTimeMillis();
        long transmitMillis = Math.max(1, (long) ((packetBytes / (double) bps) * 1000.0));

        long delay;
        synchronized (nextAvail) {
            long base = Math.max(now, nextAvail.get());
            delay = base - now;
            nextAvail.set(base + transmitMillis);
        }
        if (maxThrottleDelayMillis > 0) {
            delay = Math.min(delay, maxThrottleDelayMillis);
        }
        if (delay > 0) {
            totalThrottled.incrementAndGet();
            totalThrottleDelayMillis.addAndGet(delay);
        }
        return delay;
    }

    public NetSimStats stats() {
        return new NetSimStats(totalDropped.get(), totalThrottled.get(), totalThrottleDelayMillis.get());
    }

    private static double clampProbability(double p) {
        if (p < 0) return 0;
        if (p > 1) return 1;
        return p;
    }

    /** Immutable netsim statistics. */
    public static final class NetSimStats {
        private final long totalDropped;
        private final long totalThrottled;
        private final long totalThrottleDelayMillis;

        public NetSimStats(long totalDropped, long totalThrottled, long totalThrottleDelayMillis) {
            this.totalDropped = totalDropped;
            this.totalThrottled = totalThrottled;
            this.totalThrottleDelayMillis = totalThrottleDelayMillis;
        }

        public long totalDropped()             { return totalDropped; }
        public long totalThrottled()           { return totalThrottled; }
        public long totalThrottleDelayMillis() { return totalThrottleDelayMillis; }
    }
}
