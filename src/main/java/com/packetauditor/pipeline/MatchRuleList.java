package com.packetauditor.pipeline;

import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A named, ordered list of {@link MatchSpec}s used for a pipeline action such as
 * "intercept matching future packets" or "discard matching future packets"
 * (spec §6). Thread-safe for concurrent capture + GUI edits.
 */
public final class MatchRuleList {

    private final CopyOnWriteArrayList<MatchSpec> specs = new CopyOnWriteArrayList<>();

    public void add(MatchSpec spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    public void clear() {
        specs.clear();
    }

    public int size() {
        return specs.size();
    }

    public java.util.List<MatchSpec> all() {
        return specs;
    }

    /** True if any spec matches the packet. {@code decoded} may be null. */
    public boolean matchesAny(CapturedPacket packet, DecodedPacket decoded) {
        for (MatchSpec s : specs) {
            if (s.matches(packet, decoded)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }
}
