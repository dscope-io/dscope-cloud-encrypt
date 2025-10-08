package io.dscope.utils.crypto;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;

public class AwsDecryptor implements CloudDecryptor {
    private final AWSKMS kms;

    public AwsDecryptor(Map<String, String> config) {
        this.kms = AWSKMSClientBuilder.standard()
                .withRegion(config.getOrDefault("region", "us-west-2"))
                .build();
    }

    @Override
    public String decrypt(String cipherBase64) {
        byte[] encrypted = Base64.getDecoder().decode(cipherBase64);
        DecryptRequest req = new DecryptRequest().withCiphertextBlob(ByteBuffer.wrap(encrypted));
        byte[] plain = kms.decrypt(req).getPlaintext().array();
        return new String(plain);
    }
}
