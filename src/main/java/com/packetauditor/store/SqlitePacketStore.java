package com.packetauditor.store;

import com.packetauditor.history.PacketHistoryEntry;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.rules.ModificationRecord;
import com.packetauditor.util.AuditorLog;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous SQLite bulk store for packet history and modification history
 * (spec §16, D8). Writes are enqueued and drained on a dedicated background
 * thread (never the networking/main thread, D11/§15) and committed in batches.
 * Retention is enforced by capping the number of retained rows (rolling
 * truncation). The DB itself is left plaintext for performance (D9 scope).
 */
public final class SqlitePacketStore implements AutoCloseable {

    private static final AuditorLog LOG = AuditorLog.of(SqlitePacketStore.class);
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final Object dbLock = new Object();

    private final BlockingQueue<Runnable> writeQueue = new ArrayBlockingQueue<>(10_000);
    private final Thread writer;
    private volatile boolean running = true;

    private volatile long maxPacketRows = 500_000; // generous retention cap

    public SqlitePacketStore(StoragePaths paths) throws SQLException {
        try {
            Files.createDirectories(paths.dataDir());
        } catch (Exception e) {
            throw new SQLException("Cannot create data directory", e);
        }
        String url = "jdbc:sqlite:" + paths.data("auditor.db").toString();
        this.connection = DriverManager.getConnection(url);
        initSchema();

        this.writer = new Thread(this::drainLoop, "packet-auditor-sqlite-writer");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    public void setMaxPacketRows(long maxPacketRows) {
        this.maxPacketRows = Math.max(1, maxPacketRows);
    }

    public long maxPacketRows() {
        return maxPacketRows;
    }

    private void initSchema() throws SQLException {
        synchronized (dbLock) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("CREATE TABLE IF NOT EXISTS schema_meta ("
                        + "key TEXT PRIMARY KEY, value TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS packet_history ("
                        + "sequence INTEGER PRIMARY KEY,"
                        + "ts INTEGER, direction TEXT, phase TEXT, packet_id INTEGER,"
                        + "packet_name TEXT, size_bytes INTEGER, summary TEXT, raw_hex TEXT,"
                        + "edited INTEGER)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_packet_name ON packet_history(packet_name)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_packet_ts ON packet_history(ts)");
                st.execute("CREATE TABLE IF NOT EXISTS modification_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "ts INTEGER, rule_id TEXT, rule_name TEXT, packet_name TEXT,"
                        + "direction TEXT, field_path TEXT, original_value TEXT, modified_value TEXT)");
                st.executeUpdate("INSERT OR REPLACE INTO schema_meta(key,value) VALUES('version','"
                        + SCHEMA_VERSION + "')");
            }
        }
    }

    public void insertPacketAsync(PacketHistoryEntry e) {
        enqueue(() -> insertPacket(e));
    }

    public void insertModificationAsync(ModificationRecord r) {
        enqueue(() -> insertModification(r));
    }

    private void enqueue(Runnable task) {
        if (!running) {
            return;
        }
        if (!writeQueue.offer(task)) {
            LOG.warn("SQLite write queue full; dropping a history write (DB is slow).");
        }
    }

    private void drainLoop() {
        List<Runnable> batch = new ArrayList<>(256);
        while (running || !writeQueue.isEmpty()) {
            try {
                Runnable first = writeQueue.poll(250, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.clear();
                batch.add(first);
                writeQueue.drainTo(batch, 255);
                synchronized (dbLock) {
                    boolean prevAuto = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                    try {
                        for (Runnable r : batch) {
                            r.run();
                        }
                        connection.commit();
                    } catch (Throwable t) {
                        connection.rollback();
                        LOG.warn("Batch write failed; rolled back", t);
                    } finally {
                        connection.setAutoCommit(prevAuto);
                    }
                }
                maybeEnforceRetention();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                LOG.error("SQLite writer loop error", t);
            }
        }
    }

    private void insertPacket(PacketHistoryEntry e) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO packet_history"
                        + "(sequence,ts,direction,phase,packet_id,packet_name,size_bytes,summary,raw_hex,edited)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, e.sequence());
            ps.setLong(2, e.timestampMillis());
            ps.setString(3, e.direction().name());
            ps.setString(4, e.phase().name());
            ps.setInt(5, e.packetId());
            ps.setString(6, e.packetName());
            ps.setInt(7, e.sizeBytes());
            ps.setString(8, e.summary());
            ps.setString(9, e.rawHex());
            ps.setInt(10, e.edited() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.warn("insertPacket failed: " + ex.getMessage());
        }
    }

    private void insertModification(ModificationRecord r) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO modification_history"
                        + "(ts,rule_id,rule_name,packet_name,direction,field_path,original_value,modified_value)"
                        + " VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, r.timestampMillis());
            ps.setString(2, r.ruleId());
            ps.setString(3, r.ruleName());
            ps.setString(4, r.packetName());
            ps.setString(5, r.direction() == null ? null : r.direction().name());
            ps.setString(6, r.fieldPath());
            ps.setString(7, r.originalValue());
            ps.setString(8, r.modifiedValue());
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.warn("insertModification failed: " + ex.getMessage());
        }
    }

    private long lastRetentionCheck;

    private void maybeEnforceRetention() {
        long now = System.currentTimeMillis();
        if (now - lastRetentionCheck < 5_000) {
            return;
        }
        lastRetentionCheck = now;
        enforceRetention();
    }

    /** Immediately trim the packet_history table down to {@link #maxPacketRows()}. */
    public void enforceRetention() {
        try {
            synchronized (dbLock) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM packet_history WHERE sequence NOT IN "
                                + "(SELECT sequence FROM packet_history ORDER BY sequence DESC LIMIT ?)")) {
                    ps.setLong(1, maxPacketRows);
                    int removed = ps.executeUpdate();
                    if (removed > 0) {
                        LOG.debug("Retention removed " + removed + " old packet rows.");
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.warn("Retention cleanup failed: " + ex.getMessage());
        }
    }

    /** Query recent packets (newest first). Runs synchronously on the caller's thread. */
    public List<PacketHistoryEntry> queryRecent(int limit) {
        List<PacketHistoryEntry> out = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT sequence,ts,direction,phase,packet_id,packet_name,size_bytes,summary,raw_hex,edited"
                            + " FROM packet_history ORDER BY sequence DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readEntry(rs));
                    }
                }
            } catch (SQLException ex) {
                LOG.warn("queryRecent failed: " + ex.getMessage());
            }
        }
        return out;
    }

    private PacketHistoryEntry readEntry(ResultSet rs) throws SQLException {
        return new PacketHistoryEntry(
                rs.getLong("sequence"),
                rs.getLong("ts"),
                Direction.valueOf(rs.getString("direction")),
                ProtocolPhase.valueOf(rs.getString("phase")),
                rs.getInt("packet_id"),
                rs.getString("packet_name"),
                rs.getInt("size_bytes"),
                rs.getString("summary"),
                rs.getString("raw_hex"),
                rs.getInt("edited") != 0);
    }

    public long packetRowCount() {
        synchronized (dbLock) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM packet_history")) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (SQLException ex) {
                return -1;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            writer.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (dbLock) {
            try {
                connection.close();
            } catch (SQLException ex) {
                LOG.warn("Error closing SQLite: " + ex.getMessage());
            }
        }
    }
}
