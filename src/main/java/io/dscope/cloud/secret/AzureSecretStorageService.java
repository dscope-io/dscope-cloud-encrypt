package io.dscope.cloud.secret;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Azure Key Vault secret storage implementation.
 */
final class AzureSecretStorageService implements CloudSecretStorageService {

    private final SecretClient client;

    AzureSecretStorageService(String vaultUrl) {
        this(new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient());
    }

    AzureSecretStorageService(SecretClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void putSecret(String name, byte[] data, Map<String, String> metadata) {
        byte[] payloadBytes = SecretPayloadCodec.encode(data, metadata);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        KeyVaultSecret secret = new KeyVaultSecret(name, payload);
        if (metadata != null && !metadata.isEmpty()) {
            secret.getProperties().setTags(metadata);
        }
        client.setSecret(secret);
    }

    @Override
    public SecretRecord getSecret(String name) {
        KeyVaultSecret secret = client.getSecret(name);
        if (secret == null) {
            return new SecretRecord(new byte[0], Map.of());
        }
        byte[] bytes = secret.getValue() != null
                ? secret.getValue().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        return SecretPayloadCodec.decode(bytes);
    }

    @Override
    public void deleteSecret(String name) {
        client.beginDeleteSecret(name).waitForCompletion();
        client.purgeDeletedSecret(name);
    }
}
