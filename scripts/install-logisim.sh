#!/usr/bin/env bash
set -euo pipefail

# Download Logisim jar into backend/logisim.
# You can override VERSION or DEST via env vars if needed.

VERSION="${VERSION:-3.8.0}"
DEST="${DEST:-backend/logisim/logisim-evolution.jar}"
URL="https://github.com/logisim-evolution/logisim-evolution/releases/download/v${VERSION}/logisim-evolution-${VERSION}-all.jar"

mkdir -p "$(dirname "$DEST")"

echo "Downloading Logisim ${VERSION} to ${DEST}"
curl -L --fail --progress-bar "$URL" -o "$DEST"

echo "Done. Configure backend logisim.jar-path to $(realpath "$DEST") or rely on default relative path."
