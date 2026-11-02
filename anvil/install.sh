#!/usr/bin/env bash
# Foundry(anvil, forge, cast) 설치 및 최신화
set -euo pipefail

FOUNDRY_BIN="$HOME/.foundry/bin"
export PATH="$PATH:$FOUNDRY_BIN"

if [ ! -x "$FOUNDRY_BIN/foundryup" ]; then
  echo "Foundry 설치 중..."
  curl -L https://getfoundry.sh/install | bash
fi

# foundryup은 anvil이 실행 중이면 실패하므로 업데이트를 건너뜀
if pgrep -x anvil >/dev/null 2>&1; then
  echo "anvil이 실행 중이라 업데이트를 건너뜁니다. ./anvil/stop.sh --all 로 노드를 종료한 뒤 다시 실행하세요."
else
  foundryup
fi

anvil --version
