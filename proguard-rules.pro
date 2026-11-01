# ProGuard rules for the Packet Auditor module (D9).
# Release-only, opt-in. Keeps the public, human-readable integration API that
# Codex / your client calls; obfuscates everything else.

-dontwarn net.minecraft.**
-dontwarn io.netty.**

# Keep the public integration API (names must stay readable for integration).
-keep public class com.packetauditor.api.** {
    public protected *;
}

# Keep enum internals used via valueOf / values().
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations and signatures useful for reflective decoding.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Obfuscate internal packages (default behaviour) but do not break Gson-mapped
# config DTOs that rely on field names — keep their fields.
-keepclassmembers class com.packetauditor.store.dto.** {
    <fields>;
}
