package com.packetauditor.history;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports packet history to JSON or CSV (spec §12 "manual export"). Streams the
 * records so large histories do not need to be fully materialised as strings.
 */
public final class HistoryExporter {

    private HistoryExporter() {
    }

    public static void exportJson(List<PacketHistoryEntry> entries, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             JsonWriter jw = new JsonWriter(w)) {
            jw.setIndent("  ");
            jw.beginArray();
            for (PacketHistoryEntry e : entries) {
                jw.beginObject();
                jw.name("sequence").value(e.sequence());
                jw.name("timestamp").value(e.timestampMillis());
                jw.name("direction").value(e.direction().name());
                jw.name("phase").value(e.phase().name());
                jw.name("packetId").value(e.packetId());
                jw.name("packetName").value(e.packetName());
                jw.name("sizeBytes").value(e.sizeBytes());
                jw.name("edited").value(e.edited());
                jw.name("summary").value(e.summary());
                if (e.rawHex() != null) {
                    jw.name("rawHex").value(e.rawHex());
                }
                jw.endObject();
            }
            jw.endArray();
        }
    }

    public static void exportCsv(List<PacketHistoryEntry> entries, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("sequence,timestamp,direction,phase,packetId,packetName,sizeBytes,edited,summary\n");
            for (PacketHistoryEntry e : entries) {
                w.write(Long.toString(e.sequence()));
                w.write(',');
                w.write(Long.toString(e.timestampMillis()));
                w.write(',');
                w.write(e.direction().name());
                w.write(',');
                w.write(e.phase().name());
                w.write(',');
                w.write(Integer.toString(e.packetId()));
                w.write(',');
                w.write(csv(e.packetName()));
                w.write(',');
                w.write(Integer.toString(e.sizeBytes()));
                w.write(',');
                w.write(Boolean.toString(e.edited()));
                w.write(',');
                w.write(csv(e.summary()));
                w.write('\n');
            }
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
