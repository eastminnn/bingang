// 빈강 부하 테스트 (k6)
//   k6 run -e PROFILE=load -e BASE_URL=https://bingang.duckdns.org loadtest/scenario.js
//
// 요청 비율은 학생 한 명이 한 번 쓸 때의 행동을 흉내 낸다 (#35)
//   건물 강의실 조회(지금) 55% · 건물 목록 25% · 시간을 바꾼 강의실 조회 15% · 학사 일정 5%
// TPS를 고정하는 arrival-rate 방식이라 서버가 느려져도 요청이 줄지 않는다 (실제 학생처럼)
//
// 판단은 서버 쪽 p95(Grafana)로 한다. 여기서 재는 시간에는 네트워크 왕복이 더해진다

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const PROFILE = __ENV.PROFILE || 'smoke';

const PROFILES = {
	// 스크립트가 도는지만 확인
	smoke: { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: '20s' },
	// 한가할 때의 기준값
	baseline: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '3m' },
	// SRS N-9 목표: 100 TPS에서 p95 200ms, 에러율 1% 미만
	load: {
		executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s',
		stages: [{ target: 100, duration: '1m' }, { target: 100, duration: '10m' }],
	},
	// 쉬는 시간 정각처럼 10초 만에 몰리는 경우
	spike: {
		executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s',
		stages: [
			{ target: 10, duration: '1m' },
			{ target: 150, duration: '10s' },
			{ target: 150, duration: '2m' },
			{ target: 10, duration: '10s' },
			{ target: 10, duration: '1m' },
		],
	},
	// 1분마다 50씩 올려 어디서 무너지는지 찾는다
	stress: {
		executor: 'ramping-arrival-rate', startRate: 50, timeUnit: '1s',
		stages: [50, 100, 150, 200, 250, 300, 350, 400].map((target) => ({ target, duration: '1m' })),
	},
	// 메모리 누수·커넥션 고갈처럼 시간이 지나야 보이는 문제
	soak: { executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s', duration: '1h' },
};

if (!PROFILES[PROFILE]) {
	throw new Error(`알 수 없는 PROFILE: ${PROFILE} (${Object.keys(PROFILES).join(', ')})`);
}

export const options = {
	scenarios: {
		[PROFILE]: { ...PROFILES[PROFILE], preAllocatedVUs: 50, maxVUs: 600 },
	},
	thresholds: {
		http_req_failed: ['rate<0.01'],
		// 네트워크 왕복이 포함된 값이라 서버 목표(200ms)보다 넉넉하게 둔다
		http_req_duration: ['p(95)<500'],
		// 한계 테스트는 에러가 10%를 넘으면 더 볼 필요가 없으니 멈춘다
		...(PROFILE === 'stress'
			? { 'http_req_failed{scenario:stress}': [{ threshold: 'rate<0.1', abortOnFail: true, delayAbortEval: '30s' }] }
			: {}),
	},
	summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const params = (name) => ({
	headers: { 'User-Agent': 'bingang-loadtest/1.0', Accept: 'application/json' },
	tags: { name }, // URL마다 따로 집계하지 않고 기능별로 묶는다
});

export function setup() {
	const res = http.get(`${BASE_URL}/api/buildings`, params('setup'));
	check(res, { '건물 목록 200': (r) => r.status === 200 });
	const codes = res.json('buildings').map((b) => b.code);
	if (codes.length === 0) {
		throw new Error('건물이 없습니다. 시간표 수집이 끝난 서버에서 실행하세요');
	}
	return { codes };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];

// 오늘 이후 가장 가까운 평일 오전 9시~오후 6시 중 임의의 30분 단위 시각 (한국 시간)
function randomAt() {
	const now = new Date(Date.now() + 9 * 3600 * 1000);
	const day = new Date(now);
	day.setUTCDate(now.getUTCDate() + Math.floor(Math.random() * 5));
	while (day.getUTCDay() === 0 || day.getUTCDay() === 6) {
		day.setUTCDate(day.getUTCDate() + 1);
	}
	const minutes = 9 * 60 + 30 * Math.floor(Math.random() * 18);
	const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
	const mm = String(minutes % 60).padStart(2, '0');
	return encodeURIComponent(`${day.toISOString().slice(0, 10)}T${hh}:${mm}:00+09:00`);
}

export default function (data) {
	const r = Math.random();
	let res;
	if (r < 0.55) {
		res = http.get(`${BASE_URL}/api/buildings/${pick(data.codes)}/rooms`, params('rooms'));
	}
	else if (r < 0.80) {
		res = http.get(`${BASE_URL}/api/buildings`, params('buildings'));
	}
	else if (r < 0.95) {
		res = http.get(`${BASE_URL}/api/buildings/${pick(data.codes)}/rooms?at=${randomAt()}`, params('rooms_at'));
	}
	else {
		res = http.get(`${BASE_URL}/api/academic-events`, params('academic_events'));
	}
	check(res, { '200': (r) => r.status === 200 });
}
