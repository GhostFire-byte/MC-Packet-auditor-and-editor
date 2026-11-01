package com.packetauditor;

import com.packetauditor.history.HistoryExporter;
import com.packetauditor.history.HistoryFilter;
import com.packetauditor.history.PacketHistory;
import com.packetauditor.history.PacketHistoryEntry;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.stats.StatisticsCollector;
import com.packetauditor.stats.TrafficSnapshot;
import com.packetauditor.ui.theme.Theme;
import com.packetauditor.ui.theme.ThemeManager;
import com.packetauditor.ui.theme.ThemePresets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscCoreTest {

    private PacketHistoryEntry entry(long seq, String name, int size) {
        return new PacketHistoryEntry(seq, System.currentTimeMillis(), Direction.INBOUND,
                ProtocolPhase.PLAY, 1, name, size, name + " summary", null, false);
    }

    @Test
    void historyRollingTruncation() {
        PacketHistory h = new PacketHistory();
        h.setMaxEntries(10);
        for (int i = 1; i <= 100; i++) {
            h.add(entry(i, "P", 5));
        }
        assertEquals(10, h.size());
        assertEquals(90, h.totalEvicted());
        // Newest retained is #100
        assertEquals(100, h.recent(1).get(0).sequence());
    }

    @Test
    void historySearchFilters() {
        PacketHistory h = new PacketHistory();
        h.add(entry(1, "ChatPacket", 5));
        h.add(entry(2, "MovePacket", 5));
        h.add(entry(3, "ChatPacket", 5));
        List<PacketHistoryEntry> hits = h.search(new HistoryFilter().text("chat"), 0);
        assertEquals(2, hits.size());
    }

    @Test
    void historyExportJsonAndCsv(@TempDir Path dir) throws Exception {
        List<PacketHistoryEntry> entries = List.of(entry(1, "A", 5), entry(2, "B", 7));
        Path json = dir.resolve("h.json");
        Path csv = dir.resolve("h.csv");
        HistoryExporter.exportJson(entries, json);
        HistoryExporter.exportCsv(entries, csv);
        assertTrue(Files.size(json) > 0);
        String csvText = Files.readString(csv);
        assertTrue(csvText.contains("packetName"));
        assertTrue(csvText.contains("A"));
    }

    @Test
    void statisticsTotalsAndSnapshot() {
        StatisticsCollector stats = new StatisticsCollector();
        stats.record(Direction.INBOUND, "A", 100);
        stats.record(Direction.OUTBOUND, "B", 50);
        stats.record(Direction.INBOUND, "A", 100);
        assertEquals(2, stats.totalPacketsIn());
        assertEquals(1, stats.totalPacketsOut());
        assertEquals(200, stats.totalBytesIn());
        TrafficSnapshot snap = stats.snapshot(5);
        assertEquals(3, snap.totalPackets());
        assertEquals(2, stats.topPacketTypes(10).get(0).getValue()); // "A" seen twice
    }

    @Test
    void themePresetsAndManager() {
        assertTrue(ThemePresets.all().containsKey("Cyberpunk"));
        assertTrue(ThemePresets.all().containsKey("Deep-Ocean"));
        ThemeManager tm = new ThemeManager();
        assertTrue(tm.setCurrent("Lavender"));
        assertEquals("Lavender", tm.current().name);
        tm.setOpacity(0.5);
        assertEquals(0.5, tm.current().opacity, 0.001);
        Theme copy = ThemePresets.dark().copy();
        copy.name = "Mine";
        assertEquals("Dark", ThemePresets.dark().name); // original unchanged
    }
}
