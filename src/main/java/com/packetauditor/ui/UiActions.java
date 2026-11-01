package com.packetauditor.ui;

/**
 * Write-side actions the bundled views invoke in response to user interaction
 * (toggles, packet selection, per-packet action menu). Paired with
 * {@link UiDataSource} (read-side), this keeps the views fully decoupled from the
 * backend so they work with your own GUI too. The provided
 * {@code AuditorUiActions} wires these to a {@code PacketAuditor}.
 */
public interface UiActions {

    default void setCaptureEnabled(boolean enabled) { }

    default void setAdvancedMode(boolean enabled) { }

    default void setUnlockAllProtected(boolean enabled) { }

    /** Left-click: select a packet (e.g. show details). */
    default void selectPacket(long sequence) { }

    /** Right-click: open the per-packet action menu (inspect/edit/intercept/delay/discard). */
    default void openPacketActionMenu(long sequence) { }

    /** Cycle to the next theme by name. */
    default void cycleTheme() { }

    /** Release all held packets. */
    default void releaseAllHeld() { }

    /** Discard all held packets. */
    default void discardAllHeld() { }

    // ----- per-packet actions (from the action menu) -----

    /** Inspect a packet (open its detail). */
    default void inspect(long sequence) { }

    /** Open the detail/editor focused on editing a packet. */
    default void editPacket(long sequence) { }

    /** Change all future packets of this type: install an editing rule for the field. */
    default String applyEditToAllMatching(long sequence, String fieldPath, String value) {
        return "Rules are not wired in this host.";
    }

    /** Hold future matching packets of this type. */
    default void interceptMatchingType(long sequence) { }

    /** Delay future matching packets of this type. */
    default void delayMatchingType(long sequence) { }

    /** Discard future matching packets of this type. */
    default void discardMatchingType(long sequence) { }

    // ----- editing a selected packet's field -----

    /** Save a field edit. Returns null on success, or an error message. */
    default String saveFieldEdit(long sequence, String fieldPath, String value) {
        return "Editing is not wired in this host.";
    }

    /** Dry-run a field edit. Returns null if it would succeed, or a warning. */
    default String previewFieldEdit(long sequence, String fieldPath, String value) {
        return null;
    }

    // ----- queue item actions -----

    default void releaseHeld(long id) { }

    default void discardHeld(long id) { }

    // ----- tools -----

    default void setDelayEnabled(boolean enabled) { }

    default void setNetsimEnabled(boolean enabled) { }

    default void setInboundLoss(double probability) { }

    default void setOutboundLoss(double probability) { }

    default void setInboundThrottle(long bytesPerSecond) { }

    default void setOutboundThrottle(long bytesPerSecond) { }

    default void toggleRule(String id, boolean enabled) { }

    default void deleteRule(String id) { }

    default void clearInterceptRules() { }

    default void clearDiscardRules() { }

    /** Export the packet history to JSON; returns a status message. */
    default String exportHistoryJson() {
        return "Export is not wired in this host.";
    }

    /** Export the packet history to CSV; returns a status message. */
    default String exportHistoryCsv() {
        return "Export is not wired in this host.";
    }
}
