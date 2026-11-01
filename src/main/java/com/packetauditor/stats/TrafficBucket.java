package com.packetauditor.stats;

/** One second of aggregated traffic, used for the chronological timeline (spec §4). */
public final class TrafficBucket {

    private final long epochSecond;
    private long packetsIn;
    private long packetsOut;
    private long bytesIn;
    private long bytesOut;

    public TrafficBucket(long epochSecond) {
        this.epochSecond = epochSecond;
    }

    public long epochSecond() { return epochSecond; }
    public long packetsIn()   { return packetsIn; }
    public long packetsOut()  { return packetsOut; }
    public long bytesIn()     { return bytesIn; }
    public long bytesOut()    { return bytesOut; }

    void addIn(long bytes)  { packetsIn++;  bytesIn += Math.max(0, bytes); }
    void addOut(long bytes) { packetsOut++; bytesOut += Math.max(0, bytes); }

    public long totalPackets() { return packetsIn + packetsOut; }
    public long totalBytes()   { return bytesIn + bytesOut; }
}
