package io.dscope.utils.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudKmsConfigTest {

    @Test
    void buildsAwsConfigWithDefaults() {
        CloudKmsConfig config = CloudKmsConfig.forAws("us-east-1", "arn:aws:kms:us-east-1:123:key/abc");
        assertEquals("aws", config.getProvider());
        assertEquals("us-east-1", config.getSettings().get("region"));
        assertEquals("arn:aws:kms:us-east-1:123:key/abc", config.getSettings().get("keyId"));
    }

    @Test
    void buildsAzureConfig() {
        CloudKmsConfig config = CloudKmsConfig.forAzure("https://vault.vault.azure.net/keys/key1");
        assertEquals("azure", config.getProvider());
        assertEquals("https://vault.vault.azure.net/keys/key1", config.getSettings().get("keyId"));
    }

    @Test
    void builderMaintainsCustomValues() {
        CloudKmsConfig config = CloudKmsConfig.builder("custom")
                .with("foo", "bar")
                .with("baz", "qux")
                .build();
        assertEquals("custom", config.getProvider());
        assertEquals(Map.of("foo", "bar", "baz", "qux"), config.getSettings());
    }

    @Test
    void toSettingsReturnsMutableCopy() {
        CloudKmsConfig config = CloudKmsConfig.forAws("us-east-1", "alias/test");
        Map<String, String> copy = config.toSettings();
        copy.put("region", "override");

        assertEquals("us-east-1", config.getSettings().get("region"));
        assertEquals("override", copy.get("region"));
    }

    @Test
    void rejectsNullProvider() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> CloudKmsConfig.of(null, Map.of()));
        assertEquals("provider", ex.getMessage());
    }
}
