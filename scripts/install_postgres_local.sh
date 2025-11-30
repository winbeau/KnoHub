#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${DB_NAME:-knohub}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-@Geralt123}"

echo "==> Installing PostgreSQL via apt (requires sudo)..."
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql

echo "==> Ensuring PostgreSQL service is running..."
sudo systemctl enable postgresql
sudo systemctl start postgresql

echo "==> Configuring user..."
if sudo -u postgres bash -lc "cd /tmp && psql -tAc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\"" | grep -q 1; then
  sudo -u postgres bash -lc "cd /tmp && psql -c \"ALTER ROLE \\\"${DB_USER}\\\" WITH LOGIN PASSWORD '${DB_PASSWORD}'\""
else
  sudo -u postgres bash -lc "cd /tmp && psql -c \"CREATE ROLE \\\"${DB_USER}\\\" WITH LOGIN PASSWORD '${DB_PASSWORD}'\""
fi

echo "==> Configuring database..."
if ! sudo -u postgres bash -lc "cd /tmp && psql -tAc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\"" | grep -q 1; then
  sudo -u postgres bash -lc "cd /tmp && psql -c \"CREATE DATABASE \\\"${DB_NAME}\\\" OWNER \\\"${DB_USER}\\\"\""
else
  echo "Database ${DB_NAME} already exists, skipping creation."
fi

echo "==> PostgreSQL ready."
echo "    URL: postgres://${DB_USER}:${DB_PASSWORD}@localhost:5432/${DB_NAME}"
echo "    JDBC: jdbc:postgresql://localhost:5432/${DB_NAME}"
