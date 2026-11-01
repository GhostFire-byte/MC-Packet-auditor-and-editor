package com.packetauditor.store;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Helpers for building {@link KeyProvider}s and performing AES-GCM encryption of
 * profile/config bytes at rest (D9).
 *
 * <p>Wire format of an encrypted blob: {@code [1-byte version][12-byte IV][ciphertext+tag]}.</p>
 */
public final class ProfileCrypto {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int PBKDF2_KEY_BITS = 256;

    private static final SecureRandom RNG = new SecureRandom();

    private final KeyProvider keyProvider;

    public ProfileCrypto(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /** True if encryption is active (a key is available). */
    public boolean isEnabled() {
        byte[] k = keyProvider == null ? null : keyProvider.keyBytes();
        return k != null && k.length > 0;
    }

    /** Encrypt plaintext; if no key is configured, returns the plaintext unchanged. */
    public byte[] encrypt(byte[] plaintext) {
        byte[] key = keyProvider == null ? null : keyProvider.keyBytes();
        if (key == null || key.length == 0) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(normalize(key), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[1 + IV_LENGTH + ct.length];
            out[0] = VERSION;
            System.arraycopy(iv, 0, out, 1, IV_LENGTH);
            System.arraycopy(ct, 0, out, 1 + IV_LENGTH, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Profile encryption failed", e);
        }
    }

    /** Decrypt a blob produced by {@link #encrypt(byte[])}. */
    public byte[] decrypt(byte[] blob) {
        byte[] key = keyProvider == null ? null : keyProvider.keyBytes();
        if (key == null || key.length == 0) {
            return blob;
        }
        if (blob.length < 1 + IV_LENGTH || blob[0] != VERSION) {
            // Not an encrypted blob (e.g. legacy plaintext); return as-is.
            return blob;
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, 1, 1 + IV_LENGTH);
            byte[] ct = Arrays.copyOfRange(blob, 1 + IV_LENGTH, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(normalize(key), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Profile decryption failed (wrong key or corrupt data)", e);
        }
    }

    /** Normalise arbitrary key bytes to a valid AES key length (16/24/32). */
    private static byte[] normalize(byte[] key) {
        if (key.length == 16 || key.length == 24 || key.length == 32) {
            return key;
        }
        // Fold/pad to 32 bytes deterministically.
        byte[] out = new byte[32];
        for (int i = 0; i < key.length; i++) {
            out[i % 32] ^= key[i];
        }
        return out;
    }

    // ---- KeyProvider factories ----

    /** Use raw key bytes supplied by the host (strongest). */
    public static KeyProvider hostKey(byte[] key) {
        byte[] copy = key == null ? null : key.clone();
        return () -> copy;
    }

    /** Derive a key from a passphrase via PBKDF2-HMAC-SHA256. */
    public static KeyProvider passphrase(char[] passphrase, byte[] salt) {
        return () -> {
            if (passphrase == null || passphrase.length == 0) {
                return null;
            }
            try {
                SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] s = salt != null ? salt
                        : "packet-auditor-static-salt".getBytes(StandardCharsets.UTF_8);
                PBEKeySpec spec = new PBEKeySpec(passphrase, s, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
                return f.generateSecret(spec).getEncoded();
            } catch (Exception e) {
                throw new IllegalStateException("PBKDF2 key derivation failed", e);
            }
        };
    }

    /** Convenience: key from a passphrase string with the default salt. */
    public static KeyProvider passphrase(String passphrase) {
        return passphrase(passphrase == null ? null : passphrase.toCharArray(), null);
    }

    /** Derive a weak-but-stable key from machine/user identifiers (fallback). */
    public static KeyProvider machineDerived() {
        return () -> {
            String seed = System.getProperty("user.name", "") + "|"
                    + System.getProperty("os.name", "") + "|"
                    + System.getProperty("user.home", "");
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                return md.digest(seed.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return seed.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    /**
     * Compose providers: use the first that yields a non-null key. Implements the
     * D9 strategy "host primary + passphrase/machine fallback".
     */
    public static KeyProvider firstAvailable(KeyProvider... providers) {
        return () -> {
            if (providers != null) {
                for (KeyProvider p : providers) {
                    if (p == null) {
                        continue;
                    }
                    byte[] k = p.keyBytes();
                    if (k != null && k.length > 0) {
                        return k;
                    }
                }
            }
            return null;
        };
    }
}
