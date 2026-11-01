package com.packetauditor.ui.view;

import com.packetauditor.stats.TrafficSnapshot;
import com.packetauditor.ui.PacketRow;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.UiDataSource;
import com.packetauditor.ui.component.Component;
import com.packetauditor.ui.theme.Theme;

import java.util.List;

/**
 * Compact floating overlay (spec §14) for quick monitoring: a small premium panel
 * with live rates, queue status and the last few packets. Shares the same backend
 * as the full dashboard via the {@link UiDataSource}.
 */
public final class OverlayView extends Component {

    private final UiDataSource data;
    private int width = 196;
    private int rowCount = 6;

    public OverlayView(UiDataSource data) {
        this.data = data;
    }

    public OverlayView anchorTopLeft(int x, int y) {
        int height = 54 + rowCount * 11 + 8;
        setBounds(new Rect(x, y, width, height));
        return this;
    }

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible) {
            return;
        }
        if (bounds.width == 0) {
            anchorTopLeft(4, 4);
        }
        Views.panelBackground(r, theme, bounds, true);

        // Accent header bar.
        Rect header = new Rect(bounds.x, bounds.y, bounds.width, 16);
        r.fillRect(new Rect(bounds.x, bounds.y, 3, bounds.height), theme.accent);
        r.drawText("PACKET AUDITOR", bounds.x + 8, bounds.y + 4, theme.text, true);
        String dot = data.captureEnabled() ? "\u25CF" : "\u25CB";
        r.drawText(dot, bounds.right() - 12, bounds.y + 4,
                data.captureEnabled() ? theme.inbound : theme.textMuted, true);
        r.drawLine(bounds.x, header.bottom(), bounds.right(), header.bottom(), theme.border, 1);

        TrafficSnapshot t = data.traffic();
        int y = header.bottom() + 4;
        int x = bounds.x + 8;
        drawStat(r, theme, x, y, "IN",  String.format("%.0f/s", t.packetsInPerSecond()), theme.inbound);
        drawStat(r, theme, x + 92, y, "OUT", String.format("%.0f/s", t.packetsOutPerSecond()), theme.outbound);
        y += 11;
        drawStat(r, theme, x, y, "BW", Views.humanBytes((long) (t.bytesInPerSecond() + t.bytesOutPerSecond())) + "/s", theme.text);
        drawStat(r, theme, x + 92, y, "Q", data.queueStatus().currentSize() + "/" + data.queueStatus().capacity(), theme.warn);
        y += 13;
        r.drawLine(bounds.x + 6, y - 2, bounds.right() - 6, y - 2, theme.border, 1);

        List<PacketRow> rows = data.recentPackets(rowCount);
        for (int i = rows.size() - 1; i >= 0; i--) {
            PacketRow row = rows.get(i);
            UiColor dirColor = row.inbound ? theme.inbound : theme.outbound;
            r.drawText(row.directionLabel, x, y, dirColor, false);
            String name = Views.truncate(r, row.name, bounds.width - 58);
            r.drawText(name, x + 26, y, theme.text, false);
            if (row.severity != null) {
                r.fillRect(new Rect(bounds.right() - 10, y + 1, 5, 5), theme.severity(row.severity));
            }
            y += 11;
        }
    }

    private void drawStat(Renderer r, Theme theme, int x, int y, String label, String value, UiColor valueColor) {
        r.drawText(label, x, y, theme.textMuted, false);
        r.drawText(value, x + r.textWidth(label) + 4, y, valueColor, false);
    }
}
