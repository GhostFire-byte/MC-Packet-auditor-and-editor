# Integration Guide (for Codex)

This guide explains how to integrate the Packet Auditor module into an existing
custom Minecraft **26.2** client. The module is self-contained; you wire it to
your runtime through a few small, readable interfaces. You do **not** need to
modify the module to integrate it.

> The module's core is Minecraft-free. Everything that touches Minecraft/Netty
> lives in `src/mc` (`com.packetauditor.mc.*`). Build that source set against your
> 26.2 jar with `-PmcJar=...`.

---

## 0. Add the module

- Add the built core jar (and the `mc-adapter` classes) to your client classpath.
- Minecraft already provides Netty and Gson at runtime; the module only bundles
  the SQLite driver (and uses your Gson). If you shade the module, relocate
  `org.sqlite` to avoid conflicts.

---

## 1. Initialise the module

```java
import com.packetauditor.api.*;
import com.packetauditor.store.ProfileCrypto;

AuditorConfig config = AuditorConfig.builder()
    .baseDir(myStorageBaseDir)                       // §7 storage path (see step 7)
    .encryptionKey(ProfileCrypto.firstAvailable(     // §9 at-rest encryption
        ProfileCrypto.hostKey(myKeyBytesOrNull),     //   1) host-provided key (best)
        ProfileCrypto.passphrase(myPassphraseOrNull),//   2) passphrase fallback
        ProfileCrypto.machineDerived()))             //   3) machine-derived fallback
    .advancedMode(false)                             // safe default (view-only edits off)
    .diskPersistence(false)                          // RAM history by default
    .historyMaxEntries(50_000)
    .guiFps(60)                                      // configurable refresh rate
    .toggleKey(AuditorConfig.DEFAULT_TOGGLE_KEY)     // Insert (GLFW 260)
    .settingsBridge(myOptionalSettingsBridge)        // step 4 (optional)
    .logSink(line -> myLogger.info(line))            // route logs into your logger
    .build();

PacketAuditor auditor = new PacketAuditor(config);
auditor.start();   // opens the SQLite store; safe to call once
```

To use the bundled Minecraft binding (curated adapters + optional auto-discovery)
in one call:

```java
import com.packetauditor.mc.PacketAuditorBootstrap;

PacketAuditorBootstrap boot = new PacketAuditorBootstrap(config)
    .start(/* selfDiscover = */ true);
PacketAuditor auditor = boot.auditor();
```

---

## 2. Connect the networking interception layer

The module intercepts packets by injecting a Netty `ChannelHandler` into the
client `Connection`'s pipeline, just before the vanilla `packet_handler`.

**Option A — explicit (recommended):** call the injector when a connection opens.

```java
import com.packetauditor.mc.net.PacketAuditorInjector;
import com.packetauditor.mc.codec.StreamCodecBridge;

StreamCodecBridge codec = new StreamCodecBridge(); // optional: for raw bytes / sizes
PacketAuditorInjector.inject(connection, auditor, codec);
// On disconnect the handler auto-detaches (channelInactive); to remove early:
PacketAuditorInjector.remove(channel);
```

- `inject(Connection, ...)` resolves the Netty `Channel` from the connection
  (26.2 field names are stable; a reflective fallback scans for the `Channel`
  field).
- The handler calls `auditor.handleInbound(...)` / `auditor.handleOutbound(...)`
  and honours the returned `CaptureResult` (forward / forward-replacement /
  suppress). It never blocks the event loop.

**Option B — self-discovery:** `PacketAuditorBootstrap.start(true)` runs a watcher
that injects automatically when a connection appears (uses
`Minecraft.getInstance().getConnection().getConnection()`).

### Precise packet ids / sizes / raw bytes (optional)

Edits are forwarded as native packet objects, so **Minecraft's own encoder
serializes them** — you do **not** need the codec bridge just to edit/forward.
The `StreamCodecBridge` is only needed for the raw-bytes viewer and exact sizing.
Provide a resolver that serializes with the connection's current protocol codec:

```java
codec.setResolver(new StreamCodecBridge.Resolver() {
    public byte[] encode(Object packet, Direction dir, ProtocolPhase phase) { ... }
    public Object decode(byte[] body, Direction dir, ProtocolPhase phase)   { ... }
    public int packetId(Object packet, Direction dir, ProtocolPhase phase)  { ... }
});
```

If you don't set a resolver, sizes show as unknown and the raw view is disabled —
everything else works.

