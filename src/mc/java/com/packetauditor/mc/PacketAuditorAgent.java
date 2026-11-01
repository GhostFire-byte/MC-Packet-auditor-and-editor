package com.packetauditor.mc;

import com.packetauditor.api.AuditorConfig;

import java.lang.instrument.Instrumentation;

/**
 * Optional Java-agent entrypoint (D-context: the host uses a Runtime Java Agent /
 * Dynamic Attach). Supports both {@code -javaagent} (premain) and runtime attach
 * ({@code agentmain}). It starts the module with self-discovery enabled so that
 * simply attaching the agent begins auditing once a connection is open.
 *
 * <p>If you already drive the module from your own module system, you do not need
 * this entrypoint — construct {@link com.packetauditor.api.PacketAuditor} yourself.</p>
 *
 * <p>Manifest attributes required to use this as an agent:</p>
 * <pre>
 * Premain-Class: com.packetauditor.mc.PacketAuditorAgent
 * Agent-Class:   com.packetauditor.mc.PacketAuditorAgent
 * Can-Retransform-Classes: true
 * </pre>
 */
public final class PacketAuditorAgent {

    private static volatile PacketAuditorBootstrap bootstrap;

    private PacketAuditorAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        launch(args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        launch(args);
    }

    private static synchronized void launch(String args) {
        if (bootstrap != null) {
            return;
        }
        AuditorConfig config = AuditorConfig.builder().build();
        bootstrap = new PacketAuditorBootstrap(config).start(true);
    }

    /** Access the running bootstrap (may be null if not launched via the agent). */
    public static PacketAuditorBootstrap bootstrap() {
        return bootstrap;
    }
}
