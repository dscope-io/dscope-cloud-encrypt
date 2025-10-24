#!/usr/bin/env bash
set -e
APP_NAME="cloud-encrypt"
INSTALL_DIR="$HOME/.local/bin"
JAR="target/cloud-encrypt-cli-1.3.0-shaded.jar"

if ! command -v mvn >/dev/null 2>&1; then echo "Maven not found"; exit 1; fi
if ! command -v java >/dev/null 2>&1; then echo "Java not found"; exit 1; fi

mvn -q -DskipTests package
mkdir -p "$INSTALL_DIR"
cp "$JAR" "$INSTALL_DIR/${APP_NAME}.jar"

cat <<'WRAP' > "$INSTALL_DIR/${APP_NAME}"
#!/usr/bin/env bash
detect_provider(){
  if command -v aws >/dev/null 2>&1 && aws sts get-caller-identity >/dev/null 2>&1; then echo aws; return; fi
  if command -v az >/dev/null 2>&1 && az account show >/dev/null 2>&1; then echo azure; return; fi
  if command -v gcloud >/dev/null 2>&1 && gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then echo gcp; return; fi
  echo ""
}
if [[ $# -eq 1 && -f "$1" || $# -eq 1 && ! "$1" =~ ^(--|aws|azure|gcp) ]]; then
  if [[ -f "$1" ]]; then
    java -jar "$HOME/.local/bin/${APP_NAME}.jar" "$1"
  else
    PROV=$(detect_provider)
    if [[ -z "$PROV" ]]; then echo "No cloud detected; specify provider"; exit 1; fi
    java -jar "$HOME/.local/bin/${APP_NAME}.jar" "$1"
  fi
else
  java -jar "$HOME/.local/bin/${APP_NAME}.jar" "$@"
fi
WRAP
chmod +x "$INSTALL_DIR/${APP_NAME}"
echo "Installed wrapper at $INSTALL_DIR/${APP_NAME}"
echo "Add to PATH if needed: export PATH=\$PATH:$INSTALL_DIR"
