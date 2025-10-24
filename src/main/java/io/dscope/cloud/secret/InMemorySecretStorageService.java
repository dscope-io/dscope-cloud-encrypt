package io.dscope.cloud.secret;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation primarily used for tests and local development.
 */
public class InMemorySecretStorageService implements CloudSecretStorageService {

    private final Map<String, SecretRecord> store = new ConcurrentHashMap<>();

    @Override
    public void putSecret(String name, byte[] data, Map<String, String> metadata) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        Map<String, String> meta = metadata != null ? metadata : Collections.emptyMap();
        store.put(name, new SecretRecord(data, meta));
    }

    @Override
    public SecretRecord getSecret(String name) {
        Objects.requireNonNull(name, "name");
        SecretRecord record = store.get(name);
        if (record == null) {
            throw new IllegalArgumentException("Secret not found: " + name);
        }
        return record;
    }

    @Override
    public void deleteSecret(String name) {
        Objects.requireNonNull(name, "name");
        store.remove(name);
    }
}
