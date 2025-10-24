package io.dscope.cloud.secret;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudSecretStorageFactoryTest {

    @Test
    void memoryProviderStoresSecrets() throws Exception {
        CloudSecretConfig config = CloudSecretConfig.builder("memory").build();
        try (CloudSecretStorageService service = CloudSecretStorageFactory.create(config)) {
            service.putSecret("sample", "hello".getBytes(StandardCharsets.UTF_8), Map.of("env", "dev"));

            CloudSecretStorageService.SecretRecord record = service.getSecret("sample");
            assertEquals("hello", new String(record.data(), StandardCharsets.UTF_8));
            assertEquals("dev", record.metadata().get("env"));

        service.deleteSecret("sample");
        assertEquals("Secret not found: sample",
            assertThrows(IllegalArgumentException.class, () -> service.getSecret("sample")).getMessage());
        }
    }
}