---

## 3. Register the module toggle (Insert key)

```java
// In your key handler / module system:
if (keyCode == auditor.toggleKey()) {   // default Insert (GLFW_KEY_INSERT = 260)
    auditor.toggleOverlay();
}
// Or bind it to a module on/off:
auditor.setOverlayVisible(moduleEnabled);
```

The overlay is designed to remain usable while the inventory and other in-game
GUIs are open (render it from your HUD/overlay pass — see step 5).

---

## 4. Connect your existing settings (optional)

If you want the module's settings backed by your own `Settings.java`, implement
`SettingsBridge` and pass it in `AuditorConfig`. All methods have defaults, so
implement only what you need:

```java
AuditorConfig.builder().settingsBridge(new SettingsBridge() {
    public boolean getBoolean(String key, boolean def) { return mySettings.getBool(key, def); }
    public void setBoolean(String key, boolean v)       { mySettings.setBool(key, v); }
    // ... getString/getInt/getDouble as desired
}).build();
```

If you don't supply a bridge, the module uses its own encrypted JSON store.

---

## 5. Adapt the GUI renderer

The UI is a framework-free component tree painted through the `Renderer`
interface. You have two options:

### 5a. Use the bundled premium GUI (fastest)
A ready-made, high-contrast **premium** overlay + full dashboard is included and
renders through Minecraft's `GuiGraphics`. The dashboard is **tabbed** to stay
uncluttered — **Packets / Inspect+Edit / Queue / Traffic / Anomalies / Rules /
Tools** — with a right-click **action menu** (inspect / edit / intercept / delay /
discard), **inline field editing** (type a value → Preview → Save → optional
*Apply to all*), interception-queue management, a **Rules** tab (toggle/delete
active editing & delay rules, clear intercept/discard), and a **Tools** tab with
safety toggles, theme cycling, history export and **sliders** for simulated-loss %
and bandwidth throttle. The window is **draggable** (header) and **resizable**
(bottom-right grip). It renders **over the inventory and other screens**, and
automatically switches to a **compact docked layout** (not full-screen) when a
Minecraft screen is open.

```java
import com.packetauditor.mc.render.McGuiHost;

McGuiHost gui = new McGuiHost(auditor,
    seq -> {}, seq -> {});              // optional extra select / menu hooks

// In your HUD/overlay render pass (call it even while a screen is open, so it
// draws over the inventory; it auto-compacts in that case):
public void onRenderOverlay(GuiGraphics graphics) {
    gui.render(graphics);               // dashboard when toggled on, else compact overlay; plus toasts
}

// Forward input (route these even when a container screen is focused):
gui.mouseMoved(mx, my);
gui.mouseClicked(mx, my, button);       // left = select/release, right = action menu/discard
gui.mouseScrolled(mx, my, delta);
gui.charTyped(codepoint);               // for the inline editor text fields
gui.keyPressed(keyCode);                // Insert toggles the dashboard automatically
```

### 5b. Bring your own GUI (recommended if you have one)
The whole UI is backend-agnostic. Implement `com.packetauditor.ui.Renderer`
against your own draw API and render the **same** views — no Minecraft types
involved. See **`examples/CustomGuiExample.java`** for a complete skeleton:

```java
AuditorUiDataSource data = new AuditorUiDataSource(auditor);     // read-side
AuditorUiActions   actions = new AuditorUiActions(auditor, onSelect, onActionMenu); // write-side
DashboardView dashboard = new DashboardView(data, actions);
OverlayView   overlay   = new OverlayView(data).anchorTopLeft(4, 4);
ToastOverlay  toasts    = new ToastOverlay();
auditor.addNotificationListener(n -> toasts.push(n.level().name(), n.title(), n.message()));

// per frame, with YOUR renderer:
dashboard.render(myRenderer, data.theme());
toasts.render(myRenderer, data.theme());
```

Only `McRenderer`/`McGuiHost` touch Minecraft; the UI model, views, backend and
state are shared and reusable. Themes (incl. **Premium**, Dark, Light, Cyberpunk,
Monochrome, Night, Lavender, Toxic‑Green, Deep‑Ocean), gradients and
glass‑morphism all work with any renderer.

---

## 6. Connect existing GUI input handling

Translate your input events into `com.packetauditor.ui.InputEvent` and dispatch
them to the UI root:

