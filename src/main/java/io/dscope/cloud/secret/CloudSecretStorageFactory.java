package io.dscope.cloud.secret;

import com.oracle.bmc.Region;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Factory that instantiates the appropriate {@link CloudSecretStorageService} for the configured
 * cloud provider.
 */
public final class CloudSecretStorageFactory {

    private CloudSecretStorageFactory() {
    }

    public static CloudSecretStorageService create(CloudSecretConfig config) throws Exception {
        Objects.requireNonNull(config, "config");
        String provider = config.getProvider().toLowerCase(Locale.ROOT);
        Map<String, String> settings = config.getSettings();
        return switch (provider) {
            case "gcp", "google" -> new GcpSecretStorageService(config.getRequired("project"));
            case "aws", "amazon" -> new AwsSecretStorageService(config.getRequired("region"));
            case "azure" -> new AzureSecretStorageService(config.getRequired("vaultUrl"));
            case "oci", "oracle" -> new OciSecretStorageService(settings);
            case "memory", "local" -> new InMemorySecretStorageService();
            default -> throw new IllegalArgumentException("Unsupported secret storage provider: " + provider);
        };
    }

    static Region resolveOciRegion(Map<String, String> settings) {
        String region = settings != null ? settings.get("region") : null;
        if (region == null || region.isBlank()) {
            return null;
        }
        return Region.fromRegionId(region.trim().toLowerCase(Locale.ROOT));
    }
}
