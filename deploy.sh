#!/usr/bin/env bash
# 블루-그린 배포 (#63). 서버의 ~/bingang에서 실행한다 (GitHub Actions가 코드를 옮긴 뒤 호출)
#
#   ① 이미지 빌드 (그동안 옛 색이 계속 서비스)
#   ② 반대 색을 띄우고 healthy가 될 때까지 기다린다. 실패하면 새 색만 끄고 끝낸다 (옛 색은 그대로)
#   ③ 나머지 서비스(Caddy·모니터링) 갱신
#   ④ Caddy 헬스 체크가 새 색을 알아챌 때까지 기다린다
#   ⑤ 옛 색을 끈다 (graceful shutdown으로 처리 중인 요청은 마친다)
set -euo pipefail
cd "$(dirname "$0")"

log() { echo "[deploy] $*"; }

# 빌드 출처 정보(attestation)에는 빌드 시각이 들어가 내용이 같아도 이미지 ID가 바뀐다 (containerd 이미지 저장소).
# 그러면 compose가 바뀐 게 없는 Caddy·모니터링까지 매번 다시 만들어 Caddy가 재시작하는 동안 요청이 끊긴다
export BUILDX_NO_DEFAULT_ATTESTATIONS=1

# 실제로 요청을 받을 수 있는 색인지 (running이어도 unhealthy면 아니다)
healthy() {
	local id
	id=$(docker compose ps -q --status running "api-${1}")
	[ -n "${id}" ] && [ "$(docker inspect -f '{{.State.Health.Status}}' "${id}")" = healthy ]
}

# ① blue·green은 같은 이미지(bingang-api)를 쓴다
log "이미지 빌드"
docker compose build

# ② 서비스 중(healthy)인 색의 반대로 배포한다. 둘 다 아니면(처음 전환) blue
if healthy blue; then
	current=blue next=green
elif healthy green; then
	current=green next=blue
else
	current="" next=blue
fi
log "현재 ${current:-없음} → 새 버전 ${next}"

# --wait: compose의 healthcheck(/actuator/health)가 healthy가 될 때까지 기다린다 (start_period 120s)
if ! docker compose up -d --no-deps --force-recreate --wait --wait-timeout 180 "api-${next}"; then
	log "api-${next}: healthy가 되지 않아 배포를 멈춥니다 (계속 서비스: api-${current:-없음})"
	docker compose logs --tail 50 "api-${next}" || true
	docker compose stop "api-${next}" || true
	exit 1
fi

# ③ api를 뺀 나머지. --remove-orphans는 블루-그린 이전의 api 컨테이너도 정리한다 (처음 전환할 때 한 번)
others=$(docker compose config --services | grep -v '^api-')
# shellcheck disable=SC2086
docker compose up -d --remove-orphans $others

# ④ Caddy의 health_interval(2s)보다 넉넉하게 기다려 새 색이 트래픽을 받게 한다
sleep 5

# ⑤ 옛 색 종료. 다음 배포 때 반대 색으로 다시 만든다
if [ -n "${current}" ]; then
	log "api-${current} 종료"
	docker compose stop "api-${current}"
fi

log "완료: api-${next} 서비스 중"
