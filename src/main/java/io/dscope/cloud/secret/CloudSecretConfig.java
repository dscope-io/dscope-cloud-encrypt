package io.dscope.cloud.secret;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration describing which secret manager provider to use and the provider specific
 * settings required to connect to it.
 */
public final class CloudSecretConfig {

    private final String provider;
    private final Map<String, String> settings;

    private CloudSecretConfig(String provider, Map<String, String> settings) {
        this.provider = Objects.requireNonNull(provider, "provider").toLowerCase();
        this.settings = Collections.unmodifiableMap(new HashMap<>(settings));
    }

    public String getProvider() {
        return provider;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public String getRequired(String key) {
        String value = settings.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required secret manager setting: " + key);
        }
        return value;
    }

    public static CloudSecretConfig of(String provider, Map<String, String> settings) {
        return new CloudSecretConfig(provider, settings != null ? settings : Map.of());
    }

    public static Builder builder(String provider) {
        return new Builder(provider);
    }

    public static final class Builder {
        private final String provider;
        private final Map<String, String> values = new HashMap<>();

        private Builder(String provider) {
            this.provider = Objects.requireNonNull(provider, "provider").toLowerCase();
        }

        public Builder with(String key, String value) {
            if (key != null && value != null && !value.isBlank()) {
                values.put(key, value);
            }
            return this;
        }

        public CloudSecretConfig build() {
            return new CloudSecretConfig(provider, values);
        }
    }
}
