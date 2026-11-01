package com.packetauditor.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory session packet history (spec §12): a bounded ring with automatic
 * rolling FIFO truncation to prevent unbounded RAM/disk growth. A generous
 * default cap is used; old records are evicted oldest-first when exceeded.
 * Searchable via {@link HistoryFilter}. Optional disk persistence is handled
 * separately by the SQLite store.
 */
public final class PacketHistory {

    private final Deque<PacketHistoryEntry> entries = new ArrayDeque<>();
    private final Object lock = new Object();

    private volatile int maxEntries = 50_000;      // generous default
    private volatile long maxBytes = 200L * 1024 * 1024; // ~200 MB soft cap
    private final AtomicLong estimatedBytes = new AtomicLong();
    private final AtomicLong totalRecorded = new AtomicLong();
    private final AtomicLong totalEvicted = new AtomicLong();

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        trim();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = Math.max(1, maxBytes);
        trim();
    }

    public long maxBytes() {
        return maxBytes;
    }

    public void add(PacketHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (lock) {
            entries.addLast(entry);
            estimatedBytes.addAndGet(entry.estimatedBytes());
            totalRecorded.incrementAndGet();
            trimLocked();
        }
    }

    private void trim() {
        synchronized (lock) {
            trimLocked();
        }
    }

    private void trimLocked() {
        while (entries.size() > maxEntries || estimatedBytes.get() > maxBytes) {
            PacketHistoryEntry removed = entries.pollFirst();
            if (removed == null) {
                break;
            }
            estimatedBytes.addAndGet(-removed.estimatedBytes());
            totalEvicted.incrementAndGet();
        }
    }

    public List<PacketHistoryEntry> recent(int limit) {
        synchronized (lock) {
            int n = Math.min(limit <= 0 ? entries.size() : limit, entries.size());
            List<PacketHistoryEntry> out = new ArrayList<>(n);
            var it = entries.descendingIterator();
            while (it.hasNext() && out.size() < n) {
                out.add(it.next());
            }
            return out;
        }
    }

    public List<PacketHistoryEntry> search(HistoryFilter filter, int limit) {
        List<PacketHistoryEntry> out = new ArrayList<>();
        synchronized (lock) {
            var it = entries.descendingIterator();
            while (it.hasNext()) {
                PacketHistoryEntry e = it.next();
                if (filter == null || filter.matches(e)) {
                    out.add(e);
                    if (limit > 0 && out.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    public List<PacketHistoryEntry> all() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public long estimatedBytes() {
        return estimatedBytes.get();
    }

    public long totalRecorded() {
        return totalRecorded.get();
    }

    public long totalEvicted() {
        return totalEvicted.get();
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            estimatedBytes.set(0);
        }
    }
}
