# Spring Boot + Google Cloud KMS Sample

This sample shows how to bootstrap a tiny Spring Boot application that reads an encrypted property from a cloud KMS provider, decrypts it at startup, and prints the plaintext value. Google Cloud KMS is used out of the box, but you can switch to AWS KMS or Azure Key Vault by setting `KMS_PROVIDER`.

> ⚠️ The application prints the decrypted secret to both the log and standard output **for demonstration only**. Do not log secrets in production workloads.

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker 24+ (for container builds)
- Google Cloud project with the Cloud KMS API enabled (for GCP flows)
- A service account/principal with decryption rights on the target key
- `gcloud` CLI authenticated against the target project (for GCP flows)
- `aws` and/or `az` CLIs configured if you plan to target AWS or Azure

## 0. Publish the shared CloudEncrypt library locally

The demo uses the main CloudEncrypt library for the cross-cloud KMS abstractions. From the repository root, install it to your local Maven cache:

```bash
mvn -DskipTests install
```

## 1. Prepare a ciphertext with Cloud KMS (GCP)

1. Set the base environment variables for your KMS key:

   ```bash
   export GCP_PROJECT_ID="your-project-id"
   export GCP_KMS_LOCATION="us-central1"
   export GCP_KMS_KEY_RING="demo-ring"
   export GCP_KMS_KEY="demo-key"
   ```

2. Create the key ring + key (skip if they already exist):

   ```bash
   gcloud kms keyrings create "$GCP_KMS_KEY_RING" \
     --location="$GCP_KMS_LOCATION"

   gcloud kms keys create "$GCP_KMS_KEY" \
     --location="$GCP_KMS_LOCATION" \
     --keyring="$GCP_KMS_KEY_RING" \
     --purpose="encryption"
   ```

3. Encrypt a test secret and capture the Base64 ciphertext that the sample expects:

   ```bash
   printf "s3cr3t-value" > plaintext.txt

   gcloud kms encrypt \
     --project="$GCP_PROJECT_ID" \
     --location="$GCP_KMS_LOCATION" \
     --keyring="$GCP_KMS_KEY_RING" \
     --key="$GCP_KMS_KEY" \
     --plaintext-file="plaintext.txt" \
     --ciphertext-file="ciphertext.bin"

   export GCP_ENCRYPTED_SECRET="ENC($(base64 -w0 ciphertext.bin))"
   rm plaintext.txt ciphertext.bin
   ```

   The `ENC(...)` wrapper matches the convention used by the CloudEncrypt CLI.

## 2. Run the sample locally

```bash
cd samples/spring-gcp-kms-demo
mvn spring-boot:run
```

The application will decrypt `GCP_ENCRYPTED_SECRET` with the configured key and print:

```
PLAINTEXT=s3cr3t-value
```

When running locally, point the `GOOGLE_APPLICATION_CREDENTIALS` environment variable at a JSON key for a service account that has the **Cloud KMS CryptoKey Decrypter** role, e.g.:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.gcp/demo-sa.json"
```

You can override the KMS configuration via environment variables instead of editing `application.yaml`:

- `KMS_PROVIDER` (`gcp`, `aws`, or `azure`)
- `KMS_ENCRYPTED_SECRET` (generic secret value)
- `GCP_PROJECT_ID`, `GCP_KMS_LOCATION`, `GCP_KMS_KEY_RING`, `GCP_KMS_KEY`
- `AWS_REGION`, `AWS_KMS_KEY_ID`
- `AZURE_KEY_ID`

## 3. Build the runnable JAR

```bash
cd samples/spring-gcp-kms-demo
mvn -DskipTests package
java -jar target/spring-gcp-kms-demo-0.1.0.jar
```

## 4. Build and push the Docker image

1. Build the container:

   ```bash
   cd samples/spring-gcp-kms-demo
   docker build -t gcr.io/$GCP_PROJECT_ID/cloudencrypt-spring-demo:latest .
   ```

2. Push it to Google Container Registry (or Artifact Registry):

   ```bash
   gcloud auth configure-docker
   docker push gcr.io/$GCP_PROJECT_ID/cloudencrypt-spring-demo:latest
   ```

   > Replace `gcr.io` with your Artifact Registry hostname if you use a regional repository (e.g. `us-central1-docker.pkg.dev`).

## 5. Deploy to Cloud Run

```bash
SERVICE_NAME="cloudencrypt-spring-demo"
REGION="us-central1"

gcloud run deploy "$SERVICE_NAME" \
  --project="$GCP_PROJECT_ID" \
  --image="gcr.io/$GCP_PROJECT_ID/cloudencrypt-spring-demo:latest" \
  --region="$REGION" \
  --allow-unauthenticated \
  --set-env-vars=GCP_PROJECT_ID="$GCP_PROJECT_ID",GCP_KMS_LOCATION="$GCP_KMS_LOCATION",GCP_KMS_KEY_RING="$GCP_KMS_KEY_RING",GCP_KMS_KEY="$GCP_KMS_KEY",GCP_ENCRYPTED_SECRET="$GCP_ENCRYPTED_SECRET"
```

- Cloud Run automatically mounts the runtime service account credentials. Ensure that service account includes the **Cloud KMS CryptoKey Decrypter** role on your key.
- If you prefer other targets (GKE, Compute Engine), reuse the same container and propagate the environment variables + credentials accordingly.

## 6. Clean up

```bash
gcloud run services delete "$SERVICE_NAME" --region="$REGION"
gcloud kms keys delete "$GCP_KMS_KEY" --location="$GCP_KMS_LOCATION" --keyring="$GCP_KMS_KEY_RING"
gcloud kms keyrings delete "$GCP_KMS_KEY_RING" --location="$GCP_KMS_LOCATION"
```

## Troubleshooting

- Ensure the KMS API is enabled: `gcloud services enable cloudkms.googleapis.com`
- For permission issues, grant `roles/cloudkms.cryptoKeyDecrypter` to the runtime service account on the specific KMS key.
- The ciphertext must be Base64-encoded. The sample strips the `ENC(...)` wrapper automatically.
