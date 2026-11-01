# Minecraft Java 26.2 — Packet Auditor & Editor

A self-contained, reusable Java module for **auditing and editing Minecraft Java
Edition 26.2** network packets, designed to be integrated into an existing custom
client. It is **not** a launcher, injector, mod, or separate client.

> Built for MC **26.2** ("Chaos Cubed", 2026), which ships **non-obfuscated**
> (real class/field names) and runs on **Java 25**.

## Highlights

- **Netty handler interception** (decoded packet objects + raw bytes) — the
  standard, low-coupling seam.
- **Passive by default**: observe + forward; act only when you choose to.
- **Structured + raw editing** with native re-serialization (edits rebuild the
  immutable record packet via its canonical constructor).
- **Protection layer** enforced *below* the GUI: KeepAlive / Ping‑Pong / login /
  disconnect are protected; an explicit **"Unlock ALL"** toggle lets you
  experiment on them (off by default).
- **Interception queue**, **delay profiles**, **bandwidth throttle + simulated
  loss**, **anomaly detection**, **statistics**, **searchable history** with
  JSON/CSV export.
- **Framework-free GUI model** + a native Minecraft reference renderer with a
  ready-made **premium, high-contrast** overlay + dashboard (and a clean seam to
  drop into **your own GUI** via one `Renderer` interface). Themes
  (Premium/Dark/Light/Cyberpunk/Monochrome/Night/Lavender/Toxic‑Green/Deep‑Ocean),
  gradients, glass‑morphism. Toggle key: **Insert**. Optional Windows
  **screen-capture exclusion** (`WDA_EXCLUDEFROMCAPTURE`).
- **Crash-safe editing**: a faulty packet edit can never break the connection;
  edits emit save notifications and support a pre-save dry-run.
- **Encrypted-at-rest** JSON config (AES‑GCM) + **SQLite** bulk store.
- **ProGuard**-ready: public API kept readable, internals obfuscated.

## Module layout

```
src/main/java   CORE engine — pure Java, NO Minecraft/Netty. Builds & tests here.
src/mc/java     Minecraft 26.2 + Netty binding (handler, codec bridge, renderer,
                self-discovery, agent). Compiles in your client with -PmcJar.
src/test/java   JUnit 5 tests (lightweight mocks; no live Minecraft).
docs/           Integration guide, API overview, build notes.
examples/       Example configuration + usage snippets.
```

The core references Minecraft only through small interfaces
(`PacketTransport`, `PacketCodec`, `PacketEditAdapter`, `Renderer`, `InputSource`),
so it compiles and is fully unit-tested **without** a Minecraft classpath. The
`src/mc` source set is the only part that touches Minecraft/Netty.

## Build

```bash
# Core + tests (no Minecraft needed):
./gradlew build

# Compile the Minecraft binding against your 26.2 jar:
./gradlew compileMcJava -PmcJar=/path/to/minecraft-26.2.jar

# Obfuscated release (keeps the public api package): see docs/BUILD.md
```

See **[docs/INTEGRATION.md](docs/INTEGRATION.md)** for step-by-step integration,
**[docs/API.md](docs/API.md)** for the public API, and
**[docs/BUILD.md](docs/BUILD.md)** for build/obfuscation details.

## Quick start (from your client)

```java
PacketAuditor auditor = new PacketAuditor(
    AuditorConfig.builder()
        .baseDir(myStorageDir)                                   // D9: no hardcoded paths
        .encryptionKey(ProfileCrypto.hostKey(myKeyBytes))       // D9: at-rest encryption
        .advancedMode(false)                                     // safe default
        .guiFps(60)
        .build());
auditor.start();

// When a connection opens (from your Netty integration):
PacketAuditorInjector.inject(connection, auditor, new StreamCodecBridge());

// Bind your module toggle to the Insert key:
if (keyPressed == auditor.toggleKey()) auditor.toggleOverlay();
```

## Safety note

This module is intended for **controlled debugging and testing of your own client
and authorised test environments**. The "Unlock ALL packets" toggle can modify or
drop connection-integrity packets (KeepAlive/Pong/login) and **will** typically
disconnect you — it exists for deep experimentation and is off by default.
