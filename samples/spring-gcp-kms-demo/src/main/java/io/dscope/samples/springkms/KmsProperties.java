package io.dscope.samples.springkms;

import io.dscope.utils.crypto.CloudKmsConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kms")
public class KmsProperties {

    private String provider = "gcp";

    // GCP
    private String projectId;
    private String locationId;
    private String keyRingId;
    private String keyId;

    // AWS
    private String awsRegion;
    private String awsKeyId;

    // Azure
    private String azureKeyId;

    private String encryptedSecret;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getKeyRingId() {
        return keyRingId;
    }

    public void setKeyRingId(String keyRingId) {
        this.keyRingId = keyRingId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getAwsKeyId() {
        return awsKeyId;
    }

    public void setAwsKeyId(String awsKeyId) {
        this.awsKeyId = awsKeyId;
    }

    public String getAzureKeyId() {
        return azureKeyId;
    }

    public void setAzureKeyId(String azureKeyId) {
        this.azureKeyId = azureKeyId;
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    public CloudKmsConfig toConfig() {
        String normalized = provider == null ? "" : provider.trim().toLowerCase();
        switch (normalized) {
            case "aws":
                return CloudKmsConfig.builder("aws")
                        .with("region", defaultRegion())
                        .with("keyId", require(awsKeyId, "app.kms.aws-key-id"))
                        .build();
            case "azure":
                return CloudKmsConfig.forAzure(require(azureKeyId, "app.kms.azure-key-id"));
            case "gcp":
            case "":
                return CloudKmsConfig.forGcp(
                        require(projectId, "app.kms.project-id"),
                        require(locationId, "app.kms.location-id"),
                        require(keyRingId, "app.kms.key-ring-id"),
                        require(keyId, "app.kms.key-id")
                );
            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    private String defaultRegion() {
        return awsRegion != null && !awsRegion.isBlank() ? awsRegion : "us-west-2";
    }

    private String require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return value;
    }
}