```java
myUiRoot.onEvent(InputEvent.mouseMove(mouseX, mouseY));
myUiRoot.onEvent(InputEvent.mouseDown(mouseX, mouseY, button)); // right-click opens the action menu
myUiRoot.onEvent(InputEvent.scroll(mouseX, mouseY, delta));
myUiRoot.onEvent(InputEvent.charTyped(codepoint));
myUiRoot.onEvent(InputEvent.keyDown(keyCode));
```

The persistent packet list (`ScrollListView`) reports left-click as *select* and
right-click as *activate* (open the per-packet action menu: inspect / edit /
change-all / intercept / delay / discard).

---

## 7. Configure storage paths

There are **no hardcoded paths**. Provide a base directory; the module derives
`cfg/`, `db/`, `bak/` under it:

```java
AuditorConfig.builder().baseDir(Paths.get(myClientDir, "packet-auditor")).build();
```

If you omit `baseDir`, an obscured non-obvious default under the user home is
used. Prefer injecting an explicit path.

---

## 8. Lifecycle & threading

- `auditor.start()` once at init; `auditor.stop()` on shutdown (closes the store,
  stops background workers and the queue timeout sweeper).
- `attach(...)` / `detach()` happen automatically via the injected handler on
  channel active/inactive; you can also call them directly.
- All decoding, anomaly analysis and disk I/O run on the module's background
  worker — never on the Netty or main thread. Held/delayed packets are re-injected
  on the channel's event loop to preserve ordering and thread-affinity.

---

## 9. Connection monitoring events (optional)

```java
auditor.addConnectionListener(ev -> myDashboard.onConnEvent(ev));
// From your integration, emit richer events if you have the data:
auditor.fireConnection(ConnectionEvent.of(ConnectionEventType.SERVER_ADDRESS, "play.example.net"));
auditor.fireConnection(new ConnectionEvent(ConnectionEventType.LATENCY, "ping", 42));
```

---

## 10. Packet hooks & helpers (optional)

```java
// Observe/transform at the boundary (runs on the networking thread — keep cheap):
auditor.addHook(new PacketHook() {
    public Object onPreSend(Object pkt, Direction d) { return pkt; }   // return null to drop
    public void onPostReceive(Object pkt, Direction d) { /* observe */ }
});

// Simple helpers:
auditor.editPacketField(sequence, "y", "0");                 // manual structured edit
auditor.changeAllMatching("ServerboundMovePlayerPacket$Pos", "y", "0"); // auto rule
auditor.interceptMatching(MatchSpec.builder().packetNameEquals("...").build());
auditor.discardMatching(MatchSpec.builder().packetNameEquals("...").build());
```

Protection applies to all of these: in Normal mode non-essential edits require
**Advanced Mode**; essential packets require the **Unlock ALL** toggle.

---

## 11. Using it as a Java agent (optional)

If you prefer the "just attach the agent" path, the manifest entrypoint
`com.packetauditor.mc.PacketAuditorAgent` supports `premain`/`agentmain` and
starts the module with self-discovery. If your client already drives modules,
skip this and construct `PacketAuditor` yourself.

## 12. Save-change notifications & crash-safe edits

Packet editing is fully crash-safe: a faulty edit can **never** break the
connection — worst case the original packet is forwarded unchanged and a clear
error is reported. Every edit emits a notification you can toast:

```java
auditor.addNotificationListener(n ->
    myToast(n.level(), n.title(), n.message())); // SUCCESS / WARN / ERROR / INFO

EditResult preview = auditor.previewPacketEdit(seq, "y", "0"); // dry-run; warn before saving
if (!preview.applied()) warn(preview.message());
EditResult saved = auditor.editPacketField(seq, "y", "0");     // emits a save toast
```

The bundled `McGuiHost` / `ToastOverlay` already wire this up.

## 13. Screen-capture exclusion (Windows)

To exclude the Minecraft window (and this overlay) from screenshots / screen
recording via `WDA_EXCLUDEFROMCAPTURE`:

```java
AuditorConfig.builder().excludeFromCapture(true).build(); // applied on bootstrap start
// or directly, any time after the window exists:
com.packetauditor.mc.render.CaptureExclusion.excludeFromCapture();
com.packetauditor.mc.render.CaptureExclusion.allowCapture();   // re-enable
```

Implemented with the Java 25 Foreign Function API (no JNA) + LWJGL's
`GLFWNativeWin32`. It is a safe no-op on non-Windows platforms.
