package io.dscope.cloud.secret;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Common abstraction for storing and retrieving encrypted payloads in cloud-native secret
 * management services. Implementations are expected to persist both the ciphertext and any
 * associated metadata required for decryption.
 */
public interface CloudSecretStorageService extends AutoCloseable {

    void putSecret(String name, byte[] data, Map<String, String> metadata) throws Exception;

    SecretRecord getSecret(String name) throws Exception;

    void deleteSecret(String name) throws Exception;

    default byte[] getSecretBytes(String name) throws Exception {
        return getSecret(name).data();
    }

    @Override
    default void close() throws Exception {
        // default no-op
    }

    record SecretRecord(byte[] data, Map<String, String> metadata) {
        public SecretRecord {
            data = data != null ? data.clone() : new byte[0];
            metadata = metadata != null ? Collections.unmodifiableMap(new HashMap<>(metadata)) : Map.of();
        }
    }
}
