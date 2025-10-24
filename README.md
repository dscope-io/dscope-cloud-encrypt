# ☁️ CloudEncrypt CLI (Java / Maven / VS Code)

A cross-cloud encryption command-line tool for managing secrets securely with **AWS KMS**, **Azure Key Vault**, **Google Cloud KMS**, and **Oracle Cloud Infrastructure KMS**.

## ✨ Features
- Multi-cloud: AWS, Azure, GCP, OCI (auto-detect provider)
- Encrypt & decrypt single values or whole files (properties/yml/env)
- Stream large files through Cloud KMS envelope encryption with dedicated `encrypt` / `decrypt` commands
- Push and retrieve encrypted payloads from each cloud's native Secret Manager via `secret-*` commands
- Store plaintext secrets via KMS and write them to files with a dedicated `store` command
- Recursive directory scanning + glob patterns
- `--dry-run` preview mode
- `--json` machine-readable summaries
- `--check` audit mode (fails if plaintext secrets found)
- Project config via `.cloudencrypt.yml`
- `init` command to scaffold config
- Built-in `--help` and subcommand guidance powered by Picocli
- Shaded runnable JAR

## 🚀 Quick Start

```bash
# Build
mvn clean package

# Run CLI directly (auto-detect cloud)
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar "mySecret"

# Initialize default config
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar init

# Encrypt files in repo (using .cloudencrypt.yml includes/excludes)
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar

# Envelope-encrypt a standalone file with the active provider
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar encrypt --file secrets.env --out secrets.env.kms

# Decrypt the previously encrypted payload
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar decrypt --file secrets.env.kms --out secrets.env

# Upload the encrypted artifact to your secret manager
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar secret-put --provider gcp \
	--set project=my-project --file secrets.env.kms --name prod-config --metadata environment=prod

# Retrieve and materialize the latest secret version
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar secret-get --provider gcp \
	--set project=my-project --name prod-config --out secrets.env.kms --print-metadata

# Audit for unencrypted secrets
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar --check

# Discover commands and options
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar --help

# Decrypt a file locally
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar src/main/resources/.env --decrypt

# Store a literal secret via KMS and append to an env file
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar store --provider aws \
	--set region=us-west-2 --set keyId=alias/prod-app \
	--name API_TOKEN --output secrets/.env "super-secret-value"

# Store a secret with OCI KMS using your ~/.oci/config profile
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar store --provider oci \
	--set configFile=$HOME/.oci/config --set profile=DEFAULT \
	--set endpoint=https://<vault>-crypto.kms.us-ashburn-1.oraclecloud.com \
	--set keyId=ocid1.key.oc1..<uniqueId> "super-secret-value"
```

## 🌐 Google Cloud Secret Manager (file workflow)

Use this end-to-end workflow to envelope-encrypt a configuration file with Cloud KMS and publish it to Google Secret Manager.

**Set up Google Cloud**
- Enable the APIs: `gcloud services enable cloudkms.googleapis.com secretmanager.googleapis.com`
- Create or reuse a key ring/key (substitute your own names):
	```bash
	export GCP_PROJECT_ID="your-project-id"
	export GCP_KMS_LOCATION="us-central1"
	export GCP_KMS_KEY_RING="demo-ring"
	export GCP_KMS_KEY="demo-key"
	export GCP_SERVICE_ACCOUNT="cloudencrypt-demo@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

	gcloud kms keyrings create "$GCP_KMS_KEY_RING" \
		--location="$GCP_KMS_LOCATION" --project="$GCP_PROJECT_ID"

	gcloud kms keys create "$GCP_KMS_KEY" \
		--location="$GCP_KMS_LOCATION" \
		--keyring="$GCP_KMS_KEY_RING" \
		--purpose="encryption" \
		--project="$GCP_PROJECT_ID"
	```
- Grant permissions to the service account or workload identity that will run the CLI/application:
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
- Authenticate locally with Application Default Credentials:
	```bash
	gcloud auth application-default login
	```

**Encrypt and upload the file**
```bash
cat <<EOF > secrets.env
API_TOKEN=super-secret-value
EOF

java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar encrypt \
	--provider gcp \
	--set project=$GCP_PROJECT_ID \
	--set location=$GCP_KMS_LOCATION \
	--set keyRing=$GCP_KMS_KEY_RING \
	--set key=$GCP_KMS_KEY \
	--file secrets.env \
	--out secrets.env.kms

java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar secret-put \
	--provider gcp \
	--set project=$GCP_PROJECT_ID \
	--file secrets.env.kms \
	--name app-config \
	--metadata environment=dev --metadata application=cloud-encrypt
```

