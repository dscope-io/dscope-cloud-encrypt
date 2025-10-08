package io.dscope.utils.crypto;

import com.google.cloud.kms.v1.*;
import com.google.protobuf.ByteString;
import java.util.Base64;
import java.util.Map;

public class GcpEncryptor implements CloudEncryptor {
    private final Map<String, String> cfg;

    public GcpEncryptor(Map<String, String> cfg) {
        this.cfg = cfg;
    }

    @Override
    public String encrypt(String plainText) throws Exception {
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String keyName = String.format("projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s",
                    cfg.get("project"), cfg.get("location"), cfg.get("keyRing"), cfg.get("key"));
            EncryptResponse resp = client.encrypt(keyName, ByteString.copyFromUtf8(plainText));
            return Base64.getEncoder().encodeToString(resp.getCiphertext().toByteArray());
        }
    }
}
