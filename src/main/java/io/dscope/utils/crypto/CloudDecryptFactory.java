package io.dscope.utils.crypto;

import java.util.HashMap;
import java.util.Map;

public class CloudDecryptFactory {
    public static CloudDecryptor create(String provider, Map<String, String> config) {
        String p = provider == null ? "" : provider.toLowerCase();
        if (config == null) config = new HashMap<>();
        switch (p) {
            case "aws": return new AwsDecryptor(config);
            case "azure": return new AzureDecryptor(config);
            case "gcp": return new GcpDecryptor(config);
            case "oci": return new OciDecryptor(config);
            default: throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }
}
