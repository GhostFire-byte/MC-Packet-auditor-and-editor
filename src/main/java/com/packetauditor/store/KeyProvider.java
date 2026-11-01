package com.packetauditor.store;

/**
 * Supplies the symmetric key material used to encrypt local profiles/config at
 * rest (D9). The host provides this via the integration API. Several strategies
 * are available:
 *
 * <ul>
 *   <li><b>Host key</b> — your client passes raw key bytes (strongest).</li>
 *   <li><b>Passphrase</b> — a user passphrase is stretched with PBKDF2.</li>
 *   <li><b>Machine-derived</b> — derived from stable machine/user identifiers
 *       (weakest; convenience fallback).</li>
 * </ul>
 *
 * <p>The key is only used for profile/config encryption; the bulk SQLite store is
 * left plaintext for performance (D9 scope decision).</p>
 */
@FunctionalInterface
public interface KeyProvider {

    /**
     * @return 16/24/32 raw key bytes for AES, or {@code null} to disable
     *         encryption (profiles stored as plaintext JSON).
     */
    byte[] keyBytes();
}
