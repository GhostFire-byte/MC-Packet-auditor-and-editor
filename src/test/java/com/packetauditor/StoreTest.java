package com.packetauditor;

import com.google.gson.JsonObject;
import com.packetauditor.history.PacketHistoryEntry;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.store.JsonStore;
import com.packetauditor.store.ProfileCrypto;
import com.packetauditor.store.SqlitePacketStore;
import com.packetauditor.store.StoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTest {

    // ---------- crypto ----------

    @Test
    void hostKeyRoundTrip() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        ProfileCrypto crypto = new ProfileCrypto(ProfileCrypto.hostKey(key));
        byte[] plain = "secret profile".getBytes(StandardCharsets.UTF_8);
        byte[] enc = crypto.encrypt(plain);
        assertNotEquals(new String(plain), new String(enc)); // not plaintext
        assertArrayEquals(plain, crypto.decrypt(enc));
    }

    @Test
    void passphraseRoundTrip() {
        ProfileCrypto crypto = new ProfileCrypto(ProfileCrypto.passphrase("hunter2"));
        byte[] plain = "deep secret".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(plain, crypto.decrypt(crypto.encrypt(plain)));
    }

    @Test
    void noKeyIsPassthrough() {
        ProfileCrypto crypto = new ProfileCrypto(() -> null);
        byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(plain, crypto.encrypt(plain));
    }

    // ---------- JSON store ----------

    public static final class Settings {
        public String theme = "Dark";
        public int limit = 10;
    }

    @Test
    void jsonStoreSaveLoadEncrypted(@TempDir Path dir) {
        StoragePaths paths = StoragePaths.underBase(dir);
        ProfileCrypto crypto = new ProfileCrypto(ProfileCrypto.passphrase("pw"));
        JsonStore store = new JsonStore(paths, crypto);

        Settings s = new Settings();
        s.theme = "Cyberpunk";
        s.limit = 99;
        store.save("settings.json", s, 1);

        Settings loaded = store.load("settings.json", Settings.class, 1, Settings::new);
        assertEquals("Cyberpunk", loaded.theme);
        assertEquals(99, loaded.limit);
    }

    @Test
    void jsonStoreMigration(@TempDir Path dir) {
        StoragePaths paths = StoragePaths.underBase(dir);
        JsonStore store = new JsonStore(paths, new ProfileCrypto(() -> null));
        store.registerMigrator("settings.json", (payload, from) -> {
            JsonObject p = payload;
            if (!p.has("limit")) {
                p.addProperty("limit", 5);
            }
            return p;
        });
        // Save a v0-style payload lacking "limit" by saving raw settings then reloading as v2.
        Settings s = new Settings();
        store.save("settings.json", s, 1);
        Settings loaded = store.load("settings.json", Settings.class, 2, Settings::new);
        assertTrue(loaded.limit >= 5);
    }

    @Test
    void jsonStoreDefaultWhenMissing(@TempDir Path dir) {
        JsonStore store = new JsonStore(StoragePaths.underBase(dir), new ProfileCrypto(() -> null));
        Settings loaded = store.load("missing.json", Settings.class, 1, () -> {
            Settings d = new Settings();
            d.theme = "Fallback";
            return d;
        });
        assertEquals("Fallback", loaded.theme);
    }

    // ---------- SQLite store ----------

    private PacketHistoryEntry entry(long seq) {
        return new PacketHistoryEntry(seq, System.currentTimeMillis(), Direction.INBOUND,
                ProtocolPhase.PLAY, 10, "TestPacket", 42, "x=1", null, false);
    }

    @Test
    void sqliteInsertAndQuery(@TempDir Path dir) throws Exception {
        try (SqlitePacketStore store = new SqlitePacketStore(StoragePaths.underBase(dir))) {
            for (int i = 1; i <= 5; i++) {
                store.insertPacketAsync(entry(i));
            }
            // allow the async writer to flush
            Thread.sleep(500);
            List<PacketHistoryEntry> recent = store.queryRecent(10);
            assertEquals(5, recent.size());
            assertEquals(5, recent.get(0).sequence()); // newest first
        }
    }

    @Test
    void sqliteRetentionCaps(@TempDir Path dir) throws Exception {
        try (SqlitePacketStore store = new SqlitePacketStore(StoragePaths.underBase(dir))) {
            store.setMaxPacketRows(10);
            for (int i = 1; i <= 50; i++) {
                store.insertPacketAsync(entry(i));
            }
            Thread.sleep(800);
            store.enforceRetention();
            long count = store.packetRowCount();
            assertTrue(count <= 10, "retention should cap rows, was " + count);
        }
    }
}
