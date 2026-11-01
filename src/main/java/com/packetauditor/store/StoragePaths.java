package com.packetauditor.store;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolved on-disk locations (spec §16, D9). There are <b>no hardcoded path
 * conventions</b>: the host injects a base directory; when absent, an obscured,
 * non-obvious default under the user home is used. All sub-paths are derived
 * from the base and are fully overridable.
 */
public final class StoragePaths {

    private final Path baseDir;
    private final Path configDir;
    private final Path dataDir;
    private final Path backupDir;

    private StoragePaths(Path baseDir, Path configDir, Path dataDir, Path backupDir) {
        this.baseDir = baseDir;
        this.configDir = configDir;
        this.dataDir = dataDir;
        this.backupDir = backupDir;
    }

    public Path baseDir()   { return baseDir; }
    public Path configDir() { return configDir; }
    public Path dataDir()   { return dataDir; }
    public Path backupDir() { return backupDir; }

    public Path config(String fileName) { return configDir.resolve(fileName); }
    public Path data(String fileName)   { return dataDir.resolve(fileName); }

    /** Build from an explicit host-provided base directory. */
    public static StoragePaths underBase(Path base) {
        Path b = base.toAbsolutePath().normalize();
        return new StoragePaths(b, b.resolve("cfg"), b.resolve("db"), b.resolve("bak"));
    }

    /**
     * Obscured default base (D9): a non-obvious directory under the user home.
     * The folder name is intentionally opaque rather than a predictable
     * "packet-auditor" convention. Callers should prefer an injected path.
     */
    public static StoragePaths obscuredDefault() {
        String home = System.getProperty("user.home", ".");
        // Opaque, stable token so data persists but the location is not obvious.
        String token = ".pa-" + Integer.toHexString("packet-auditor-store".hashCode());
        return underBase(Paths.get(home, token));
    }

    @Override
    public String toString() {
        return "StoragePaths{base=" + baseDir + "}";
    }
}
