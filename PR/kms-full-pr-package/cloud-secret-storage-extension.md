# 🧩 Cloud Secret Storage Extension  
**Dscope Cloud Encrypt — v1.3.1 Proposal**

### 📘 Purpose  
This update introduces **multi-cloud Secret Manager integration** to complement existing KMS-based file encryption.  
Encrypted files or metadata can now be securely stored and retrieved from each cloud’s native Secret Manager service.

---

## 🚀 Summary

| Area | Description |
|-------|-------------|
| **Feature Name** | Cloud Secret Storage |
| **Parent Branch** | `feature/kms-file-encrypt` |
| **Target Version** | v1.3.1 |
| **Purpose** | Allow storage and retrieval of encrypted file content and metadata in native cloud Secret Managers (Google, AWS, Azure, OCI). |
| **Status** | Proposed for inclusion after successful validation of v1.3.0 build. |

---

## 🔐 Feature Overview

This module extends the **KMS encryption layer** by adding a **Secret Storage abstraction** that supports:

- Persisting encrypted files or payloads (as base64)  
- Retrieving the latest version for decryption  
- Managing metadata such as KMS key ID, project, and region  
- Provider-agnostic access interface  

---

## ☁️ Multi-Cloud Implementation

| Cloud | Service | Supported Operations |
|--------|----------|----------------------|
| **Google Cloud** | Secret Manager | Create, add version, retrieve |
| **AWS** | Secrets Manager | Create, update, retrieve |
| **Azure** | Key Vault Secrets | Set, get, list versions |
| **Oracle Cloud** | OCI Vault Secrets | Create, retrieve |

---

## 🧱 Architectural Layers

| Layer | Responsibility | Technology |
|--------|----------------|------------|
| **Encryption Layer** | Encrypts/decrypts file using Cloud KMS | Existing KMS modules |
| **Secret Storage Layer** | Persists encrypted file or metadata | GCP/AWS/Azure/OCI SDKs |
| **Abstraction Layer** | Unified interface for all providers | `CloudSecretStorageService` |
| **Optional Orchestration** | File routing and automation | Apache Camel routes (if enabled) |

---

## ⚙️ Implementation Details

### 🧩 1. Core Interface
```java
public interface CloudSecretStorageService {
    void putSecret(String name, byte[] data, Map<String, String> metadata);
    byte[] getSecret(String name);
    void deleteSecret(String name);
}
```

### 🧩 2. Provider Implementations
| Class | Provider | SDK |
|--------|-----------|----|
| `GcpSecretStorageService` | Google Cloud | `google-cloud-secretmanager` |
| `AwsSecretStorageService` | AWS | `software.amazon.awssdk:secretsmanager` |
| `AzureSecretStorageService` | Azure | `azure-security-keyvault-secrets` |
| `OciSecretStorageService` | Oracle | `oci-java-sdk-vault` |

Each implementation uses native SDKs to handle binary payloads (e.g., `.enc` files).

---

## 🧾 Example Usage (Java)

```java
CloudSecretStorageService service = new GcpSecretStorageService();

Path file = Paths.get("sample.enc");
service.putSecret("encrypted-file", Files.readAllBytes(file), Map.of("kmsKey", "projects/my-project/..."));

byte[] decrypted = service.getSecret("encrypted-file");
Files.write(Paths.get("sample.dec"), decrypted);
```

---

## 🧩 Optional: Camel Integration

To automate or expose this as a service endpoint:
```yaml
- route:
    id: secret-storage-route
    from:
      uri: "file:input/encrypted?include=.*\\.enc"
      steps:
        - to: "bean:cloudSecretStorage?method=putSecret(${header.fileName}, ${body})"
        - log:
            message: "Stored file ${header.fileName} in ${header.cloudProvider} Secret Manager"
```

---

## 🧠 Configuration Properties

```properties
# Cloud provider selection
dscope.secret.provider=gcp

# GCP
dscope.secret.project.id=my-project

# AWS
dscope.secret.region=us-east-1

# Azure
dscope.secret.vault.url=https://my-vault.vault.azure.net/

# OCI
dscope.secret.compartment.id=ocid1.compartment.oc1..aaaa
```

---

## 🔒 Security Best Practices

- Encrypt files **before** sending to Secret Manager.  
- Enforce least-privilege IAM roles.  
- Rotate secrets periodically.  
- Avoid logging secret data — only metadata.  
- Use per-environment isolation (e.g., `dev`, `staging`, `prod`).  

---

## 🧱 Dependencies

**Maven Additions**
```xml
<dependencies>
  <dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-secretmanager</artifactId>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>secretsmanager</artifactId>
  </dependency>
  <dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-security-keyvault-secrets</artifactId>
  </dependency>
  <dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-vault</artifactId>
  </dependency>
</dependencies>
```

*(Camel dependency optional for orchestration use cases.)*

---

## 🧩 Test Scenarios

| ID | Scenario | Expected Outcome |
|----|-----------|------------------|
| T1 | Upload `.enc` file to GCP Secret Manager | Secret created and retrievable |
| T2 | Retrieve file from AWS Secrets Manager | Correct binary restored |
| T3 | Invalid secret name | Proper exception and log |
| T4 | Cross-provider integration | Same interface works for all |

---

## 🧾 PR Integration Notes

- **Branch:** `feature/kms-file-encrypt`  
- **Target:** `main`  
- **Version bump:** `1.3.1`  
- **Labels:** `enhancement`, `security`, `multi-cloud`  
- **Docs added:** `cloud-secret-storage-extension.md`

---

### ✍️ Maintainer Notes
This extension ensures DScope’s encryption stack can operate **end-to-end** — encrypt, store, and retrieve secrets securely across all major clouds, using consistent APIs.
