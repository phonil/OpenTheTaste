// -------------------------------------------------------
// k6 공통 설정
//
// 이 파일은 모든 시나리오에서 import하여 사용한다.
// BASE_URL, 성능 임계값, 부하 단계를 정의한다.
// -------------------------------------------------------

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// export const BASE_URL = __ENV.BASE_URL || 'http://api-user:8080';

// -------------------------------------------------------
// 성능 임계값 (Thresholds)
//
// "이 기준을 넘기면 실패로 간주"하는 기준선.
// Before(Baseline)에서 이 기준을 넘기는 API가 개선 대상.
//
// p(50)  : 절반의 요청이 이 시간 이내 응답
// p(95)  : 95%의 요청이 이 시간 이내 (대부분의 사용자 경험)
// p(99)  : 99%의 요청이 이 시간 이내 (최악에 가까운 경우)
// rate   : 에러 비율 (0.01 = 1%)
// -------------------------------------------------------
export const THRESHOLDS = {
    http_req_duration: ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
};

// -------------------------------------------------------
// 부하 단계 (Stages)
//
// 각 프리셋 구성:
//   ramp-up  10초 : 서버 커넥션 풀/스레드 풀 정상 할당 시간
//   sustain  1분  : 안정 상태 측정 구간 (이 구간의 수치가 실제 성능)
//   ramp-down 5초 : 열린 커넥션 정리, 다음 테스트 영향 방지
//
// VU(Virtual User) = 동시에 요청을 보내는 가상 사용자 수
// 1 VU = 요청 → 응답 대기 → sleep → 다음 요청 루프 1개
//
// 사용법:
//   k6 run --env LOAD=vu1000 k6/scenarios/home-screen.js
//
// 수정 방법:
//   - sustain 시간 변경: duration을 '2m' 등으로 수정
//   - VU 수 변경: target 값 수정
//   - 프리셋 추가: vu2000 등 자유롭게 추가 가능
// -------------------------------------------------------
export const STAGES = {
    // 동작 확인용 (시나리오 오류 점검, VU 1개로 10초)
    smoke: [
        { duration: '10s', target: 1 },
    ],
    vu100: [
        { duration: '10s', target: 100 },
        { duration: '1m',  target: 100 },
        { duration: '5s',  target: 0 },
    ],
    vu500: [
        { duration: '10s', target: 500 },
        { duration: '1m',  target: 500 },
        { duration: '5s',  target: 0 },
    ],
    vu1000: [
        { duration: '10s', target: 1000 },
        { duration: '1m',  target: 1000 },
        { duration: '5s',  target: 0 },
    ],
    vu5000: [
        { duration: '10s', target: 5000 },
        { duration: '1m',  target: 5000 },
        { duration: '5s',  target: 0 },
    ],
    vu10000: [
        { duration: '10s', target: 10000 },
        { duration: '1m',  target: 10000 },
        { duration: '5s',  target: 0 },
    ],
};
