#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="knohub-postgres"
IMAGE="postgres:16"
HOST_PORT="${HOST_PORT:-5432}"
DB_NAME="${DB_NAME:-knohub}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-@Geralt123}"
DATA_DIR="${DATA_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backend/postgres-data}"

# Optional proxy: honor existing env (HTTP_PROXY/HTTPS_PROXY/ALL_PROXY, lowercase compatible).
resolved_http="${HTTP_PROXY:-${http_proxy:-}}"
resolved_https="${HTTPS_PROXY:-${https_proxy:-${resolved_http}}}"
resolved_all="${ALL_PROXY:-${all_proxy:-}}"
if [[ -n "${resolved_http}" || -n "${resolved_https}" || -n "${resolved_all}" ]]; then
  export HTTP_PROXY="${resolved_http}"
  export HTTPS_PROXY="${resolved_https}"
  export ALL_PROXY="${resolved_all}"
  echo "==> Using proxy HTTP: ${HTTP_PROXY:-unset}, HTTPS: ${HTTPS_PROXY:-unset}, SOCKS: ${ALL_PROXY:-unset}"
fi

echo "==> Preparing data directory at ${DATA_DIR}"
mkdir -p "${DATA_DIR}"

# If container exists, start it; otherwise create
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}\$"; then
  echo "==> Container ${CONTAINER_NAME} already exists, starting it..."
  docker start "${CONTAINER_NAME}"
else
  echo "==> Running new PostgreSQL container ${CONTAINER_NAME}"
  docker run -d \
    --name "${CONTAINER_NAME}" \
    -e POSTGRES_DB="${DB_NAME}" \
    -e POSTGRES_USER="${DB_USER}" \
    -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
    -p "${HOST_PORT}:5432" \
    -v "${DATA_DIR}:/var/lib/postgresql/data" \
    "${IMAGE}"
fi

echo "==> PostgreSQL is starting. Connection info:"
echo "    URL: postgres://${DB_USER}:${DB_PASSWORD}@localhost:${HOST_PORT}/${DB_NAME}"
echo "    DB_URL env for app: jdbc:postgresql://localhost:${HOST_PORT}/${DB_NAME}"
echo "Tip: stop with 'docker stop ${CONTAINER_NAME}', remove with 'docker rm ${CONTAINER_NAME}'."
