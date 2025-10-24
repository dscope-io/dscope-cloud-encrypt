package io.dscope.cloud.secret;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Google Secret Manager implementation.
 */
final class GcpSecretStorageService implements CloudSecretStorageService {

    private final Supplier<SecretManagerServiceClient> clientFactory;
    private final String projectId;

    GcpSecretStorageService(String projectId) {
        this(() -> {
            try {
                return SecretManagerServiceClient.create();
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to create SecretManagerServiceClient", ex);
            }
        }, projectId);
    }

    GcpSecretStorageService(Supplier<SecretManagerServiceClient> clientFactory, String projectId) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    @Override
    public void putSecret(String name, byte[] data, Map<String, String> metadata) throws Exception {
        SecretName secretName = SecretName.of(projectId, name);
        try (SecretManagerServiceClient client = clientFactory.get()) {
            ensureSecretExists(client, secretName, metadata);
            byte[] payloadBytes = SecretPayloadCodec.encode(data, metadata);
            SecretPayload payload = SecretPayload.newBuilder()
                    .setData(ByteString.copyFrom(payloadBytes))
                    .build();
            AddSecretVersionRequest request = AddSecretVersionRequest.newBuilder()
                    .setParent(secretName.toString())
                    .setPayload(payload)
                    .build();
            client.addSecretVersion(request);
        }
    }

    @Override
    public SecretRecord getSecret(String name) throws Exception {
        SecretVersionName versionName = SecretVersionName.of(projectId, name, "latest");
        try (SecretManagerServiceClient client = clientFactory.get()) {
            AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
                    .setName(versionName.toString())
                    .build();
            ByteString data = client.accessSecretVersion(request).getPayload().getData();
            return SecretPayloadCodec.decode(data.toByteArray());
        }
    }

    @Override
    public void deleteSecret(String name) throws Exception {
        SecretName secretName = SecretName.of(projectId, name);
        try (SecretManagerServiceClient client = clientFactory.get()) {
            client.deleteSecret(secretName);
        }
    }

    private void ensureSecretExists(SecretManagerServiceClient client, SecretName secretName, Map<String, String> metadata) {
        try {
            client.getSecret(secretName);
        } catch (NotFoundException notFound) {
            Replication replication = Replication.newBuilder()
                    .setAutomatic(Replication.Automatic.newBuilder().build())
                    .build();
            Secret secret = Secret.newBuilder()
                    .putAllLabels(metadata != null ? metadata : Map.of())
                    .setReplication(replication)
                    .build();
            client.createSecret(ProjectName.of(projectId), secretName.getSecret(), secret);
        }
    }
}
