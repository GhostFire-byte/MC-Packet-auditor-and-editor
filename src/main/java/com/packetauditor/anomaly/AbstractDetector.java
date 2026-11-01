package com.packetauditor.anomaly;

import java.util.concurrent.atomic.AtomicBoolean;

/** Base detector with enable + sensitivity handling. */
public abstract class AbstractDetector implements AnomalyDetector {

    private final String id;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private volatile double sensitivity = 0.5;

    protected AbstractDetector(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public double sensitivity() {
        return sensitivity;
    }

    @Override
    public void setSensitivity(double sensitivity) {
        this.sensitivity = Math.max(0.0, Math.min(1.0, sensitivity));
    }

    /** Linearly interpolate a threshold between {@code high} (low sensitivity) and
     *  {@code low} (high sensitivity). More sensitive = lower threshold. */
    protected double thresholdBetween(double high, double low) {
        return high + (low - high) * sensitivity;
    }

    protected Anomaly anomaly(Severity severity, String message, long sequence, String packetName) {
        return new Anomaly(System.currentTimeMillis(), id(), severity, message, sequence, packetName);
    }
}
