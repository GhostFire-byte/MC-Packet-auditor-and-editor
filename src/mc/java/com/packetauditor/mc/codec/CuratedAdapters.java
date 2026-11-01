package com.packetauditor.mc.codec;

import com.packetauditor.codec.DecoderService;
import com.packetauditor.codec.RecordRebuildAdapter;
import com.packetauditor.util.AuditorLog;

/**
 * Registers curated editable adapters for the prioritized Minecraft 26.2 packet
 * families (D7 priority: movement/position, attack/entity interaction, inventory
 * clicks, chat, container/GUI). Each candidate class is resolved by name and
 * registered via {@link RecordRebuildAdapter} only if it is present and is a
 * record, so the set degrades gracefully across builds.
 */
public final class CuratedAdapters {

    private static final AuditorLog LOG = AuditorLog.of(CuratedAdapters.class);

    /** Candidate record packet classes to make editable (prioritized families). */
    private static final String[] CANDIDATES = {
            // Movement / position
            "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos",
            "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot",
            "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot",
            "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$StatusOnly",
            "net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket",
            "net.minecraft.network.protocol.game.ServerboundPlayerInputPacket",
            // Attack / entity interaction
            "net.minecraft.network.protocol.game.ServerboundInteractPacket",
            "net.minecraft.network.protocol.game.ServerboundSwingPacket",
            // Player actions
            "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket",
            "net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket",
            "net.minecraft.network.protocol.game.ServerboundUseItemPacket",
            "net.minecraft.network.protocol.game.ServerboundUseItemOnPacket",
            // Chat
            "net.minecraft.network.protocol.game.ServerboundChatPacket",
            "net.minecraft.network.protocol.game.ServerboundChatCommandPacket",
            // Inventory / GUI
            "net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket",
            "net.minecraft.network.protocol.game.ServerboundContainerClosePacket",
            "net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket",
            // Clientbound world/entity updates
            "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket",
            "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
            "net.minecraft.network.protocol.game.ClientboundSetTimePacket",
            "net.minecraft.network.protocol.game.ClientboundSetHealthPacket"
    };

    private CuratedAdapters() {
    }

    /**
     * Register editable adapters for all available candidate record packets.
     *
     * @return the number of adapters registered.
     */
    public static int registerDefaults(DecoderService decoder) {
        int count = 0;
        for (String className : CANDIDATES) {
            try {
                Class<?> type = Class.forName(className);
                if (type.isRecord()) {
                    decoder.registerAdapter(RecordRebuildAdapter.forClass(type));
                    count++;
                } else {
                    LOG.debug("Skipping non-record packet (inspect-only): " + className);
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("Packet class not present in this build: " + className);
            } catch (Throwable t) {
                LOG.debug("Could not register adapter for " + className + ": " + t.getMessage());
            }
        }
        LOG.info("Registered " + count + " curated editable packet adapters.");
        return count;
    }
}
