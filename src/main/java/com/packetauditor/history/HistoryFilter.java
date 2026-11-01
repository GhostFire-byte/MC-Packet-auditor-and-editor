package com.packetauditor.history;

import com.packetauditor.model.Direction;

/**
 * A searchable/filter predicate over {@link PacketHistoryEntry} (spec §4/§12):
 * text search plus direction, id, name and size-range filters. All criteria are
 * optional; an empty filter matches everything.
 */
public final class HistoryFilter {

    private String text;              // matches name/summary (case-insensitive)
    private Direction direction;      // null == any
    private Integer packetId;         // null == any
    private String packetName;        // exact (case-insensitive), null == any
    private Integer minSize;
    private Integer maxSize;
    private Boolean editedOnly;

    public HistoryFilter text(String v)          { this.text = emptyToNull(v); return this; }
    public HistoryFilter direction(Direction v)  { this.direction = v; return this; }
    public HistoryFilter packetId(Integer v)     { this.packetId = v; return this; }
    public HistoryFilter packetName(String v)    { this.packetName = emptyToNull(v); return this; }
    public HistoryFilter minSize(Integer v)      { this.minSize = v; return this; }
    public HistoryFilter maxSize(Integer v)      { this.maxSize = v; return this; }
    public HistoryFilter editedOnly(Boolean v)   { this.editedOnly = v; return this; }

    public boolean matches(PacketHistoryEntry e) {
        if (direction != null && e.direction() != direction) {
            return false;
        }
        if (packetId != null && e.packetId() != packetId) {
            return false;
        }
        if (packetName != null && !packetName.equalsIgnoreCase(e.packetName())) {
            return false;
        }
        if (minSize != null && e.sizeBytes() < minSize) {
            return false;
        }
        if (maxSize != null && e.sizeBytes() > maxSize) {
            return false;
        }
        if (editedOnly != null && editedOnly && !e.edited()) {
            return false;
        }
        if (text != null) {
            String t = text.toLowerCase();
            boolean hit = e.packetName().toLowerCase().contains(t)
                    || e.summary().toLowerCase().contains(t);
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
