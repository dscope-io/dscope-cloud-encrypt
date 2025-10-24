# Spring Boot + Google Cloud KMS Sample

This sample shows how to bootstrap a tiny Spring Boot application that reads an encrypted property from a cloud KMS provider, decrypts it at startup, and prints the plaintext value. Google Cloud KMS is used out of the box, but you can switch to AWS KMS or Azure Key Vault by setting `KMS_PROVIDER`. The walkthrough now also demonstrates how to publish the same envelope-encrypted configuration file to **Google Secret Manager** by using the CloudEncrypt CLI.

> ⚠️ The application prints the decrypted secret to both the log and standard output **for demonstration only**. Do not log secrets in production workloads.

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker 24+ (for container builds)
- Google Cloud project with the **Cloud KMS** and **Secret Manager** APIs enabled
- A service account or workload identity with **Cloud KMS CryptoKey Encrypter/Decrypter** and **Secret Manager Secret Version Adder** (or `Secret Manager Admin`) roles
- `gcloud` CLI authenticated against the target project (for GCP flows)
- `aws` and/or `az` CLIs configured if you plan to target AWS or Azure

## 0. Publish the shared CloudEncrypt library locally

The demo uses the main CloudEncrypt library for the cross-cloud KMS abstractions. From the repository root, install it to your local Maven cache:

```bash
mvn -DskipTests install
```

## 1. Configure Google Cloud (KMS + Secret Manager)

1. Set the base environment variables for your KMS key:

    ```bash
    export GCP_PROJECT_ID="your-project-id"
    export GCP_KMS_LOCATION="us-central1"
    export GCP_KMS_KEY_RING="demo-ring"
    export GCP_KMS_KEY="demo-key"
    export GCP_SERVICE_ACCOUNT="cloudencrypt-demo@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
    ```

2. Enable the required services and prepare a key ring/key:

    ```bash
    gcloud services enable cloudkms.googleapis.com secretmanager.googleapis.com

    gcloud kms keyrings create "$GCP_KMS_KEY_RING" \
       --location="$GCP_KMS_LOCATION" --project="$GCP_PROJECT_ID"

    gcloud kms keys create "$GCP_KMS_KEY" \
       --location="$GCP_KMS_LOCATION" \
       --keyring="$GCP_KMS_KEY_RING" \
       --purpose="encryption" \
       --project="$GCP_PROJECT_ID"
    ```

3. Create or reuse a service account and grant permissions (skip if you already have one wired with the right roles):

    ```bash
    gcloud iam service-accounts create cloudencrypt-demo \
       --project="$GCP_PROJECT_ID"

    gcloud kms keys add-iam-policy-binding "$GCP_KMS_KEY" \
       --location="$GCP_KMS_LOCATION" \
       --keyring="$GCP_KMS_KEY_RING" \
       --project="$GCP_PROJECT_ID" \
       --member="serviceAccount:${GCP_SERVICE_ACCOUNT}" \
       --role="roles/cloudkms.cryptoKeyEncrypterDecrypter"

    gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
       --member="serviceAccount:${GCP_SERVICE_ACCOUNT}" \
       --role="roles/secretmanager.admin"
    ```

4. Authenticate your workstation for Application Default Credentials (ADC):

    ```bash
    gcloud auth application-default login
    ```

    If you prefer a JSON key file instead, download one for the service account and point `GOOGLE_APPLICATION_CREDENTIALS` to it when running the CLI or sample.

## 2. Prepare a ciphertext with Cloud KMS (GCP)

1. Encrypt a test secret and capture the Base64 ciphertext that the sample expects:

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

## 3. Publish the encrypted config to Google Secret Manager (optional but recommended)

1. Create a tiny `.env` file that carries the encrypted secret string the sample expects:

   ```bash
   cat <<EOF > secrets.env
   GCP_ENCRYPTED_SECRET=${GCP_ENCRYPTED_SECRET}
   EOF
   ```

2. Use the CloudEncrypt CLI to envelope-encrypt the file with your KMS key:

    ```bash
    java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar encrypt \
       --provider gcp \
       --set project=$GCP_PROJECT_ID \
       --set location=$GCP_KMS_LOCATION \
       --set keyRing=$GCP_KMS_KEY_RING \
       --set key=$GCP_KMS_KEY \
       --file secrets.env \
       --out secrets.env.kms
    ```

3. Push the encrypted artifact to Google Secret Manager and tag it with useful metadata:

    ```bash
    java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar secret-put \
       --provider gcp \
       --set project=$GCP_PROJECT_ID \
       --file secrets.env.kms \
       --name spring-demo-config \
       --metadata application=spring-gcp-kms-demo --metadata environment=dev
    ```

    The `secret-put` command automatically creates the secret if it does not exist and stores the blob as the latest secret version.

4. (Optional) Retrieve the payload and decrypt it locally to confirm the round trip:

    ```bash
    java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar secret-get \
       --provider gcp \
       --set project=$GCP_PROJECT_ID \
       --name spring-demo-config \
       --out secrets.env.kms --print-metadata

    java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar decrypt \
       --provider gcp \
       --set project=$GCP_PROJECT_ID \
       --set location=$GCP_KMS_LOCATION \
       --set keyRing=$GCP_KMS_KEY_RING \
       --set key=$GCP_KMS_KEY \
       --file secrets.env.kms \
       --out secrets.env
    ```

