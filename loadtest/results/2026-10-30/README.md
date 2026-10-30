# 부하 테스트 결과 (2026-10-30)

운영 서버(OCI A1, 2 OCPU / 12GB)에 GitHub Actions의 k6로 부하를 걸고, 같은 시간대의 Grafana "빈강 운영" 대시보드를 캡처했다. 수치 요약은 [#35](https://github.com/eastminnn/bingang/issues/35).

| 테스트 | 부하 | 서버 p95 (최대) | 에러 | CPU 최대 | 캡처 |
|---|---|---|---|---|---|
| baseline | 10 TPS, 3분 | 42.7ms | 0% | 13% | [1-baseline.png](1-baseline.png) |
| load | 100 TPS, 10분 | 7.0ms | 0% | 33% | [2-load.png](2-load.png) |
| spike | 10 → 150 TPS (10초) | 4.2ms | 0% | - | [3-spike.png](3-spike.png) |
| stress | 50 → 400 TPS | 8.1ms | 0% | 75% | [4-stress.png](4-stress.png) |

- 대시보드 그래프는 5분 평균(`rate[5m]`)이라 순간 최고치보다 완만하다. 표의 수치는 1분 기준
- 수집기 패널은 테스트 당시 아직 수집 기록이 없어 비어 있다
