package io.dscope.cloud.secret;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.SecretSummary;
import com.oracle.bmc.vault.model.UpdateSecretDetails;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.ListSecretsRequest;
import com.oracle.bmc.vault.requests.ScheduleSecretDeletionRequest;
import com.oracle.bmc.vault.requests.UpdateSecretRequest;
import com.oracle.bmc.vault.responses.ListSecretsResponse;
import com.oracle.bmc.vault.responses.ScheduleSecretDeletionResponse;
import com.oracle.bmc.vault.model.ScheduleSecretDeletionDetails;
import io.dscope.utils.crypto.OciKmsSupport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Oracle Cloud Infrastructure secrets implementation.
 */
final class OciSecretStorageService implements CloudSecretStorageService {

    private final VaultsClient vaultsClient;
    private final SecretsClient secretsClient;
    private final Map<String, String> settings;

    OciSecretStorageService(Map<String, String> settings) throws Exception {
        this(settings, createVaultsClient(settings), createSecretsClient(settings));
    }

    OciSecretStorageService(Map<String, String> settings, VaultsClient vaultsClient, SecretsClient secretsClient) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.vaultsClient = Objects.requireNonNull(vaultsClient, "vaultsClient");
        this.secretsClient = Objects.requireNonNull(secretsClient, "secretsClient");
    }

    @Override
    public void putSecret(String name, byte[] data, Map<String, String> metadata) {
        String secretId = findSecretId(name);
        byte[] payload = SecretPayloadCodec.encode(data, metadata);
        String content = Base64.getEncoder().encodeToString(payload);
        Base64SecretContentDetails contentDetails = Base64SecretContentDetails.builder()
                .content(content)
                .stage(Base64SecretContentDetails.Stage.Current)
                .build();
        if (secretId == null) {
            CreateSecretDetails details = CreateSecretDetails.builder()
                    .compartmentId(require("compartmentId"))
                    .secretName(name)
                    .vaultId(require("vaultId"))
                    .keyId(require("keyId"))
                    .description("Managed by CloudEncrypt")
                    .secretContent(contentDetails)
            .freeformTags(metadata != null ? metadata : Map.of())
                    .build();
            vaultsClient.createSecret(CreateSecretRequest.builder()
                    .createSecretDetails(details)
                    .build());
        } else {
            UpdateSecretDetails details = UpdateSecretDetails.builder()
                    .secretContent(contentDetails)
            .freeformTags(metadata != null ? metadata : Map.of())
                    .build();
            vaultsClient.updateSecret(UpdateSecretRequest.builder()
                    .secretId(secretId)
                    .updateSecretDetails(details)
                    .build());
        }
    }

    @Override
    public SecretRecord getSecret(String name) {
        String secretId = findSecretId(name);
        if (secretId == null) {
            throw new IllegalArgumentException("Secret not found: " + name);
        }
        GetSecretBundleResponse response = secretsClient.getSecretBundle(GetSecretBundleRequest.builder()
                .secretId(secretId)
                .stage(GetSecretBundleRequest.Stage.Current)
                .build());
        SecretBundle bundle = response.getSecretBundle();
        SecretBundleContentDetails contentDetails = bundle != null ? bundle.getSecretBundleContent() : null;
        if (contentDetails instanceof Base64SecretBundleContentDetails base64) {
            byte[] decoded = Base64.getDecoder().decode(base64.getContent());
            return SecretPayloadCodec.decode(decoded);
        }
        return new SecretRecord(new byte[0], Map.of());
    }

    @Override
    public void deleteSecret(String name) {
        String secretId = findSecretId(name);
        if (secretId == null) {
            return;
        }
    ScheduleSecretDeletionDetails details = ScheduleSecretDeletionDetails.builder()
        .timeOfDeletion(Date.from(Instant.now().plus(2, ChronoUnit.MINUTES)))
        .build();
    ScheduleSecretDeletionRequest request = ScheduleSecretDeletionRequest.builder()
        .secretId(secretId)
        .scheduleSecretDeletionDetails(details)
        .build();
        ScheduleSecretDeletionResponse response = vaultsClient.scheduleSecretDeletion(request);
        if (response.get__httpStatusCode__() >= 400) {
            throw new IllegalStateException("Failed to schedule deletion for secret: " + name);
        }
    }

    @Override
    public void close() {
        vaultsClient.close();
        secretsClient.close();
    }

    private String findSecretId(String name) {
        ListSecretsResponse response = vaultsClient.listSecrets(ListSecretsRequest.builder()
                .compartmentId(require("compartmentId"))
                .name(name)
                .vaultId(require("vaultId"))
                .limit(1)
                .build());
        if (response.getItems().isEmpty()) {
            return null;
        }
        SecretSummary summary = response.getItems().get(0);
        return summary.getId();
    }

    private String require(String key) {
        String value = settings.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing OCI secret configuration: " + key);
        }
        return value;
    }

    private static VaultsClient createVaultsClient(Map<String, String> settings) throws Exception {
        AuthenticationDetailsProvider provider = OciKmsSupport.authenticationProvider(settings);
        VaultsClient client = VaultsClient.builder().build(provider);
        Region region = CloudSecretStorageFactory.resolveOciRegion(settings);
        if (region != null) {
            client.setRegion(region);
        }
        return client;
    }

    private static SecretsClient createSecretsClient(Map<String, String> settings) throws Exception {
        AuthenticationDetailsProvider provider = OciKmsSupport.authenticationProvider(settings);
        SecretsClient client = SecretsClient.builder().build(provider);
        Region region = CloudSecretStorageFactory.resolveOciRegion(settings);
        if (region != null) {
            client.setRegion(region);
        }
        return client;
    }
}
