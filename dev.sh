#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null || { echo 'Docker Desktop is required.' >&2; exit 1; }
command -v java >/dev/null || { echo 'Java 21 is required.' >&2; exit 1; }
docker compose version >/dev/null
docker info >/dev/null 2>&1 || { echo 'Start Docker Desktop first.' >&2; exit 1; }

if [[ ! -f .env ]]; then
  cp .env.example .env
fi
# This is a local shell environment file maintained by the developer.
set -a
source .env
set +a

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT:-5433}/${POSTGRES_DB:-stablepay}}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-${POSTGRES_USER:-stablepay}}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-${POSTGRES_PASSWORD:-stablepay}}"
export ANVIL_RPC_URL="${ANVIL_RPC_URL:-http://localhost:${ANVIL_PORT:-8545}}"
export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:${KAFKA_PORT:-9092}}"

if command -v lsof >/dev/null && lsof -nP -iTCP:"${SERVER_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port ${SERVER_PORT:-8080} is in use. Stop the running API before starting ./dev.sh." >&2
  exit 1
fi

docker compose config --quiet
printf '%s\n' 'Resetting local PostgreSQL, Kafka and Anvil data and containers...'
docker compose down --volumes --remove-orphans
# Compose does not remove unused volumes after their service mount is removed.
legacy_volumes="$(docker volume ls --filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME:-stablepay}" \
  --filter 'label=com.docker.compose.volume=anvil-data' --format '{{.Name}}')"
while IFS= read -r legacy_volume; do
  if [[ -n "$legacy_volume" ]]; then
    docker volume rm "$legacy_volume"
  fi
done <<< "$legacy_volumes"
if ! docker compose up -d --wait --wait-timeout 120; then
  docker compose logs --tail 40
  exit 1
fi
printf '%s\n' "Starting API: http://localhost:${SERVER_PORT:-8080}" 'Wallets: /wallets | Swagger: /swagger-ui.html'
cd "$ROOT_DIR/api"
exec ./gradlew --no-daemon bootRun
