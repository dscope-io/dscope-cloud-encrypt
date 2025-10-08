package io.dscope.utils.crypto;

import java.util.Locale;
import java.util.Objects;

/**
 * Convenience API for encrypting and decrypting secrets with the supported cloud KMS providers.
 */
public class CloudKmsClient {

    /**
     * Decrypts a ciphertext that may optionally be wrapped in {@code ENC(...)}.
     *
     * @param config     provider configuration
     * @param ciphertext encrypted value, optionally wrapped with ENC(...)
     * @return plaintext secret
     */
    public String decryptValue(CloudKmsConfig config, String ciphertext) throws Exception {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(ciphertext, "ciphertext");

        String payload = unwrap(ciphertext);
        CloudDecryptor decryptor = CloudDecryptFactory.create(config.getProvider(), config.asMutableMap());
        return decryptor.decrypt(payload);
    }

    /**
     * Encrypts a plaintext value and optionally wraps it in {@code ENC(...)}.
     */
    public String encryptValue(CloudKmsConfig config, String plaintext, boolean wrap) throws Exception {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plaintext, "plaintext");
        CloudEncryptor encryptor = CloudEncryptFactory.create(config.getProvider(), config.asMutableMap());
        String ciphertext = encryptor.encrypt(plaintext);
        return wrap ? "ENC(" + ciphertext + ")" : ciphertext;
    }

    private String unwrap(String value) {
        String trimmed = value.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("ENC(") && trimmed.endsWith(")")) {
            return trimmed.substring(4, trimmed.length() - 1);
        }
        return trimmed;
    }
}
