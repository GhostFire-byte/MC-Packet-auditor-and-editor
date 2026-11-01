package com.packetauditor.queue;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.pipeline.PacketTransport;
import com.packetauditor.util.AuditorLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The interception queue (spec §8): holds packets off the networking thread,
 * supports single/batch release, edit-before-release, discard, overflow handling
 * and timeout protection. Released packets are delivered through a
 * {@link PacketTransport}, which marshals them back onto the event loop (D4), so
 * ordering and thread-affinity are preserved and the loop is never blocked.
 */
public final class InterceptionQueue {

    private static final AuditorLog LOG = AuditorLog.of(InterceptionQueue.class);

    private final Object lock = new Object();
    private final Map<Long, HeldPacket> held = new LinkedHashMap<>(); // insertion order == FIFO
    private final AtomicLong idSeq = new AtomicLong(1);
    private final PacketTransport transport;

    private volatile int maxSize = 256;
    private volatile OverflowPolicy overflowPolicy = OverflowPolicy.RELEASE_OLDEST;
    private volatile long defaultTimeoutMillis = 30_000; // safety: never hold forever

    // Statistics
    private final AtomicLong totalHeld = new AtomicLong();
    private final AtomicLong totalReleased = new AtomicLong();
    private final AtomicLong totalDiscarded = new AtomicLong();
    private final AtomicLong totalTimedOut = new AtomicLong();
    private final AtomicLong totalOverflowed = new AtomicLong();

    private final ScheduledExecutorService timeoutSweeper;

