package io.dscope.utils.crypto;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.DecryptResult;
import com.azure.security.keyvault.keys.cryptography.models.EncryptionAlgorithm;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AzureDecryptor implements CloudDecryptor {
    private final CryptographyClient client;

    public AzureDecryptor(Map<String, String> config) {
        this.client = new CryptographyClientBuilder()
                .keyIdentifier(config.get("keyId"))
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Override
    public String decrypt(String cipherBase64) {
        byte[] encrypted = Base64.getDecoder().decode(cipherBase64);
    DecryptResult result = client.decrypt(
        EncryptionAlgorithm.RSA_OAEP,
        encrypted
    );
    return new String(result.getPlainText(), StandardCharsets.UTF_8);
    }
}
