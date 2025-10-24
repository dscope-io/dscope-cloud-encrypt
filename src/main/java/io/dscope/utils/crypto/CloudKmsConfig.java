package io.dscope.utils.crypto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration holder for cloud KMS providers used by {@link CloudKmsClient}.
 */
public final class CloudKmsConfig {

    private final String provider;
    private final Map<String, String> settings;

    private CloudKmsConfig(String provider, Map<String, String> settings) {
        this.provider = Objects.requireNonNull(provider, "provider").toLowerCase();
        this.settings = Collections.unmodifiableMap(new HashMap<>(settings));
    }

    public String getProvider() {
        return provider;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    /**
     * Returns a mutable copy of the provider settings. Callers can safely modify the returned map
     * without affecting this configuration instance.
     */
    public Map<String, String> toSettings() {
        return new HashMap<>(settings);
    }

    Map<String, String> asMutableMap() {
        return new HashMap<>(settings);
    }

    public static CloudKmsConfig of(String provider, Map<String, String> settings) {
        return new CloudKmsConfig(provider, settings);
    }

    public static Builder builder(String provider) {
        return new Builder(provider);
    }

    public static CloudKmsConfig forAws(String region, String keyId) {
        return builder("aws")
                .with("region", region)
                .with("keyId", keyId)
                .build();
    }

    public static CloudKmsConfig forAzure(String keyIdentifier) {
        return builder("azure")
                .with("keyId", keyIdentifier)
                .build();
    }

    public static CloudKmsConfig forGcp(String projectId, String locationId, String keyRingId, String keyId) {
        return builder("gcp")
                .with("project", projectId)
                .with("location", locationId)
                .with("keyRing", keyRingId)
                .with("key", keyId)
                .build();
    }

    public static final class Builder {
        private final String provider;
        private final Map<String, String> values = new HashMap<>();

        private Builder(String provider) {
            this.provider = Objects.requireNonNull(provider, "provider").toLowerCase();
        }

        public Builder with(String key, String value) {
            if (value != null && !value.isBlank()) {
                values.put(key, value);
            }
            return this;
        }

        public CloudKmsConfig build() {
            return new CloudKmsConfig(provider, values);
        }
    }
}
