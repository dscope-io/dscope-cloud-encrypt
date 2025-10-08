package io.dscope.utils.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OciKmsSupportTest {

    @Test
    void resolveEndpointPrefersExplicitValue() {
        String endpoint = OciKmsSupport.resolveEndpoint(Map.of("endpoint", "https://example.com"));
        assertEquals("https://example.com", endpoint);
    }

    @Test
    void resolveEndpointBuildsFromRegionAndVault() {
        String endpoint = OciKmsSupport.resolveEndpoint(Map.of(
                "region", "us-ashburn-1",
                "vault", "myvault"
        ));
        assertEquals("https://myvault-crypto.kms.us-ashburn-1.oraclecloud.com", endpoint);
    }

    @Test
    void requireKeyIdComplainsWhenMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OciKmsSupport.requireKeyId(Map.of()));
        assertTrue(ex.getMessage().contains("OCI keyId"));
    }
}
