#!/usr/bin/env bash
set -euo pipefail

BRANCH="feature/kms-file-encrypt"
REPO_URL="https://github.com/dscope-io/dscope-cloud-encrypt"

echo "🚀 Creating branch ${BRANCH}"
git checkout -b "$BRANCH"

echo "📦 Adding files..."
git add .
git commit -m "feat(kms): add multi-cloud file encryption via Cloud KMS"

echo "⬆️ Pushing branch..."
git push origin "$BRANCH"

echo "🌐 Opening compare page..."
open "${REPO_URL}/compare/${BRANCH}?expand=1"
