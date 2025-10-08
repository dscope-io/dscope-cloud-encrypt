package io.dscope.utils.crypto;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.EncryptRequest;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;

public class AwsEncryptor implements CloudEncryptor {
    private final AWSKMS kms;
    private final String keyId;

    public AwsEncryptor(Map<String, String> config) {
        this.kms = AWSKMSClientBuilder.standard()
                .withRegion(config.getOrDefault("region", "us-west-2"))
                .build();
        this.keyId = config.get("keyId");
        if (this.keyId == null) {
            System.err.println("⚠️  AwsEncryptor: keyId not set; provide keyId=...");
        }
    }

    @Override
    public String encrypt(String plainText) {
        ByteBuffer plaintext = ByteBuffer.wrap(plainText.getBytes());
        EncryptRequest req = new EncryptRequest().withKeyId(keyId).withPlaintext(plaintext);
        ByteBuffer cipher = kms.encrypt(req).getCiphertextBlob();
        return Base64.getEncoder().encodeToString(cipher.array());
    }
}
