// Illustrative usage snippet (not compiled as part of the build).
// Shows the end-to-end wiring described in docs/INTEGRATION.md.

import com.packetauditor.api.*;
import com.packetauditor.match.*;
import com.packetauditor.model.Direction;
import com.packetauditor.store.ProfileCrypto;
import com.packetauditor.mc.net.PacketAuditorInjector;
import com.packetauditor.mc.codec.StreamCodecBridge;
import com.packetauditor.mc.codec.CuratedAdapters;
import com.packetauditor.mc.render.McRenderer;

import java.nio.file.Paths;

public final class UsageExample {

    public static void main(String[] args) {
        // 1) Initialise with a host-provided storage dir + encryption key.
        AuditorConfig config = AuditorConfig.builder()
                .baseDir(Paths.get(System.getProperty("user.home"), ".myclient", "packet-auditor"))
                .encryptionKey(ProfileCrypto.firstAvailable(
                        ProfileCrypto.passphrase("change-me"),
                        ProfileCrypto.machineDerived()))
                .advancedMode(false)
                .guiFps(60)
                .logSink(System.out::println)
                .build();

        PacketAuditor auditor = new PacketAuditor(config);

        // 2) Register curated editable adapters for MC 26.2 record packets.
        CuratedAdapters.registerDefaults(auditor.decoder());
        auditor.start();

        // 3) When a connection opens (from your Netty integration):
        // PacketAuditorInjector.inject(connection, auditor, new StreamCodecBridge());

        // 4) Example: experiment safely.
        auditor.protection().setAdvancedMode(true);                 // allow editing non-essential
        auditor.changeAllMatching("ServerboundMovePlayerPacket$Pos", "y", "0");

        // 5) Intercept the next matching packets for manual release/edit/discard:
        auditor.interceptMatching(MatchSpec.builder()
                .packetNameContains("Interact")
                .direction(Direction.OUTBOUND)
                .build());

        // 6) Render the overlay each frame with the reference renderer:
        // McRenderer renderer = new McRenderer();
        // renderer.begin(guiGraphics);
        // myUiRoot.render(renderer, auditor.themes().current());

        // 7) Toggle with Insert:
        // if (keyCode == auditor.toggleKey()) auditor.toggleOverlay();

        // ... later ...
        auditor.stop();
    }
}
