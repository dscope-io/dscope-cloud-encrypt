package io.dscope.utils.crypto;

import io.dscope.cloud.secret.CloudSecretConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CloudEncryptCLITest {

    @Test
    void buildKmsConfigMergesOverrides() {
        Map<String, String> base = new LinkedHashMap<>();
        base.put("region", "us-west-2");
        base.put("keyId", "alias/base");

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("keyId", "alias/override");
        overrides.put("context", "tenant-123");

        CloudKmsConfig cfg = CloudEncryptCLI.buildKmsConfig("aws", base, overrides);

        assertEquals("aws", cfg.getProvider());
        assertEquals("us-west-2", cfg.getSettings().get("region"));
        assertEquals("alias/override", cfg.getSettings().get("keyId"));
        assertEquals("tenant-123", cfg.getSettings().get("context"));
    }

    @Test
    void upsertPropertyReplacesExistingValue() {
        List<String> lines = new ArrayList<>(List.of(
                "FOO=bar",
                "HELLO=world"
        ));

        List<String> updated = CloudEncryptCLI.upsertProperty(lines, "HELLO", "ENC(new)");

        assertEquals(2, updated.size());
        assertEquals("HELLO=ENC(new)", updated.get(1));
    }

    @Test
    void upsertPropertyAppendsWhenMissing() {
        List<String> lines = new ArrayList<>(List.of(
                "FOO=bar"
        ));

        List<String> updated = CloudEncryptCLI.upsertProperty(lines, "HELLO", "ENC(new)");

        assertEquals(2, updated.size());
        assertEquals("HELLO=ENC(new)", updated.get(1));
    }

    @Test
    void buildSecretConfigMergesOverrides() {
        Map<String, String> base = new LinkedHashMap<>();
        base.put("project", "base-project");
        base.put("region", "us-central1");

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("region", "europe-west1");
        overrides.put("label", "prod");

        CloudSecretConfig secretConfig = CloudEncryptCLI.buildSecretConfig("gcp", base, overrides);

        assertEquals("gcp", secretConfig.getProvider());
        assertEquals("base-project", secretConfig.getSettings().get("project"));
        assertEquals("europe-west1", secretConfig.getSettings().get("region"));
        assertEquals("prod", secretConfig.getSettings().get("label"));
    }
}