    public InterceptionQueue(PacketTransport transport) {
        this.transport = transport;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "packet-auditor-queue-timeout");
            t.setDaemon(true);
            return t;
        };
        this.timeoutSweeper = Executors.newSingleThreadScheduledExecutor(tf);
        this.timeoutSweeper.scheduleWithFixedDelay(this::sweepTimeouts, 250, 250, TimeUnit.MILLISECONDS);
    }

    // ----- configuration -----
    public void setMaxSize(int maxSize)                  { this.maxSize = Math.max(1, maxSize); }
    public int maxSize()                                 { return maxSize; }
    public void setOverflowPolicy(OverflowPolicy policy) { this.overflowPolicy = policy == null ? OverflowPolicy.RELEASE_OLDEST : policy; }
    public OverflowPolicy overflowPolicy()               { return overflowPolicy; }
    public void setDefaultTimeoutMillis(long millis)     { this.defaultTimeoutMillis = Math.max(0, millis); }
    public long defaultTimeoutMillis()                   { return defaultTimeoutMillis; }

    /**
     * Hold a packet. Returns the hold id, or -1 if the packet was not held
     * (forwarded or dropped due to overflow policy).
     */
    public long hold(CapturedPacket packet, String reason, long timeoutMillis) {
        synchronized (lock) {
            if (held.size() >= maxSize) {
                if (!handleOverflow(packet)) {
                    return -1; // new packet was forwarded or dropped
                }
            }
            long id = idSeq.getAndIncrement();
            long now = System.currentTimeMillis();
            long effectiveTimeout = timeoutMillis > 0 ? timeoutMillis : defaultTimeoutMillis;
            long deadline = effectiveTimeout > 0 ? now + effectiveTimeout : 0;
            held.put(id, new HeldPacket(id, packet, now, deadline, reason));
            totalHeld.incrementAndGet();
            return id;
        }
    }

    public long hold(CapturedPacket packet, String reason) {
        return hold(packet, reason, defaultTimeoutMillis);
    }

    /** @return true if there is now room for the new packet; false if the new packet was handled. */
    private boolean handleOverflow(CapturedPacket newPacket) {
        totalOverflowed.incrementAndGet();
        switch (overflowPolicy) {
            case FORWARD_NEWEST:
                LOG.warn("Queue full (" + maxSize + "); forwarding newest packet "
                        + newPacket.meta().packetName());
                deliver(newPacket);
                return false;
            case DROP_NEWEST:
                LOG.warn("Queue full (" + maxSize + "); dropping newest packet "
                        + newPacket.meta().packetName());
                return false;
            case RELEASE_OLDEST:
            default:
                HeldPacket oldest = firstEntry();
                if (oldest != null) {
                    held.remove(oldest.id());
                    LOG.warn("Queue full (" + maxSize + "); releasing oldest held packet #"
                            + oldest.id());
                    deliver(oldest.packet());
                    totalReleased.incrementAndGet();
                }
                return true;
        }
    }

    private HeldPacket firstEntry() {
        for (HeldPacket hp : held.values()) {
            return hp;
        }
        return null;
    }

    /** Release a single held packet (forward it). */
    public boolean release(long id) {
        HeldPacket hp;
        synchronized (lock) {
            hp = held.remove(id);
        }
        if (hp == null) {
            return false;
        }
        deliver(hp.packet());
        totalReleased.incrementAndGet();
        return true;
    }

    /** Replace the held packet's contents with an edited version, then release it. */
    public boolean releaseEdited(long id, CapturedPacket edited) {
        synchronized (lock) {
            HeldPacket hp = held.get(id);
            if (hp == null) {
                return false;
            }
            hp.replacePacket(edited);
        }
        return release(id);
    }

    /** Edit a held packet in place without releasing it yet. */
    public boolean editHeld(long id, CapturedPacket edited) {
        synchronized (lock) {
            HeldPacket hp = held.get(id);
            if (hp == null) {
                return false;
            }
            hp.replacePacket(edited);
            return true;
        }
    }

    /** Discard a held packet (never forwarded). */
    public boolean discard(long id) {
        HeldPacket hp;
        synchronized (lock) {
            hp = held.remove(id);
        }
        if (hp == null) {
            return false;
        }
        totalDiscarded.incrementAndGet();
        LOG.debug("Discarded held packet #" + id);
        return true;
    }

    /** Release every held packet in FIFO order. */
    public int releaseAll() {
        List<HeldPacket> all;
        synchronized (lock) {
            all = new ArrayList<>(held.values());
            held.clear();
        }
        for (HeldPacket hp : all) {
            deliver(hp.packet());
            totalReleased.incrementAndGet();
        }
        return all.size();
    }

    /** Discard every held packet. */
    public int discardAll() {
        int n;
        synchronized (lock) {
            n = held.size();
            held.clear();
        }
        totalDiscarded.addAndGet(n);
        return n;
    }

    public int releaseBatch(Iterable<Long> ids) {
        int n = 0;
        for (Long id : ids) {
            if (release(id)) {
                n++;
            }
        }
        return n;
    }

    public int discardBatch(Iterable<Long> ids) {
        int n = 0;
        for (Long id : ids) {
            if (discard(id)) {
                n++;
            }
        }
        return n;
    }

    /** Immutable snapshot of currently held packets in FIFO order. */
    public List<HeldPacket> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(held.values());
        }
    }

    public int size() {
        synchronized (lock) {
            return held.size();
        }
    }

    public QueueStatus status() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long oldestAge = 0;
            HeldPacket first = firstEntry();
            if (first != null) {
                oldestAge = first.ageMillis(now);
            }
            return new QueueStatus(held.size(), maxSize, oldestAge,
                    totalHeld.get(), totalReleased.get(), totalDiscarded.get(),
                    totalTimedOut.get(), totalOverflowed.get());
        }
    }

    private void sweepTimeouts() {
        List<HeldPacket> expired = new ArrayList<>();
        synchronized (lock) {
            long now = System.currentTimeMillis();
            var it = held.values().iterator();
            while (it.hasNext()) {
                HeldPacket hp = it.next();
                if (hp.isTimedOut(now)) {
                    expired.add(hp);
                    it.remove();
                }
            }
        }
        for (HeldPacket hp : expired) {
            LOG.warn("Held packet #" + hp.id() + " (" + hp.packet().meta().packetName()
                    + ") timed out; auto-releasing to protect the connection.");
            deliver(hp.packet());
            totalTimedOut.incrementAndGet();
            totalReleased.incrementAndGet();
        }
    }

    private void deliver(CapturedPacket packet) {
        try {
            transport.deliver(packet);
        } catch (Throwable t) {
            LOG.error("Failed to deliver released packet " + packet.meta().packetName(), t);
        }
    }

    public void shutdown() {
        timeoutSweeper.shutdownNow();
    }
}
