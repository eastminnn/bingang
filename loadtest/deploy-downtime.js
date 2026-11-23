// 배포하는 동안 API가 얼마나 끊기는지 잰다 (#63)
//   k6 run -e BASE_URL=https://bingang.duckdns.org --out json=loadtest/results/<날짜>-deploy/raw.json loadtest/deploy-downtime.js
//   실행 중에 GitHub Actions에서 배포를 일으키고, 끝나면 실패 요청 수와 끊긴 구간을 본다
//
// 부하가 목적이 아니라 끊김을 보는 것이라 초당 5건만 보낸다 (200ms 단위로 끊긴 시간을 잴 수 있다)

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

export const options = {
	scenarios: {
		deploy: {
			executor: 'constant-arrival-rate',
			rate: 5,
			timeUnit: '1s',
			duration: __ENV.DURATION || '6m',
			preAllocatedVUs: 20,
			maxVUs: 50,
		},
	},
	// 응답이 늦어도 끊긴 것으로 본다
	thresholds: { http_req_failed: ['rate==0'] },
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

const params = {
	headers: { 'User-Agent': 'bingang-loadtest/1.0', Accept: 'application/json' },
	timeout: '3s',
	tags: { name: 'buildings' },
};

export default function () {
	const res = http.get(`${BASE_URL}/api/buildings`, params);
	if (!check(res, { '200': (r) => r.status === 200 })) {
		// 끊긴 구간을 시각으로 남긴다 (status 0은 연결 실패·시간 초과)
		console.warn(`FAIL ${new Date().toISOString()} status=${res.status}`);
	}
}
