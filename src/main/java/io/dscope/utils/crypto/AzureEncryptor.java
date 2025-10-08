package io.dscope.utils.crypto;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.EncryptResult;
import com.azure.security.keyvault.keys.cryptography.models.EncryptionAlgorithm;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AzureEncryptor implements CloudEncryptor {
    private final CryptographyClient client;

    public AzureEncryptor(Map<String, String> config) {
        this.client = new CryptographyClientBuilder()
                .keyIdentifier(config.get("keyId"))
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Override
    public String encrypt(String plainText) {
    EncryptResult result = client.encrypt(
        EncryptionAlgorithm.RSA_OAEP,
        plainText.getBytes(StandardCharsets.UTF_8)
    );
    return Base64.getEncoder().encodeToString(result.getCipherText());
    }
}
