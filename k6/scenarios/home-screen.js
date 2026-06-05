import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, THRESHOLDS, STAGES } from '../config.js';
import { getAuthHeaders } from '../helpers/auth.js';

// -------------------------------------------------------
// 시나리오: 홈 화면 진입
//
// 사용자가 홈 화면에 진입하면 프론트엔드가 6개 API를 호출한다.
// 이 시나리오는 1 iteration = 6 HTTP requests로 실제 동작을 시뮬레이션.
//
// 포함 API:
//   1. GET /playlists/trending         - 인기 차트 (북마크 수 기준 인기순)
//   2. GET /playlists/recommend        - OO님이 좋아할만한 (태그 가중치 기반 추천)
//   3. GET /playlists/tags/top?index=0 - 선호태그 1순위 콘텐츠
//   4. GET /playlists/tags/top?index=1 - 선호태그 2순위 콘텐츠
//   5. GET /playlists/tags/top?index=2 - 선호태그 3순위 콘텐츠
//   6. GET /playlists/history          - 시청이력 (최근 시청 영상 목록)
//
// 사용법:
//   k6 run --env LOAD=smoke k6/scenarios/home-screen.js       # 동작 확인
//   k6 run --env LOAD=vu100 k6/scenarios/home-screen.js       # VU 100
//   k6 run --env LOAD=vu1000 k6/scenarios/home-screen.js      # VU 1,000
//
//   # Grafana 모니터링 연동
//   k6 run --env LOAD=vu1000 --out influxdb=http://localhost:8086/k6 k6/scenarios/home-screen.js
//
//   # 결과 JSON 저장 (Before/After 비교용)
//   k6 run --env LOAD=vu1000 --out json=k6/results/before/home-screen-vu1000.json k6/scenarios/home-screen.js
//
// 주요 지표 해석:
//   http_req_duration            : 개별 API 응답 시간
//   http_req_duration{name:XXX}  : API별 응답 시간 (tag로 분리)
//   group_duration               : 홈 화면 전체 로드 시간 (6 request 묶음)
//   http_reqs                    : 초당 처리량 (RPS)
//   http_req_failed              : 에러율 (1% 미만이어야 정상)
//
// Before/After 비교 포인트:
//   - recommend의 p95가 가장 높을 것으로 예상 (동적 CASE WHEN 쿼리)
//   - history도 조건부 JOIN+GROUP BY로 높을 수 있음
//   - 개선 후 동일 VU로 재측정하여 개선율(%) 산출
// -------------------------------------------------------

export const options = {
    stages: STAGES[__ENV.LOAD || 'vu100'],
    thresholds: {
        ...THRESHOLDS,
        // API별 임계값 (tag name으로 분리 측정)
        // Before에서 이 기준을 넘기는 API = 개선 대상
        'http_req_duration{name:trending}':   ['p(95)<500'],
        'http_req_duration{name:recommend}':  ['p(95)<500'],
        'http_req_duration{name:tags_top_0}': ['p(95)<500'],
        'http_req_duration{name:tags_top_1}': ['p(95)<500'],
        'http_req_duration{name:tags_top_2}': ['p(95)<500'],
        'http_req_duration{name:history}':    ['p(95)<500'],
    },
};

export default function () {
    const params = getAuthHeaders();

    // -------------------------------------------------------
    // group: 홈 화면 전체를 하나의 묶음으로 측정
    // → group_duration 지표로 "홈 진입 1회 총 소요 시간" 확인 가능
    // → Grafana에서 group별 차트로 전체 로드 시간 추이 확인
    // -------------------------------------------------------
    group('홈 화면 진입', function () {

        // 1. 인기 차트
        // 북마크 수 기준 인기 콘텐츠. 모든 사용자 동일 결과 → 캐싱 후보.
        const trending = http.get(
            `${BASE_URL}/playlists/trending?page=0&size=20`,
            Object.assign({}, params, { tags: { name: 'trending' } })
        );
        check(trending, {
            '[trending] status 200': (r) => r.status === 200,
        });

        // 2. OO님이 좋아할만한 (추천 플레이리스트)
        // 사용자별 선호 태그 가중치 기반. 동적 CASE WHEN 쿼리 → 가장 무거울 것으로 예상.
        const recommend = http.get(
            `${BASE_URL}/playlists/recommend?page=0&size=20`,
            Object.assign({}, params, { tags: { name: 'recommend' } })
        );
        check(recommend, {
            '[recommend] status 200': (r) => r.status === 200,
        });

        // 3~5. 선호태그 순위별 top3 (index 0, 1, 2)
        // 사용자의 상위 3개 선호 태그별 콘텐츠 목록.
        // 홈 화면에서 3번 연속 호출됨.
        for (let i = 0; i < 3; i++) {
            const tagsTop = http.get(
                `${BASE_URL}/playlists/tags/top?index=${i}&page=0&size=20`,
                Object.assign({}, params, { tags: { name: `tags_top_${i}` } })
            );
            check(tagsTop, {
                [`[tags_top_${i}] status 200`]: (r) => r.status === 200,
            });
        }

        // 6. 시청이력 (최근 시청 영상 목록)
        // 조건부 JOIN + GROUP BY + MAX 서브쿼리 → 복잡 쿼리 개선 후보.
        const history = http.get(
            `${BASE_URL}/playlists/history?page=0&size=20`,
            Object.assign({}, params, { tags: { name: 'history' } })
        );
        check(history, {
            '[history] status 200': (r) => r.status === 200,
        });
    });

    // think time: 사용자가 홈 화면 결과를 보는 시간 (1초)
    // 제거하면 요청을 쉬지 않고 연속으로 쏨 → 순수 서버 처리 한계 측정
    sleep(1);
}
