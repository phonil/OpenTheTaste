import { SharedArray } from 'k6/data';

// -------------------------------------------------------
// VU별 개별 JWT 토큰 관리
//
// 왜 개별 토큰인가:
//   공유 토큰 = 모든 VU가 같은 member로 요청
//   → 1명의 데이터만 반복 조회 → 캐시 히트율 비정상 → Baseline 왜곡
//   개별 토큰 = 각 VU가 다른 member로 요청
//   → 실제 서비스처럼 다양한 사용자 데이터 분산 조회
//
// SharedArray:
//   모든 VU가 메모리를 공유하여 로드. 10,000 VU여도 파일 1번만 읽음.
//
// __VU:
//   k6가 부여하는 VU 고유 번호 (1부터 시작).
//   모듈러(%)로 토큰을 순환 사용.
//   예) 토큰 1,000개 + VU 5,000 → 1명당 5 VU 공유 (허용 범위)
//
// tokens.json 형식:
//   [{ "memberId": 1, "token": "eyJ..." }, ...]
//
// 생성 방법:
//   CommandLineRunner로 시드 사용자 1,000명의 JWT 일괄 발급
//   → k6/data/tokens.json 출력
// -------------------------------------------------------

const users = new SharedArray('users', function () {
    return JSON.parse(open('../data/tokens.json'));
});

/**
 * 현재 VU에 할당된 사용자의 인증 헤더 반환.
 * 각 VU는 고유한 member의 토큰을 사용한다.
 */
export function getAuthHeaders() {
    const user = users[(__VU - 1) % users.length];
    return {
        headers: {
            'Authorization': `Bearer ${user.token}`,
            'Content-Type': 'application/json',
        },
    };
}

/**
 * 현재 VU에 할당된 memberId 반환 (디버깅/로그용)
 */
export function getCurrentMemberId() {
    return users[(__VU - 1) % users.length].memberId;
}
