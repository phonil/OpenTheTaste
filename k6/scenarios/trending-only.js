import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THRESHOLDS } from '../config.js';
import { getAuthHeaders } from '../helpers/auth.js';

// -------------------------------------------------------
// 시나리오: Trending API 단독 부하 테스트
//
// 홈 화면 6개 API 중 Trending만 집중 측정.
// 개선 전후 비교용.
//
// 사용법:
//   k6 run k6/scenarios/trending-only.js
// -------------------------------------------------------

export const options = {
    stages: [
        { duration: '10s', target: 100 },
        { duration: '1m',  target: 100 },
        { duration: '5s',  target: 0 },
    ],
    thresholds: {
        ...THRESHOLDS,
        'http_req_duration{name:trending}': ['p(95)<500'],
    },
};

export default function () {
    const params = getAuthHeaders();

    const res = http.get(
        `${BASE_URL}/playlists/trending?page=0&size=20`,
        Object.assign({}, params, { tags: { name: 'trending' } })
    );

    check(res, {
        '[trending] status 200': (r) => r.status === 200,
    });

    sleep(1);
}
