package io.dscope.utils.crypto;

import com.google.cloud.kms.v1.*;
import com.google.protobuf.ByteString;
import java.util.Base64;
import java.util.Map;

public class GcpDecryptor implements CloudDecryptor {
    private final Map<String, String> cfg;

    public GcpDecryptor(Map<String, String> cfg) {
        this.cfg = cfg;
    }

    @Override
    public String decrypt(String cipherBase64) throws Exception {
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String keyName = String.format("projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s",
                    cfg.get("project"), cfg.get("location"), cfg.get("keyRing"), cfg.get("key"));
            byte[] enc = java.util.Base64.getDecoder().decode(cipherBase64);
            DecryptResponse resp = client.decrypt(keyName, ByteString.copyFrom(enc));
            return resp.getPlaintext().toStringUtf8();
        }
    }
}
