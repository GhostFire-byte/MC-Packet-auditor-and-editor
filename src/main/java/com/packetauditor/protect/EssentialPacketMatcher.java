package com.packetauditor.protect;

import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides whether a packet is a Tier-1 "essential connection-integrity" packet.
 *
 * <p>Minecraft 26.2 ships readable class names, so classification is done by
 * matching the (simple) packet class name against a configurable set of
 * substrings, combined with always-essential protocol phases. The default set
 * covers KeepAlive, Ping/Pong, the login/encryption handshake, compression,
 * configuration finish, and disconnect packets — anything that silently breaks
 * or desyncs the connection if altered, held, delayed, or dropped.</p>
 *
 * <p>The set is additive and tweakable at runtime, but the hard-core liveness
 * markers ("KeepAlive", "Ping", "Pong") are always present.</p>
 */
public final class EssentialPacketMatcher {

    /** Markers that can never be removed (connection liveness). */
    private static final Set<String> HARD_LOCKED = Set.of(
            "KeepAlive", "Ping", "Pong"
    );

    /** Default Tier-1 markers (case-insensitive substring match on class name). */
    public static final Set<String> DEFAULT_MARKERS = Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
            "KeepAlive",
            "Ping",
            "Pong",
            "Hello",                 // login/encryption hello (both directions)
            "Key",                   // ServerboundKey (encryption response)
            "EncryptionRequest",
            "EncryptionResponse",
            "LoginAcknowledged",
            "LoginFinished",
            "GameProfile",
            "SetCompression",
            "LoginCompression",
            "Intention",             // ClientIntentionPacket (handshake)
            "Handshake",
            "FinishConfiguration",
            "ConfigurationAcknowledged",
            "SelectKnownPacks",
            "Disconnect",
            "CookieRequest",
            "CookieResponse",
            "Transfer"
    )));

    private final Set<String> markers;

    public EssentialPacketMatcher() {
        this(DEFAULT_MARKERS);
    }

    public EssentialPacketMatcher(Set<String> markers) {
        LinkedHashSet<String> m = new LinkedHashSet<>(HARD_LOCKED);
        if (markers != null) {
            m.addAll(markers);
        }
        this.markers = Collections.unmodifiableSet(m);
    }

    /** The active marker set (read-only; always contains the hard-locked ones). */
    public Set<String> markers() {
        return markers;
    }

    /** True if the given packet is Tier-1 essential. */
    public boolean isEssential(PacketMeta meta) {
        if (meta == null) {
            return false;
        }
        // The entire handshake and login phases are integrity-critical.
        ProtocolPhase phase = meta.phase();
        if (phase == ProtocolPhase.HANDSHAKE || phase == ProtocolPhase.LOGIN) {
            return true;
        }
        String name = meta.packetName();
        String cls = meta.packetClass();
        for (String marker : markers) {
            if (containsIgnoreCase(name, marker) || containsIgnoreCase(cls, marker)) {
                return true;
            }
        }
        return false;
    }

    public ProtectionTier classify(PacketMeta meta) {
        return isEssential(meta) ? ProtectionTier.TIER1_ESSENTIAL : ProtectionTier.TIER2_STANDARD;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        final int n = needle.length();
        final int max = haystack.length() - n;
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, n)) {
                return true;
            }
        }
        return false;
    }
}
