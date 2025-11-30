#!/usr/bin/env bash
# Source this file to export backend DB env vars pointing at the local Postgres container.
# Usage: source ./scripts/postgres_env.sh

export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/knohub}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD='@Geralt123'
export DB_DRIVER="${DB_DRIVER:-org.postgresql.Driver}"
export DB_DIALECT="${DB_DIALECT:-org.hibernate.dialect.PostgreSQLDialect}"

echo "Exported DB_URL=${DB_URL}"
echo "Exported DB_USERNAME=${DB_USERNAME}"
echo "Exported DB_PASSWORD (hidden)"
echo "Exported DB_DRIVER=${DB_DRIVER}"
echo "Exported DB_DIALECT=${DB_DIALECT}"
