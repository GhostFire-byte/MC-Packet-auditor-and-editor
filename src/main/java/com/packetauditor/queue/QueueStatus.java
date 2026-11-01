package com.packetauditor.queue;

/** Immutable snapshot of interception-queue status (spec §8 status indicators). */
public final class QueueStatus {

    private final int currentSize;
    private final int capacity;
    private final long oldestAgeMillis;
    private final long totalHeld;
    private final long totalReleased;
    private final long totalDiscarded;
    private final long totalTimedOut;
    private final long totalOverflowed;

    public QueueStatus(int currentSize, int capacity, long oldestAgeMillis, long totalHeld,
                       long totalReleased, long totalDiscarded, long totalTimedOut,
                       long totalOverflowed) {
        this.currentSize = currentSize;
        this.capacity = capacity;
        this.oldestAgeMillis = oldestAgeMillis;
        this.totalHeld = totalHeld;
        this.totalReleased = totalReleased;
        this.totalDiscarded = totalDiscarded;
        this.totalTimedOut = totalTimedOut;
        this.totalOverflowed = totalOverflowed;
    }

    public int currentSize()        { return currentSize; }
    public int capacity()           { return capacity; }
    public long oldestAgeMillis()   { return oldestAgeMillis; }
    public long totalHeld()         { return totalHeld; }
    public long totalReleased()     { return totalReleased; }
    public long totalDiscarded()    { return totalDiscarded; }
    public long totalTimedOut()     { return totalTimedOut; }
    public long totalOverflowed()   { return totalOverflowed; }

    public double fillRatio() {
        return capacity <= 0 ? 0 : (double) currentSize / capacity;
    }

    public boolean isFull() {
        return currentSize >= capacity;
    }

    @Override
    public String toString() {
        return "Queue " + currentSize + "/" + capacity
                + " (held=" + totalHeld + " released=" + totalReleased
                + " discarded=" + totalDiscarded + " timedOut=" + totalTimedOut + ")";
    }
}
