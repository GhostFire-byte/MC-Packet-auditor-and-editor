package com.packetauditor.stats;

/** Immutable snapshot of live traffic statistics (spec §4). */
public final class TrafficSnapshot {

    private final long packetsIn;
    private final long packetsOut;
    private final long bytesIn;
    private final long bytesOut;
    private final double packetsInPerSecond;
    private final double packetsOutPerSecond;
    private final double bytesInPerSecond;
    private final double bytesOutPerSecond;

    public TrafficSnapshot(long packetsIn, long packetsOut, long bytesIn, long bytesOut,
                           double packetsInPerSecond, double packetsOutPerSecond,
                           double bytesInPerSecond, double bytesOutPerSecond) {
        this.packetsIn = packetsIn;
        this.packetsOut = packetsOut;
        this.bytesIn = bytesIn;
        this.bytesOut = bytesOut;
        this.packetsInPerSecond = packetsInPerSecond;
        this.packetsOutPerSecond = packetsOutPerSecond;
        this.bytesInPerSecond = bytesInPerSecond;
        this.bytesOutPerSecond = bytesOutPerSecond;
    }

    public long packetsIn()               { return packetsIn; }
    public long packetsOut()              { return packetsOut; }
    public long totalPackets()            { return packetsIn + packetsOut; }
    public long bytesIn()                 { return bytesIn; }
    public long bytesOut()                { return bytesOut; }
    public long totalBytes()              { return bytesIn + bytesOut; }
    public double packetsInPerSecond()    { return packetsInPerSecond; }
    public double packetsOutPerSecond()   { return packetsOutPerSecond; }
    public double bytesInPerSecond()      { return bytesInPerSecond; }
    public double bytesOutPerSecond()     { return bytesOutPerSecond; }

    @Override
    public String toString() {
        return String.format("in=%d(%.1f/s) out=%d(%.1f/s) bytesIn=%d bytesOut=%d",
                packetsIn, packetsInPerSecond, packetsOut, packetsOutPerSecond, bytesIn, bytesOut);
    }
}
