package com.packetauditor.pipeline;

/**
 * What the Netty handler should do with a packet after the core has processed it.
 */
public final class CaptureResult {

    public enum Action {
        /** Forward the original packet unchanged. */
        FORWARD_ORIGINAL,
        /** Forward {@link #replacement()} instead of the original (it was edited). */
        FORWARD_REPLACEMENT,
        /** Do not forward now — the core is holding, delaying or dropping it. */
        SUPPRESS
    }

    private final Action action;
    private final Object replacement;
    private final String note;

    private CaptureResult(Action action, Object replacement, String note) {
        this.action = action;
        this.replacement = replacement;
        this.note = note;
    }

    public Action action()        { return action; }
    public Object replacement()   { return replacement; }
    public String note()          { return note; }

    public static CaptureResult forwardOriginal() {
        return new CaptureResult(Action.FORWARD_ORIGINAL, null, null);
    }

    public static CaptureResult forwardReplacement(Object replacement) {
        return new CaptureResult(Action.FORWARD_REPLACEMENT, replacement, null);
    }

    public static CaptureResult suppress(String note) {
        return new CaptureResult(Action.SUPPRESS, null, note);
    }
}
