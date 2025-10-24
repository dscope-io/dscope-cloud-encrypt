package io.dscope.utils.crypto;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class OciKmsSupport {

    private static final String DEFAULT_PROFILE = "DEFAULT";

    private OciKmsSupport() {
    }

    public static AuthenticationDetailsProvider authenticationProvider(Map<String, String> config) {
        String profile = valueOrDefault(config, "profile", DEFAULT_PROFILE);
        String configFile = valueOrDefault(config, "configFile", null);
        try {
            ConfigFileReader.ConfigFile file = configFile != null && !configFile.isBlank()
                    ? ConfigFileReader.parse(configFile, profile)
                    : ConfigFileReader.parseDefault(profile);
            return new ConfigFileAuthenticationDetailsProvider(file);
    } catch (IOException e) {
            throw new IllegalStateException("Unable to load OCI configuration: " + e.getMessage(), e);
        }
    }

    static String resolveEndpoint(Map<String, String> config) {
        String endpoint = valueOrDefault(config, "endpoint", null);
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint;
        }
        String region = valueOrDefault(config, "region", null);
        String vault = valueOrDefault(config, "vault", null);
        if ((vault == null || vault.isBlank())) {
            vault = valueOrDefault(config, "vaultName", null);
        }
        if (region != null && !region.isBlank() && vault != null && !vault.isBlank()) {
            return String.format("https://%s-crypto.kms.%s.oraclecloud.com", vault.trim(), region.trim().toLowerCase(Locale.ROOT));
        }
        throw new IllegalArgumentException("OCI KMS endpoint not configured. Set kms.endpoint or provide region+vault.");
    }

    static String requireKeyId(Map<String, String> config) {
        String keyId = valueOrDefault(config, "keyId", null);
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("OCI keyId is required. Provide kms.keyId in configuration.");
        }
        return keyId;
    }

    private static String valueOrDefault(Map<String, String> config, String key, String fallback) {
        if (config == null) {
            return fallback;
        }
        return Objects.toString(config.get(key), fallback);
    }
}