**Retrieve and decrypt when you need it**
```bash
java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar secret-get \
	--provider gcp \
	--set project=$GCP_PROJECT_ID \
	--name app-config \
	--out secrets.env.kms --print-metadata

java -jar target/cloud-encrypt-cli-1.3.0-shaded.jar decrypt \
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
```

These steps adapt the Google Cloud setup guidance from `PR/kms-full-pr-package/RunJavaWithGCloud.md` and mirror what the `samples/spring-gcp-kms-demo` guide walks through.

> For convenience, use the provided `install-cloud-encrypt.sh` to install a wrapper script as `cloud-encrypt` in your `~/.local/bin`.

## 🍃 Using CloudEncrypt with Spring Boot

CloudEncrypt is designed to keep sensitive keys in your Spring configuration encrypted at rest while still letting Spring read them as plain text at runtime.

1. **Store encrypted values in `application.properties` (or `.yml`).** Keep secrets wrapped in `ENC(...)`, for example:
	```properties
	# src/main/resources/application.properties
	spring.datasource.password=ENC(....)
	mail.api-token=ENC(....)
	```
2. **Create a decrypted runtime overlay before you start Spring.** Copy your encrypted file to a temporary location and run the CLI in `--decrypt` mode on the copy:
	```bash
	mkdir -p target/runtime-config
	cp src/main/resources/application.properties target/runtime-config/app-decrypted.properties
	cloud-encrypt target/runtime-config/app-decrypted.properties --decrypt
	```
	The CLI rewrites the file in place, so decrypt only the copy that lives outside your source tree. Remove it when the app stops (for example in your container entrypoint or systemd unit).
3. **Point Spring at the decrypted overlay.** Use Spring's config import/location knobs when launching:
	```bash
	SPRING_CONFIG_IMPORT=optional:file:target/runtime-config/app-decrypted.properties \
	./mvnw spring-boot:run
	```
	or, if you ship an executable JAR:
	```bash
	SPRING_CONFIG_LOCATION=optional:target/runtime-config/app-decrypted.properties \
	java -jar build/libs/your-app.jar
	```

With this flow, the repository and container image continue to store only encrypted secrets, while Spring reads the decrypted overlay that CloudEncrypt prepares just-in-time.

## 🔐 Sensitive Key Pattern
Any key whose name matches `(password|secret|token|key)` (case-insensitive) is considered sensitive.

## 🧪 Tests

```bash
mvn test
```

Unit tests exercise the core transformation logic without calling cloud SDKs.

## 📚 Samples

- [`samples/spring-gcp-kms-demo`](samples/spring-gcp-kms-demo): Minimal Spring Boot app that fetches an encrypted property from a cloud KMS (GCP, AWS, Azure), decrypts it at startup, and prints the plaintext.

## 🧰 Requirements
- Java 21+
- Maven 3.9+

## 📂 VS Code
Project includes `.vscode/launch.json`. Open folder in VS Code and run **Launch CloudEncryptCLI**.

## ☁️ Provider configuration quick reference

| Provider | Required keys | Notes |
| --- | --- | --- |
| AWS | `region`, `keyId` | `keyId` can be an alias such as `alias/prod-app`. Credentials come from the default AWS SDK chain. |
| Azure | `keyId` | Use the full Key Vault key URL. Azure identity is resolved with the default credential chain. |
| GCP | `project`, `location`, `keyRing`, `key` | Application Default Credentials must be available (for example via `gcloud auth application-default login`). |
| OCI | `configFile`, `profile`, `endpoint` *(or `region` + `vault`)*, `keyId` | `configFile` defaults to `~/.oci/config`. `endpoint` is the vault's crypto endpoint, e.g. `https://<vault>-crypto.kms.us-ashburn-1.oraclecloud.com`. If you omit `endpoint`, provide both `region` and the vault name (`vault`). |

When you run `cloud-encrypt init`, the generated `.cloudencrypt.yml` seeds these keys with sensible placeholders based on the detected provider. Update them with your actual values before encrypting secrets.

The same configuration file now includes a `secret` block that seeds provider-specific defaults (for example `project` for GCP or `vaultUrl` for Azure). The `secret-put`, `secret-get`, and `secret-delete` commands merge those defaults with any `--set key=value` overrides you supply at runtime.
