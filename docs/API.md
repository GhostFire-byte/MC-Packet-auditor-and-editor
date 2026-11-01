# Public API Overview

All public, integration-facing types live under `com.packetauditor.api` (plus the
UI seam in `com.packetauditor.ui` and the MC binding in `com.packetauditor.mc`).
These names are preserved by the ProGuard release config (D9); internals are
obfuscated.

## Lifecycle — `com.packetauditor.api.PacketAuditor`

| Method | Purpose |
|---|---|
| `new PacketAuditor(AuditorConfig)` | Construct with configuration. |
| `start()` / `stop()` | Open/close the bulk store and background workers. |
| `attach(PacketTransport)` / `detach()` | Bind/unbind the active connection. |
| `handleInbound(pkt,id,size,phase,raw)` | Process a SERVER→CLIENT packet → `CaptureResult`. |
| `handleOutbound(pkt,id,size,phase,raw)` | Process a CLIENT→SERVER packet → `CaptureResult`. |
| `toggleOverlay()` / `setOverlayVisible(b)` / `isOverlayVisible()` | Overlay visibility. |
| `toggleKey()` | Configured toggle key (default Insert / GLFW 260). |
| `registerEditAdapter(PacketEditAdapter)` | Add a curated editable adapter. |
| `addHook(PacketHook)` / `removeHook(...)` | Pre-send / post-receive hooks. |
| `addConnectionListener(...)` / `fireConnection(...)` | Connection events (§4). |

### Helper methods
- `editPacketField(long sequence, String fieldPath, String value)` → `EditResult`
- `changeAllMatching(packetName, fieldPath, value)` → installs an `EditingRule`
- `interceptMatching(MatchSpec)` / `discardMatching(MatchSpec)`

### Subsystem accessors
`protection()`, `decoder()`, `stats()`, `history()`, `anomaly()`, `queue()`,
`delay()`, `netsim()`, `rules()`, `performance()`, `themes()`, `pipeline()`,
`jsonStore()`, `paths()`, `sqlite()`.

## Configuration — `AuditorConfig` (builder)

`baseDir`, `encryptionKey(KeyProvider)`, `defaultTheme`, `advancedMode`,
`captureEnabled`, `diskPersistence`, `historyMaxEntries`, `historyMaxBytes`,
`sqliteMaxRows`, `guiFps`, `toggleKey`, `settingsBridge`, `logLevel`, `logSink`,
`enableSqlite`.

## Protection — `com.packetauditor.protect`
- `ProtectionPolicy` — `setAdvancedMode(b)`, `setUnlockAllProtected(b)`,
  `evaluate(PacketMeta, OperationType)` → `ProtectionDecision`.
- `OperationType` — `INSPECT, EDIT, INTERCEPT, DELAY, DISCARD, SIMULATE_LOSS, THROTTLE`.
- `ProtectionTier` — `TIER1_ESSENTIAL, TIER2_STANDARD`.
- `EssentialPacketMatcher` — configurable Tier-1 markers (KeepAlive/Ping/Pong hard-locked).

## Matching — `com.packetauditor.match`
- `MatchMode` — `BASIC, INTERMEDIATE, ADVANCED`.
- `MatchSpec` (builder) + `FieldCondition` + `ConditionOperator` + `LogicOp`.
  `requiredMode()` / `isValidUnder(mode)` implement "switching modes keeps rules,
  invalid ones inactive".

## Editing — `com.packetauditor.rules`
- `EditingRule`, `FieldReplacement`, `EditingRuleEngine`, `EditResult`,
  `ModificationRecord` (editing history).

## Queue / Delay / NetSim
- `queue.InterceptionQueue` — `hold/release/releaseEdited/discard/releaseAll/
  discardAll/releaseBatch`, `OverflowPolicy`, `QueueStatus`, timeout protection.
- `delay.DelayManager` + `DelayProfile` (fixed/random, per-direction, caps).
- `netsim.NetworkSimulator` — loss probability + bandwidth throttle (off by default).

## Decoding — `com.packetauditor.codec`
- `DecoderService`, `ReflectiveDecoder`, `RecordRebuildAdapter` (generic editable
  record adapter), `PacketCodec`, `PacketEditAdapter`, `ValueParser`.

## Stats / Anomaly / History
- `stats.StatisticsCollector` → `TrafficSnapshot`, `TrafficBucket` (timeline).
- `anomaly.AnomalyDetectionService` + detectors + `CustomRuleDetector`, `Severity`, `Anomaly`.
- `history.PacketHistory`, `PacketHistoryEntry`, `HistoryFilter`, `HistoryExporter` (JSON/CSV).

## Storage — `com.packetauditor.store`
- `StoragePaths`, `JsonStore` (versioned, migrated, backed-up, encrypted),
  `SqlitePacketStore` (async, retention), `ProfileCrypto`, `KeyProvider`.

## UI seam — `com.packetauditor.ui`
- `Renderer`, `InputSource`, `InputEvent`, `Rect`, `UiColor`, `PacketRow`.
- `UiDataSource` (read-side) + `UiActions` (write-side) — the portability seam.
- `ui.theme`: `Theme`, `ThemeManager`, `ThemePresets` (incl. `premium()`), `GradientSpec`, `GlassSpec`.
- `ui.component`: `Component`, `Container`, `Panel`, `Label`, `Button`,
  `ToggleSwitch`, `ScrollListView`, `TabBar`.
- `ui.view`: `OverlayView` (compact), `DashboardView` (full), `ToastOverlay`.

## Notifications & data binding — `com.packetauditor.api`
- `Notification` + `Notification.Listener`; `PacketAuditor.addNotificationListener(...)`,
  `notify(...)`.
- `PacketAuditor.editPacketField(...)` (emits a save toast) /
  `previewPacketEdit(...)` (dry-run).
- `AuditorUiDataSource` (implements `UiDataSource`), `AuditorUiActions` (implements `UiActions`).

## MC binding — `com.packetauditor.mc`
- `net.AuditorChannelHandler`, `net.NettyPacketTransport`, `net.PacketAuditorInjector`
- `codec.StreamCodecBridge`, `codec.CuratedAdapters`
- `render.McRenderer`, `render.McGuiHost`, `render.CaptureExclusion` (WDA_EXCLUDEFROMCAPTURE)
- `discovery.ConnectionDiscovery`
- `PacketAuditorBootstrap`, `PacketAuditorAgent`

## `CaptureResult` (returned to the Netty handler)
- `Action.FORWARD_ORIGINAL` — forward the packet unchanged.
- `Action.FORWARD_REPLACEMENT` — forward `replacement()` (edited).
- `Action.SUPPRESS` — do not forward now (held/delayed/dropped by the core).
