package io.dscope.utils.crypto;

import java.util.HashMap;
import java.util.Map;

public class CloudEncryptFactory {
    public static CloudEncryptor create(String provider, Map<String, String> config) {
        String p = provider == null ? "" : provider.toLowerCase();
        if (config == null) config = new HashMap<>();
        switch (p) {
            case "aws": return new AwsEncryptor(config);
            case "azure": return new AzureEncryptor(config);
            case "gcp": return new GcpEncryptor(config);
            case "oci": return new OciEncryptor(config);
            default: throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }
}
