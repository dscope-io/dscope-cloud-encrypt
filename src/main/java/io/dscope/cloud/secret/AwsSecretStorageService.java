package io.dscope.cloud.secret;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.Tag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AWS Secrets Manager implementation.
 */
final class AwsSecretStorageService implements CloudSecretStorageService {

    private final SecretsManagerClient client;

    AwsSecretStorageService(String region) {
        this(SecretsManagerClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(region))
                .build());
    }

    AwsSecretStorageService(SecretsManagerClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void putSecret(String name, byte[] data, Map<String, String> metadata) {
        byte[] payloadBytes = SecretPayloadCodec.encode(data, metadata);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        try {
            client.describeSecret(DescribeSecretRequest.builder().secretId(name).build());
            PutSecretValueRequest request = PutSecretValueRequest.builder()
                    .secretId(name)
                    .secretString(payload)
                    .build();
            client.putSecretValue(request);
        } catch (ResourceNotFoundException ex) {
            CreateSecretRequest.Builder builder = CreateSecretRequest.builder()
                    .name(name)
                    .secretString(payload);
            if (metadata != null && !metadata.isEmpty()) {
                List<Tag> tags = metadata.entrySet().stream()
                        .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                        .collect(Collectors.toList());
                builder.tags(tags);
            }
            CreateSecretResponse response = client.createSecret(builder.build());
            if (response.arn() == null || response.arn().isBlank()) {
                throw new IllegalStateException("AWS Secrets Manager failed to create secret: " + name);
            }
        }
    }

    @Override
    public SecretRecord getSecret(String name) {
        GetSecretValueResponse response = client.getSecretValue(GetSecretValueRequest.builder()
                .secretId(name)
                .build());
        if (response.secretString() != null) {
            return SecretPayloadCodec.decode(response.secretString().getBytes(StandardCharsets.UTF_8));
        }
        SdkBytes binary = response.secretBinary();
        if (binary == null) {
            return new SecretRecord(new byte[0], Map.of());
        }
        return SecretPayloadCodec.decode(binary.asByteArray());
    }

    @Override
    public void deleteSecret(String name) {
        client.deleteSecret(DeleteSecretRequest.builder()
                .secretId(name)
                .forceDeleteWithoutRecovery(true)
                .build());
    }

    @Override
    public void close() {
        client.close();
    }
}
