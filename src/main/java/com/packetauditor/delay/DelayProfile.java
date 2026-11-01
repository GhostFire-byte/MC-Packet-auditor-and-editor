package com.packetauditor.delay;

import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.Direction;

import java.util.Random;
import java.util.UUID;

/**
 * A reusable delay profile (spec §9): fixed or randomised delay, applied to
 * inbound and/or outbound traffic, optionally scoped to specific packet types,
 * with a per-profile maximum-delay cap (timeout protection).
 */
public final class DelayProfile {

    public enum Mode { FIXED, RANDOM }

    private final String id;
    private String name;
    private boolean enabled;
    private Mode mode;
    private long fixedMillis;
    private long minMillis;
    private long maxMillis;
    private boolean appliesToInbound;
    private boolean appliesToOutbound;
    private long maxDelayMillisCap;     // hard upper bound for this profile
    private MatchSpec packetFilter;     // null == applies to all packet types

    public DelayProfile(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name == null ? "Delay" : name;
        this.enabled = false;
        this.mode = Mode.FIXED;
        this.fixedMillis = 100;
        this.minMillis = 50;
        this.maxMillis = 250;
        this.appliesToInbound = true;
        this.appliesToOutbound = true;
        this.maxDelayMillisCap = 5_000;
    }

    public String id()                 { return id; }
    public String name()               { return name; }
    public boolean enabled()           { return enabled; }
    public Mode mode()                 { return mode; }
    public long fixedMillis()          { return fixedMillis; }
    public long minMillis()            { return minMillis; }
    public long maxMillis()            { return maxMillis; }
    public boolean appliesToInbound()  { return appliesToInbound; }
    public boolean appliesToOutbound() { return appliesToOutbound; }
    public long maxDelayMillisCap()    { return maxDelayMillisCap; }
    public MatchSpec packetFilter()    { return packetFilter; }

    public DelayProfile setName(String v)              { this.name = v; return this; }
    public DelayProfile setEnabled(boolean v)          { this.enabled = v; return this; }
    public DelayProfile setMode(Mode v)                { this.mode = v; return this; }
    public DelayProfile setFixedMillis(long v)         { this.fixedMillis = Math.max(0, v); return this; }
    public DelayProfile setMinMillis(long v)           { this.minMillis = Math.max(0, v); return this; }
    public DelayProfile setMaxMillis(long v)           { this.maxMillis = Math.max(0, v); return this; }
    public DelayProfile setAppliesToInbound(boolean v) { this.appliesToInbound = v; return this; }
    public DelayProfile setAppliesToOutbound(boolean v){ this.appliesToOutbound = v; return this; }
    public DelayProfile setMaxDelayMillisCap(long v)   { this.maxDelayMillisCap = Math.max(0, v); return this; }
    public DelayProfile setPacketFilter(MatchSpec v)   { this.packetFilter = v; return this; }

    public boolean appliesTo(Direction direction) {
        return direction == Direction.INBOUND ? appliesToInbound : appliesToOutbound;
    }

    /** Compute this profile's delay for a packet, clamped to the per-profile cap. */
    public long computeDelayMillis(Random random) {
        long base;
        if (mode == Mode.RANDOM) {
            long lo = Math.min(minMillis, maxMillis);
            long hi = Math.max(minMillis, maxMillis);
            base = hi <= lo ? lo : lo + (long) (random.nextDouble() * (hi - lo));
        } else {
            base = fixedMillis;
        }
        if (maxDelayMillisCap > 0) {
            base = Math.min(base, maxDelayMillisCap);
        }
        return Math.max(0, base);
    }
}
