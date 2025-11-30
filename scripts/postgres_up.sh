#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-knohub-postgres}"
IMAGE="${IMAGE:-postgres:16}"
HOST_PORT="${HOST_PORT:-5432}"
DB_NAME="${DB_NAME:-knohub}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-@Geralt123}"
DATA_DIR="${DATA_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backend/postgres-data}"
# Optional: path to a SQL file to seed on first run
INIT_SQL="${INIT_SQL:-}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but not found. Please install Docker first." >&2
  exit 1
fi

# Check if a TCP port is free (localhost)
is_port_free() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tln | awk '{print $4}' | grep -E "[:.]${port}\$" >/dev/null && return 1 || return 0
  elif command -v lsof >/dev/null 2>&1; then
    lsof -Pi :"${port}" -sTCP:LISTEN -t >/dev/null && return 1 || return 0
  elif command -v nc >/dev/null 2>&1; then
    nc -z localhost "${port}" >/dev/null 2>&1 && return 1 || return 0
  fi
  # Fallback: assume free
  return 0
}

find_free_port() {
  local port="$1"
  for _ in $(seq 1 20); do
    if is_port_free "${port}"; then
      echo "${port}"
      return 0
    fi
    port=$((port + 1))
  done
  echo ""
  return 1
}

ensure_user_password() {
  echo "==> Ensuring database user ${DB_USER} has the configured password"
  docker exec -u postgres "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -d postgres -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}') THEN CREATE ROLE \"${DB_USER}\" LOGIN; END IF; EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', '${DB_USER}', '${DB_PASSWORD}'); END \$\$;" >/dev/null
}

wait_for_postgres() {
  local max_attempts=30
  local attempt=1
  while (( attempt <= max_attempts )); do
    if docker exec -u postgres "${CONTAINER_NAME}" pg_isready -h localhost -p 5432 -d postgres >/dev/null 2>&1; then
      echo "==> PostgreSQL is ready (attempt ${attempt})"
      return 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done
  echo "!! PostgreSQL did not become ready after ${max_attempts} seconds."
  return 1
}

# Configure Docker daemon proxy from env (HTTP_PROXY/HTTPS_PROXY/NO_PROXY) if present
configure_docker_proxy() {
  local current_http="${HTTP_PROXY:-${http_proxy:-}}"
  local current_https="${HTTPS_PROXY:-${https_proxy:-${current_http}}}"
  local current_no="${NO_PROXY:-${no_proxy:-localhost,127.0.0.1}}"

  if [[ -z "${current_http}" && -z "${current_https}" ]]; then
    echo "==> No HTTP_PROXY/HTTPS_PROXY detected, skipping Docker proxy configuration."
    return 0
  fi

  if ! command -v systemctl >/dev/null 2>&1; then
    echo "==> systemctl not available; cannot configure Docker proxy automatically."
    return 0
  fi

  echo "==> Configuring Docker daemon proxy (requires sudo)..."
  echo "    HTTP:  ${current_http}"
  echo "    HTTPS: ${current_https}"
  echo "    NO:    ${current_no}"

  sudo mkdir -p /etc/systemd/system/docker.service.d
  cat <<EOF | sudo tee /etc/systemd/system/docker.service.d/http-proxy.conf >/dev/null
[Service]
Environment="HTTP_PROXY=${current_http}"
Environment="HTTPS_PROXY=${current_https}"
Environment="NO_PROXY=${current_no}"
EOF

  sudo systemctl daemon-reload
  sudo systemctl restart docker
  echo "==> Docker restarted with proxy settings."
}

configure_docker_proxy

echo "==> Preparing data directory at ${DATA_DIR}"
mkdir -p "${DATA_DIR}"

# Determine whether this looks like a first run (no data present yet)
first_run="no"
if [ -z "$(ls -A "${DATA_DIR}" 2>/dev/null)" ]; then
  first_run="yes"
fi

# If container exists, just start it (will reuse mounted data)
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}\$"; then
  echo "==> Container ${CONTAINER_NAME} exists, starting it..."
  host_port_mapped="$(docker inspect -f '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' "${CONTAINER_NAME}" 2>/dev/null || echo "${HOST_PORT}")"
  if ! is_port_free "${host_port_mapped}"; then
    echo "!! Host port ${host_port_mapped} (from existing container mapping) is already in use."
    echo "   Stop the process using it, or remove the container and rerun with HOST_PORT set to a free port."
    exit 1
  fi
  docker start "${CONTAINER_NAME}" >/dev/null
else
  chosen_port="$(find_free_port "${HOST_PORT}")"
  if [[ -z "${chosen_port}" ]]; then
    echo "No free port found starting from ${HOST_PORT}. Please specify a free port via HOST_PORT." >&2
    exit 1
  fi
  if [[ "${chosen_port}" != "${HOST_PORT}" ]]; then
    echo "==> Port ${HOST_PORT} is busy, switching to ${chosen_port}"
  fi

  echo "==> Running new PostgreSQL container ${CONTAINER_NAME}"
  run_cmd=(
    docker run -d
    --name "${CONTAINER_NAME}"
    -e POSTGRES_DB="${DB_NAME}"
    -e POSTGRES_USER="${DB_USER}"
    -e POSTGRES_PASSWORD="${DB_PASSWORD}"
    -p "${chosen_port}:5432"
    -v "${DATA_DIR}:/var/lib/postgresql/data"
  )

  if [[ -n "${INIT_SQL}" && -f "${INIT_SQL}" ]]; then
    run_cmd+=(-v "${INIT_SQL}:/docker-entrypoint-initdb.d/$(basename "${INIT_SQL}")")
    echo "==> Seeding from ${INIT_SQL} on first boot"
  fi

  "${run_cmd[@]}" "${IMAGE}" >/dev/null
  host_port_mapped="${chosen_port}"
fi

wait_for_postgres
ensure_user_password

echo "==> PostgreSQL is starting"
if [ "${first_run}" = "yes" ]; then
  echo "    New data directory initialized at ${DATA_DIR}"
else
  echo "    Reusing existing data at ${DATA_DIR}"
fi
echo "    Connection: postgres://${DB_USER}:${DB_PASSWORD}@localhost:${host_port_mapped:-${chosen_port:-$HOST_PORT}}/${DB_NAME}"
echo "    JDBC URL: jdbc:postgresql://localhost:${host_port_mapped:-${chosen_port:-$HOST_PORT}}/${DB_NAME}"
echo "Tip: stop with 'docker stop ${CONTAINER_NAME}', remove with 'docker rm ${CONTAINER_NAME}'."
