package io.dscope.cloud.secret;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class SecretPayloadCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SecretPayloadCodec() {
    }

    static byte[] encode(byte[] data, Map<String, String> metadata) {
        Objects.requireNonNull(data, "data");
        Payload payload = new Payload();
        payload.data = Base64.getEncoder().encodeToString(data);
        payload.metadata = metadata != null ? new TreeMap<>(metadata) : Collections.emptyMap();
        return GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
    }

    static CloudSecretStorageService.SecretRecord decode(byte[] payloadBytes) {
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        Payload payload = GSON.fromJson(new String(payloadBytes, StandardCharsets.UTF_8), Payload.class);
        byte[] data = payload != null && payload.data != null
                ? Base64.getDecoder().decode(payload.data)
                : new byte[0];
        Map<String, String> metadata = payload != null && payload.metadata != null
                ? payload.metadata
                : Collections.emptyMap();
        return new CloudSecretStorageService.SecretRecord(data, metadata);
    }

    private static final class Payload {
        String data;
        Map<String, String> metadata;
    }
}
