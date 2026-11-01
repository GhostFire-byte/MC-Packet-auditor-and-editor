package com.packetauditor.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.packetauditor.util.AuditorLog;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Versioned, encrypted-at-rest JSON store for config/profiles/rules/themes
 * (spec §16, D8/D9). Each file is wrapped in a small envelope carrying a schema
 * version; a previous copy is kept as a {@code .bak} for corruption recovery, and
 * registered migrators upgrade older versions on load.
 */
public final class JsonStore {

    private static final AuditorLog LOG = AuditorLog.of(JsonStore.class);

    private final StoragePaths paths;
    private final ProfileCrypto crypto;
    private final Gson gson;
    private final Map<String, BiFunction<JsonObject, Integer, JsonObject>> migrators = new HashMap<>();

    public JsonStore(StoragePaths paths, ProfileCrypto crypto) {
        this(paths, crypto, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create());
    }

    public JsonStore(StoragePaths paths, ProfileCrypto crypto, Gson gson) {
        this.paths = paths;
        this.crypto = crypto;
        this.gson = gson;
    }

    public Gson gson() {
        return gson;
    }

    /**
     * Register a migrator for a config file. Given the stored payload and its
     * version, it returns an upgraded payload.
     */
    public void registerMigrator(String fileName, BiFunction<JsonObject, Integer, JsonObject> migrator) {
        migrators.put(fileName, migrator);
    }

    /** Serialize, wrap, encrypt and atomically write {@code object} to a config file. */
    public void save(String fileName, Object object, int schemaVersion) {
        try {
            Files.createDirectories(paths.configDir());
            Files.createDirectories(paths.backupDir());

            JsonObject envelope = new JsonObject();
            envelope.addProperty("version", schemaVersion);
            envelope.addProperty("savedAt", System.currentTimeMillis());
            envelope.add("payload", gson.toJsonTree(object));

            byte[] plain = gson.toJson(envelope).getBytes(StandardCharsets.UTF_8);
            byte[] bytes = crypto.encrypt(plain);

            Path target = paths.config(fileName);
            if (Files.exists(target)) {
                Files.copy(target, paths.backupDir().resolve(fileName + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Path tmp = paths.config(fileName + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Failed to save config '" + fileName + "'", e);
        }
    }

    /** Load a config file, applying migrations; falls back to backup, then default. */
    public <T> T load(String fileName, Type type, int currentVersion, Supplier<T> defaultSupplier) {
        T result = tryLoad(paths.config(fileName), fileName, type, currentVersion);
        if (result != null) {
            return result;
        }
        // Corruption recovery: try the backup.
        Path bak = paths.backupDir().resolve(fileName + ".bak");
        if (Files.exists(bak)) {
            LOG.warn("Config '" + fileName + "' unreadable; attempting backup recovery.");
            result = tryLoad(bak, fileName, type, currentVersion);
            if (result != null) {
                return result;
            }
        }
        return defaultSupplier == null ? null : defaultSupplier.get();
    }

    private <T> T tryLoad(Path file, String fileName, Type type, int currentVersion) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            byte[] plain = crypto.decrypt(bytes);
            String json = new String(plain, StandardCharsets.UTF_8);
            JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();

            int version = envelope.has("version") ? envelope.get("version").getAsInt() : 0;
            JsonObject payload = envelope.has("payload") && envelope.get("payload").isJsonObject()
                    ? envelope.getAsJsonObject("payload")
                    : new JsonObject();

            if (version < currentVersion && migrators.containsKey(fileName)) {
                LOG.info("Migrating config '" + fileName + "' from v" + version + " to v" + currentVersion);
                payload = migrators.get(fileName).apply(payload, version);
            }
            return gson.fromJson(payload, type);
        } catch (Exception e) {
            LOG.warn("Could not read config '" + file.getFileName() + "': " + e.getMessage());
            return null;
        }
    }

    public boolean exists(String fileName) {
        return Files.exists(paths.config(fileName));
    }
}
