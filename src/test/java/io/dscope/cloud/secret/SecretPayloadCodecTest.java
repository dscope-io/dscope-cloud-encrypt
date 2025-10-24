package io.dscope.cloud.secret;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretPayloadCodecTest {

    @Test
    void roundTripsPayload() {
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        Map<String, String> metadata = Map.of("env", "dev");

        byte[] encoded = SecretPayloadCodec.encode(data, metadata);
        CloudSecretStorageService.SecretRecord record = SecretPayloadCodec.decode(encoded);

        assertArrayEquals(data, record.data());
        assertEquals("dev", record.metadata().get("env"));
    }

    @Test
    void decodeHandlesMissingMetadata() {
        byte[] encoded = SecretPayloadCodec.encode("data".getBytes(StandardCharsets.UTF_8), null);
        CloudSecretStorageService.SecretRecord record = SecretPayloadCodec.decode(encoded);

        assertEquals(0, record.metadata().size());
    }
}
