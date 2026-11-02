#!/usr/bin/env bash
# 로컬 이더리움 노드(anvil) 실행
set -euo pipefail

export PATH="$PATH:$HOME/.foundry/bin"

CHAIN_ID="${CHAIN_ID:-31337}"
HOST="${ANVIL_HOST:-127.0.0.1}"
PORT="${ANVIL_PORT:-8545}"

if ! command -v anvil >/dev/null 2>&1; then
  echo "anvil이 없습니다. 먼저 ./anvil/install.sh 를 실행하세요." >&2
  exit 1
fi

exec anvil --chain-id "$CHAIN_ID" --host "$HOST" --port "$PORT"