## 4. Run the sample locally

```bash
cd samples/spring-gcp-kms-demo
```

Pick one of the following options to expose `GCP_ENCRYPTED_SECRET` before launching Spring Boot:

- **Option A – use the ciphertext directly:**

   ```bash
   export GCP_ENCRYPTED_SECRET="$GCP_ENCRYPTED_SECRET"
   mvn spring-boot:run
   ```

- **Option B – hydrate from Secret Manager:**

   ```bash
   java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar secret-get \
      --provider gcp \
      --set project=$GCP_PROJECT_ID \
      --name spring-demo-config \
      --out secrets.env.kms

   java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar decrypt \
      --provider gcp \
      --set project=$GCP_PROJECT_ID \
      --set location=$GCP_KMS_LOCATION \
      --set keyRing=$GCP_KMS_KEY_RING \
      --set key=$GCP_KMS_KEY \
      --file secrets.env.kms \
      --out secrets.env

   set -a
   . ./secrets.env
   set +a

   mvn spring-boot:run
   ```

The application will decrypt `GCP_ENCRYPTED_SECRET` with the configured key and print:

```
PLAINTEXT=s3cr3t-value
```

When running locally with a JSON key file, point the `GOOGLE_APPLICATION_CREDENTIALS` environment variable at it, e.g.:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.gcp/demo-sa.json"
```

### Optional: Mount the decrypted secret into a local Docker container

If you want to exercise the production container locally and supply the decrypted secret as a mounted file, reuse the payload already retrieved in Option B (or fetch it again) and run:

```bash
# Ensure the latest secret version is present on disk
java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar secret-get \
   --provider gcp \
   --set project=$GCP_PROJECT_ID \
   --name spring-demo-config \
   --out secrets.env.kms

java -jar ../../target/cloud-encrypt-cli-1.3.0-shaded.jar decrypt \
   --provider gcp \
   --set project=$GCP_PROJECT_ID \
   --set location=$GCP_KMS_LOCATION \
   --set keyRing=$GCP_KMS_KEY_RING \
   --set key=$GCP_KMS_KEY \
   --file secrets.env.kms \
   --out secrets.env

# Run the container and mount the secret file read-only
docker run --rm \
   --mount type=bind,src="$(pwd)/secrets.env",target=/workspace/secrets.env,readonly \
   --env-file secrets.env \
   gcr.io/$GCP_PROJECT_ID/cloudencrypt-spring-demo:latest
```

The container reads the mounted file and loads the decrypted environment variables, mirroring the production contract.

You can override the KMS configuration via environment variables instead of editing `application.yaml`:

- `KMS_PROVIDER` (`gcp`, `aws`, or `azure`)
- `KMS_ENCRYPTED_SECRET` (generic secret value)
- `GCP_PROJECT_ID`, `GCP_KMS_LOCATION`, `GCP_KMS_KEY_RING`, `GCP_KMS_KEY`
- `AWS_REGION`, `AWS_KMS_KEY_ID`
- `AZURE_KEY_ID`

## 5. Build the runnable JAR

```bash
cd samples/spring-gcp-kms-demo
mvn -DskipTests package
java -jar target/spring-gcp-kms-demo-0.1.0.jar
```

## 6. Build and push the Docker image

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

## 7. Deploy to Cloud Run

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

- Cloud Run automatically mounts the runtime service account credentials. Ensure that service account includes the **Cloud KMS CryptoKey Decrypter** role on your key and can access the Secret Manager secret if you retrieve it at runtime.
- If you prefer other targets (GKE, Compute Engine), reuse the same container and propagate the environment variables + credentials accordingly.
- To mount the Secret Manager payload as a file instead of an environment variable, deploy with `--set-secrets`:

   ```bash
   gcloud run deploy "$SERVICE_NAME" \
      --project="$GCP_PROJECT_ID" \
      --image="gcr.io/$GCP_PROJECT_ID/cloudencrypt-spring-demo:latest" \
      --region="$REGION" \
      --allow-unauthenticated \
      --set-secrets=/workspace/secrets.env=projects/$GCP_PROJECT_ID/secrets/spring-demo-config:latest
   ```

   Cloud Run writes the latest secret version to `/workspace/secrets.env` inside the container so your entrypoint can source or parse it before launching the app.

## 8. Clean up

```bash
gcloud run services delete "$SERVICE_NAME" --region="$REGION"
gcloud kms keys delete "$GCP_KMS_KEY" --location="$GCP_KMS_LOCATION" --keyring="$GCP_KMS_KEY_RING"
gcloud kms keyrings delete "$GCP_KMS_KEY_RING" --location="$GCP_KMS_LOCATION"
gcloud secrets delete spring-demo-config --project="$GCP_PROJECT_ID"
```

## Troubleshooting

- Ensure both APIs are enabled: `gcloud services enable cloudkms.googleapis.com secretmanager.googleapis.com`
- For permission issues, grant `roles/cloudkms.cryptoKeyEncrypterDecrypter` on the specific KMS key and `roles/secretmanager.admin` (or more granular Secret Manager roles) to the runtime principal.
- The ciphertext must be Base64-encoded. The sample strips the `ENC(...)` wrapper automatically.
- When using Secret Manager, confirm that the CloudEncrypt CLI runs with the same Application Default Credentials the sample will use in order to avoid permission mismatches.
