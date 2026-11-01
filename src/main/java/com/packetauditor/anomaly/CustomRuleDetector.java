package com.packetauditor.anomaly;

import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A user-defined anomaly rule (spec §11 "custom detection"): raises an anomaly of
 * a chosen severity whenever a packet matches a {@link MatchSpec}. A per-rule
 * cooldown avoids flooding the anomaly history.
 */
public final class CustomRuleDetector extends AbstractDetector {

    private final MatchSpec spec;
    private final Severity severity;
    private final String message;
    private volatile long cooldownMillis;
    private final AtomicLong lastEmit = new AtomicLong();

    public CustomRuleDetector(String id, MatchSpec spec, Severity severity, String message,
                              long cooldownMillis) {
        super(id);
        this.spec = spec;
        this.severity = severity == null ? Severity.INFO : severity;
        this.message = message == null ? "Custom anomaly rule matched" : message;
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    public MatchSpec spec()     { return spec; }
    public Severity severity()  { return severity; }
    public String message()     { return message; }

    public void setCooldownMillis(long v) {
        this.cooldownMillis = Math.max(0, v);
    }

    @Override
    public Anomaly inspect(CapturedPacket packet, StatisticsCollector stats) {
        // packet.decoded() is populated by the detection service before custom rules run.
        if (!spec.matches(packet, packet.decoded())) {
            return null;
        }
        long now = System.currentTimeMillis();
        long last = lastEmit.get();
        if (cooldownMillis > 0 && now - last < cooldownMillis) {
            return null;
        }
        if (!lastEmit.compareAndSet(last, now)) {
            return null;
        }
        return anomaly(severity, message, packet.meta().sequence(), packet.meta().packetName());
    }
}
