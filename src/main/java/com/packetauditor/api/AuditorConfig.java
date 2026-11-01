package com.packetauditor.api;

import com.packetauditor.store.KeyProvider;
import com.packetauditor.util.AuditorLog;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Immutable configuration for a {@link PacketAuditor}, built via {@link Builder}
 * (D12). Everything has a safe default so the host can start with
 * {@code AuditorConfig.builder().build()} and refine from there.
 */
public final class AuditorConfig {

    /** Default overlay toggle key (GLFW_KEY_INSERT = 260), per D5. */
    public static final int DEFAULT_TOGGLE_KEY = 260;

    private final Path baseDir;                 // nullable -> obscured default
    private final KeyProvider keyProvider;      // nullable -> no at-rest encryption
    private final String defaultTheme;
    private final boolean advancedModeDefault;
    private final boolean captureEnabledDefault;
    private final boolean diskPersistenceDefault;
    private final int historyMaxEntries;
    private final long historyMaxBytes;
    private final long sqliteMaxRows;
    private final int guiFps;
    private final int toggleKey;
    private final SettingsBridge settingsBridge; // nullable
    private final AuditorLog.Level logLevel;
    private final Consumer<String> logSink;      // nullable
    private final boolean enableSqlite;
    private final boolean excludeFromCapture;

    private AuditorConfig(Builder b) {
        this.baseDir = b.baseDir;
        this.keyProvider = b.keyProvider;
        this.defaultTheme = b.defaultTheme;
        this.advancedModeDefault = b.advancedModeDefault;
        this.captureEnabledDefault = b.captureEnabledDefault;
        this.diskPersistenceDefault = b.diskPersistenceDefault;
        this.historyMaxEntries = b.historyMaxEntries;
        this.historyMaxBytes = b.historyMaxBytes;
        this.sqliteMaxRows = b.sqliteMaxRows;
        this.guiFps = b.guiFps;
        this.toggleKey = b.toggleKey;
        this.settingsBridge = b.settingsBridge;
        this.logLevel = b.logLevel;
        this.logSink = b.logSink;
        this.enableSqlite = b.enableSqlite;
        this.excludeFromCapture = b.excludeFromCapture;
    }

    public Path baseDir()                 { return baseDir; }
    public KeyProvider keyProvider()      { return keyProvider; }
    public String defaultTheme()          { return defaultTheme; }
    public boolean advancedModeDefault()  { return advancedModeDefault; }
    public boolean captureEnabledDefault(){ return captureEnabledDefault; }
    public boolean diskPersistenceDefault(){ return diskPersistenceDefault; }
    public int historyMaxEntries()        { return historyMaxEntries; }
    public long historyMaxBytes()         { return historyMaxBytes; }
    public long sqliteMaxRows()           { return sqliteMaxRows; }
    public int guiFps()                   { return guiFps; }
    public int toggleKey()                { return toggleKey; }
    public SettingsBridge settingsBridge(){ return settingsBridge; }
    public AuditorLog.Level logLevel()    { return logLevel; }
    public Consumer<String> logSink()     { return logSink; }
    public boolean enableSqlite()         { return enableSqlite; }
    public boolean excludeFromCapture()   { return excludeFromCapture; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path baseDir;
        private KeyProvider keyProvider;
        private String defaultTheme = "Dark";
        private boolean advancedModeDefault = false;
        private boolean captureEnabledDefault = true;
        private boolean diskPersistenceDefault = false;
        private int historyMaxEntries = 50_000;
        private long historyMaxBytes = 200L * 1024 * 1024;
        private long sqliteMaxRows = 500_000;
        private int guiFps = 60;
        private int toggleKey = DEFAULT_TOGGLE_KEY;
        private SettingsBridge settingsBridge;
        private AuditorLog.Level logLevel = AuditorLog.Level.INFO;
        private Consumer<String> logSink;
        private boolean enableSqlite = true;
        private boolean excludeFromCapture = false;

        /** Host-provided base directory for all storage (D9). If null, an obscured default is used. */
        public Builder baseDir(Path v)                  { this.baseDir = v; return this; }

        /** Encryption key supplier for at-rest profiles/config (D9). If null, plaintext. */
        public Builder encryptionKey(KeyProvider v)     { this.keyProvider = v; return this; }

        public Builder defaultTheme(String v)           { this.defaultTheme = v; return this; }
        public Builder advancedMode(boolean v)          { this.advancedModeDefault = v; return this; }
        public Builder captureEnabled(boolean v)        { this.captureEnabledDefault = v; return this; }
        public Builder diskPersistence(boolean v)       { this.diskPersistenceDefault = v; return this; }
        public Builder historyMaxEntries(int v)         { this.historyMaxEntries = v; return this; }
        public Builder historyMaxBytes(long v)          { this.historyMaxBytes = v; return this; }
        public Builder sqliteMaxRows(long v)            { this.sqliteMaxRows = v; return this; }
        public Builder guiFps(int v)                    { this.guiFps = v; return this; }
        public Builder toggleKey(int v)                 { this.toggleKey = v; return this; }
        public Builder settingsBridge(SettingsBridge v) { this.settingsBridge = v; return this; }
        public Builder logLevel(AuditorLog.Level v)     { this.logLevel = v; return this; }
        public Builder logSink(Consumer<String> v)      { this.logSink = v; return this; }
        public Builder enableSqlite(boolean v)          { this.enableSqlite = v; return this; }

        /**
         * Windows only: exclude the Minecraft window (and this overlay) from screen
         * capture / recording via {@code WDA_EXCLUDEFROMCAPTURE}. Applied by the MC
         * binding's {@code CaptureExclusion} on start. No effect on other OSes.
         */
        public Builder excludeFromCapture(boolean v)    { this.excludeFromCapture = v; return this; }

        public AuditorConfig build() {
            return new AuditorConfig(this);
        }
    }
}
