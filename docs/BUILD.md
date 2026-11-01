# Build & Obfuscation

## Requirements
- **JDK 25** (Minecraft 26.2 language level). A toolchain is declared in
  `build.gradle`; Gradle will use a matching JDK 25.
- Gradle (wrapper included: `./gradlew`).

## Common tasks

```bash
# Build the core and run the test suite (no Minecraft needed):
./gradlew build

# Just the tests:
./gradlew test

# Compile the Minecraft 26.2 binding (src/mc) against your game jar:
./gradlew compileMcJava -PmcJar=/absolute/path/to/minecraft-26.2.jar

# Package the MC adapter classes into a jar:
./gradlew mcJar -PmcJar=/absolute/path/to/minecraft-26.2.jar
```

The `src/mc` source set is **not** compiled by `build`/`test` unless `-PmcJar` is
provided, so CI stays green without a Minecraft jar (D11).

## Dependencies
- `com.google.code.gson:gson` — reuse Minecraft's bundled Gson in your client
  (switch to `compileOnly` there to avoid duplication).
- `org.xerial:sqlite-jdbc` — bundle & **relocate** `org.sqlite` when shading.
- `io.netty:*` — `compileOnly` for `src/mc`; provided by the client at runtime.
- `org.junit.jupiter:*` — tests only.

## Test configuration (D11)
The `test` task:
- disables heavy headless Minecraft simulation
  (`-Dpacketauditor.test.headlessMc=false`, `-Dpacketauditor.test.mode=mocks`),
- isolates tests to lightweight packet-adapter mocks,
- uses an aggressive, memory-frugal GC configuration
  (`-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication`, 512 MB heap).

## Release obfuscation (D9)
Obfuscation is **opt-in** and not wired into `build`, so dev/test builds stay
readable. Rules live in `proguard-rules.pro` and **keep the public API**
(`com.packetauditor.api.**`) and Gson-mapped DTOs, while obfuscating internals.

To apply it, run ProGuard against the built jar with `proguard-rules.pro`, e.g.:

```bash
java -jar proguard.jar \
  -injars   build/libs/packet-auditor-0.1.0.jar \
  -outjars  build/libs/packet-auditor-0.1.0-obf.jar \
  -libraryjars "<JDK>/jmods" \
  -libraryjars /path/to/minecraft-26.2.jar \
  @proguard-rules.pro
```

(You can also wire this as a Gradle task using the `com.guardsquare:proguard-gradle`
plugin; it is intentionally left out of the default build.)

## Notes
- A harmless runtime warning `WARNING: java.lang.System::load ... sqlite-jdbc`
  appears on JDK 25 because the SQLite driver loads a native library. To silence
  it, add `--enable-native-access=ALL-UNNAMED` to the JVM args of the process that
  loads the module.
