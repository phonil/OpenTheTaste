# AI Workflow Guide

## 0. 목적

이 폴더는 루트 `AGENTS.md`를 보완하는 작업 절차 문서를 둔다.

역할은 분명하게 나눈다.

- `AGENTS.md`: 전역 규칙, 승인 방식, 보고 형식, 명세 우선순위
- `docs/ai/workflows/*.md`: 작업 종류별 절차
- `docs/improvements/{topic}/`: 실제 주제별 산출물

즉, 규칙은 `AGENTS.md`, 방법은 `workflow`, 결과물은 `docs/improvements`에 둔다.

---

## 1. 가장 간단한 사용 순서

1. 작업 시작 전에 루트 `AGENTS.md`를 기준으로 삼는다.
2. 작업 종류에 맞는 workflow 문서를 고른다.
3. 주제가 정해지면 `docs/improvements/{topic}/` 폴더를 만든다.
4. topic 폴더 안에서 필요한 문서만 생성한다.
5. 각 단계마다 사용자 승인 후 진행한다.
6. 작업이 끝나면 검증 결과와 다음 액션을 남긴다.

---

## 2. 현재 제공 문서

- `api-improvement-workflow.md`
  - API 성능/구조 개선 작업용 기본 절차
- `api-topic-template.md`
  - 새 API 개선 주제 문서 초안 템플릿

---

## 3. 운영 원칙

- 문서를 너무 많이 만들지 않는다.
- 먼저 짧게 정리하고, 필요한 경우에만 확장한다.
- topic별 문서는 측정과 결정 근거를 남기는 데 집중한다.
- AI 협업 기록은 핵심만 남긴다. 전체 로그를 복붙하지 않는다.
- 대안 비교, 측정 결과, 작업 단계는 가능하면 표로 정리한다.

---

## 4. 추천 최소 산출물

작업이 작으면 아래 3개만 있어도 충분하다.

- `001-overview.md`
- `002-as-is.md`
- `003-adr.md`

구현과 측정까지 가면 아래를 추가한다.

- `004-implementation.md`
- `005-benchmark.md`

회고나 포트폴리오 정리가 필요할 때만 추가한다.

- `05-retrospective.md`
- `ai-collaboration.md`
