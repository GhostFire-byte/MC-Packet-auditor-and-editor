package com.packetauditor.mc.render;

import com.packetauditor.util.AuditorLog;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFWNativeWin32;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Windows-only screen-capture exclusion (user request): applies
 * {@code WDA_EXCLUDEFROMCAPTURE} to the Minecraft window so the client (and this
 * overlay) is excluded from screenshots and screen recordings/streams.
 *
 * <p>Implemented with the Java 25 Foreign Function &amp; Memory API (no JNA
 * dependency): it resolves the native window handle via LWJGL's
 * {@code GLFWNativeWin32} and calls {@code user32!SetWindowDisplayAffinity}. On
 * non-Windows platforms (or if anything is unavailable) it is a safe no-op.</p>
 */
public final class CaptureExclusion {

    private static final AuditorLog LOG = AuditorLog.of(CaptureExclusion.class);

    /** WDA_EXCLUDEFROMCAPTURE (Windows 10 2004+). */
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
    /** WDA_NONE — disable exclusion. */
    private static final int WDA_NONE = 0x00000000;

    private CaptureExclusion() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Exclude the Minecraft window from capture. @return true on success. */
    public static boolean excludeFromCapture() {
        return setAffinity(WDA_EXCLUDEFROMCAPTURE);
    }

    /** Re-enable capture of the Minecraft window. @return true on success. */
    public static boolean allowCapture() {
        return setAffinity(WDA_NONE);
    }

    private static boolean setAffinity(int affinity) {
        if (!isWindows()) {
            LOG.debug("Capture exclusion is Windows-only; skipping on this OS.");
            return false;
        }
        try {
            long glfwWindow = Minecraft.getInstance().getWindow().getWindow();
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
            if (hwnd == 0L) {
                LOG.warn("Could not resolve native window handle; capture exclusion skipped.");
                return false;
            }

            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            MethodHandle setWindowDisplayAffinity = linker.downcallHandle(
                    user32.find("SetWindowDisplayAffinity").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,     // BOOL
                            ValueLayout.JAVA_LONG,                   // HWND
                            ValueLayout.JAVA_INT));                  // DWORD affinity

            int result = (int) setWindowDisplayAffinity.invoke(hwnd, affinity);
            boolean ok = result != 0;
            if (ok) {
                LOG.info("SetWindowDisplayAffinity(0x" + Integer.toHexString(affinity) + ") applied.");
            } else {
                LOG.warn("SetWindowDisplayAffinity returned FALSE (unsupported Windows build?).");
            }
            return ok;
        } catch (Throwable t) {
            LOG.warn("Capture exclusion failed: " + t.getMessage());
            return false;
        }
    }
}
