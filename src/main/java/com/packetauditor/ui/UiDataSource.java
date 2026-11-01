package com.packetauditor.ui;

import com.packetauditor.perf.PerformanceManager;
import com.packetauditor.queue.QueueStatus;
import com.packetauditor.stats.TrafficSnapshot;
import com.packetauditor.ui.theme.Theme;

import java.util.List;

/**
 * Read-only data source that the bundled views ({@code OverlayView},
 * {@code DashboardView}) render from. This is the seam that makes the GUI
 * portable: implement it (or use the provided {@code AuditorUiDataSource}) and
 * render the views with any {@link Renderer}, including your own GUI's.
 */
public interface UiDataSource {

    Theme theme();

    TrafficSnapshot traffic();

    QueueStatus queueStatus();

    List<PacketRow> recentPackets(int limit);

    List<String> recentAnomalies(int limit);

    PerformanceManager.ResourceSnapshot resources();

    long totalProcessed();

    boolean advancedMode();

    boolean unlockAllProtected();

    boolean captureEnabled();

    /** Decoded detail for a selected packet, or null if it is no longer available. */
    default PacketDetail packetDetail(long sequence) {
        return null;
    }

    /** Packets currently held in the interception queue. */
    default java.util.List<HeldView> heldPackets() {
        return java.util.List.of();
    }

    default boolean delayEnabled() {
        return false;
    }

    default boolean netsimEnabled() {
        return false;
    }

    default double inboundLoss() {
        return 0;
    }

    default double outboundLoss() {
        return 0;
    }

    default long inboundThrottleBps() {
        return 0;
    }

    default long outboundThrottleBps() {
        return 0;
    }

    default java.util.List<RuleView> rules() {
        return java.util.List.of();
    }

    default int interceptRuleCount() {
        return 0;
    }

    default int discardRuleCount() {
        return 0;
    }
}
