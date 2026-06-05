# API Docs Guide

## 0. 목적

`docs/improvements/`는 API별 분석, 설계, 구현 기록, 검증 결과를 남기는 폴더다.

여기에는 "완성된 공식 문서"만 두는 것이 아니라, 실제 개선 작업 중 생긴 근거와 판단을 함께 둔다.

---

## 1. topic 폴더 규칙

주제별로 폴더를 하나 만든다.

예시

- `docs/improvements/playback/`
- `docs/improvements/home/`

한 폴더 안에는 하나의 주제만 둔다.

---

## 2. 권장 파일 구성

모든 파일을 항상 다 만들 필요는 없다.

작업 규모에 따라 아래에서 필요한 것만 쓴다.

- `001-overview.md`
  - 작업 전체 한 페이지 요약
- `002-as-is.md`
  - 현재 구조와 문제 분석
- `003-adr.md`
  - 대안 비교와 선택 이유
- `004-implementation.md`
  - 구현 로그와 변경 이유
- `005-benchmark.md`
  - before/after 검증
- `05-retrospective.md`
  - 회고
- `ai-collaboration.md`
  - AI 협업 핵심 기록

작은 작업은 아래 2~3개면 충분하다.

- `001-overview.md`
- `002-as-is.md`
- `003-adr.md`

---

## 3. 파일명 원칙

- 숫자는 읽는 순서를 맞추기 위한 것이다.
- 숫자는 `001-`, `002-`처럼 3자리 0패딩 형식을 기본으로 사용한다.
- 한글/영문은 혼용 가능하지만 주제 폴더 안에서는 일관성을 유지한다.
- 임시 메모는 가능하면 최종적으로 정리된 문서로 흡수한다.
- 대안 비교, 측정 결과, 작업 단계, 리스크는 가능하면 표로 정리한다.
- query 세팅처럼 메인이 아닌 준비 단계는 별도 문서를 만들기보다 `001-overview.md`나 `003-adr.md` 안의 짧은 섹션으로 끝내도 된다.

---

## 4. 추천 작성 순서

1. topic 폴더 생성
2. `001-overview.md`에 목표와 범위만 먼저 적기
3. `002-as-is.md`에 현재 구조와 문제 정리
4. `003-adr.md`에 대안 비교와 선택 이유 정리
5. 구현이 시작되면 `004-implementation.md` 추가
6. 재측정하면 `005-benchmark.md` 추가

---

## 5. AI 협업 시 원칙

- 전체 대화 로그를 그대로 남기지 않는다.
- 결정이 바뀐 순간, 대안 비교 결과, 최종 선택 이유만 남긴다.
- 문서는 "나중에 내가 다시 읽었을 때 이해되는가" 기준으로 쓴다.

---

## 6. 함께 읽을 문서

- 전역 규칙: 루트 `AGENTS.md`
- 작업 절차: `docs/ai/workflows/api-improvement-workflow.md`
- 새 주제 시작 템플릿: `docs/ai/templates/api-topic-template.md`
