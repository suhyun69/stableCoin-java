#!/usr/bin/env bash
# 로컬 이더리움 노드(anvil) 종료
# 사용법: ./anvil/stop.sh        ANVIL_PORT(기본 8545)의 anvil 종료
#         ./anvil/stop.sh --all  실행 중인 모든 anvil 종료
set -euo pipefail

PORT="${ANVIL_PORT:-8545}"

if [ "${1:-}" = "--all" ]; then
  pids=$(pgrep -x anvil || true)
  target="모든 anvil"
else
  pids=$(lsof -a -c anvil -ti "tcp:$PORT" -sTCP:LISTEN || true)
  target="포트 $PORT의 anvil"
fi

if [ -z "$pids" ]; then
  echo "실행 중인 $target 이(가) 없습니다."
  exit 0
fi

kill $pids

# 최대 5초간 종료 대기
for _ in $(seq 1 50); do
  alive=""
  for pid in $pids; do
    kill -0 "$pid" 2>/dev/null && alive="$alive $pid"
  done
  [ -z "$alive" ] && break
  sleep 0.1
done

if [ -n "$alive" ]; then
  echo "$target 이(가) 종료되지 않았습니다 (PID:$alive)." >&2
  exit 1
fi

echo "$target 종료 완료 (PID: $(echo $pids))"
