package io.dscope.utils.crypto;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.EncryptRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class AwsEncryptor implements CloudEncryptor {
    private final AWSKMS kms;
    private final String keyId;

    public AwsEncryptor(Map<String, String> config) {
        this.kms = AWSKMSClientBuilder.standard()
                .withRegion(config.getOrDefault("region", "us-west-2"))
                .build();
        String key = config.get("keyId");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("AWS keyId is required");
        }
        this.keyId = key;
    }

    @Override
    public String encrypt(String plainText) {
        ByteBuffer plaintext = ByteBuffer.wrap(plainText.getBytes(StandardCharsets.UTF_8));
        EncryptRequest req = new EncryptRequest().withKeyId(keyId).withPlaintext(plaintext);
        ByteBuffer cipher = kms.encrypt(req).getCiphertextBlob();
        return Base64.getEncoder().encodeToString(ByteBufferUtils.copyRemaining(cipher));
    }
}
