# Jira 티켓: Perforce Conflict 해결 AI 에이전트 — 벤치마크 데이터셋 구축

---

## 티켓 기본 정보

| 항목 | 내용 |
|------|------|
| **이슈 유형** | Subtask |
| **상위 이슈** | [부모 티켓 번호 — AI-assisted Perforce Conflict Resolution Agent] |
| **컴포넌트** | Agent Evaluation / Test Infrastructure |
| **우선순위** | High |
| **레이블** | `ai-agent`, `benchmark`, `perforce`, `conflict-resolution` |
| **담당자** | minkyeongk |

---

## 요약 (Summary)

> Perforce submit 충돌 시나리오를 재현한 벤치마크 데이터셋 구축 및 Git/P4 충돌 구조 차이 문서화

---

## 배경 및 목적 (Background)

현재 팀에서 개발 중인 AI 에이전트는 Perforce 워크스페이스에서 `p4 submit` 실패 시
발생하는 충돌을 자동으로 탐지·해결하는 것을 목표로 한다.

에이전트 학습 및 평가를 위해서는 **실제 P4 충돌 상황과 최대한 동일한 형태의 테스트 케이스**가
필요하다. 그러나 순수 Perforce 환경은 재현성과 버전 관리가 어렵기 때문에, Git 레포지토리를
활용하여 P4 충돌 구조를 충실히 모사한 벤치마크 데이터셋을 구축한다.

또한, Git과 Perforce 간의 충돌 파일 구조 차이를 명확히 문서화하여
에이전트 개발·검토 과정에서 팀원 간 공통 이해를 확보한다.

---

## 범위 (Scope)

- **In scope:** Android Telephony 프레임워크 코드를 기반으로 한 충돌 시나리오 3건 구축,
  Git/P4 충돌 구조 비교 문서 작성
- **Out of scope:** 실제 Perforce 서버 연동, 에이전트 학습 파이프라인, 자동 평가 스크립트

---

## Action Items

### 1. 벤치마크 시나리오 설계 및 구현

- [x] **실제 Perforce 충돌 구조 분석**
  - `p4 resolve -am` 이 생성하는 마커 형식 확인 (`yours` / `theirs` + depot 경로)
  - `p4 describe <CL>` 출력 구조 파악 (Change, User, Date, Description, Files 섹션)
  - Git 마커(`HEAD` / branch명)와의 차이점 정리

- [x] **충돌 케이스 3건 구현 (Android Telephony 코드 기반)**
  - `conflict/1-token-util` — `PackageBasedTokenUtil.java`
    - CL:89432 (feature: token cache) vs CL:89501 (fix: SHA-256→512 업그레이드)
    - 충돌 구간 4개: import, 상수 필드, generateToken() 진입부, generateToken() 말미
  - `conflict/2-otp-handler` — `InboundSmsHandlerExt.java`
    - CL:91205 (feature: GmsCore OTP 로깅) vs CL:91318 (fix: SMS 권한 강화)
    - 충돌 구간 2개: TAG 상수, processSmsRetrieverMatchedPackage() 전체
  - `conflict/3-sms-permissions` — `SmsPermissions.java`
    - CL:93710 (feature: SMS 전송 rate limit) vs CL:93845 (fix: AppOps silent denial)
    - 충돌 구간 5개: import, 상수, 인스턴스 필드, checkCallingCanSendSms(), checkCallingOrSelfCanSendSms()

- [x] **P4 스타일 마커로 변환**
  - 기존 Git 브랜치명 마커 → `yours:파일명 (CL:XXXXX ...)` / `theirs:파일명 (CL:YYYYY ...)` 형식으로 교체
  - 각 브랜치 커밋 메시지를 `CONFLICT: resolve CL:X vs CL:Y in 파일명` 형식으로 변경

- [x] **`p4-conflict-context.txt` 작성 (케이스별)**
  - 실제 `p4 describe` 출력을 모사한 형식
  - Your CL / Their CL 각각: Change, Date, Client, User, Status, Description, Affected files
  - 충돌 구간 목록 및 해결 방향 가이드 포함

### 2. 문서화

- [x] **Git vs Perforce 충돌 구조 비교 문서 작성** (`docs/vcs-conflict-comparison.md`)
  - 충돌 발생 시점 및 워크플로 차이
  - 마커 문법 비교 (실제 코드 예시 포함)
  - 커밋 메시지 vs Changelist description 형식 비교
  - 에이전트 관점에서의 입력 차이 정리

- [ ] **에이전트 평가 기준 문서 추가**
  - 각 케이스의 "정답 해결본" (ground truth) 작성
  - 평가 지표 정의: 컴파일 가능 여부, 양쪽 의도 보존 여부, 불필요한 코드 제거 여부

### 3. 검증

- [ ] **시나리오 현실성 검토**
  - 실제 P4 운영 경험이 있는 팀원에게 마커 형식 및 context 파일 내용 리뷰 요청
  - `p4 resolve` 실제 출력물과 비교하여 형식 보정

- [ ] **에이전트 dry-run 테스트**
  - 3개 케이스 각각에 에이전트 적용하여 해결 가능 여부 확인
  - 실패 케이스는 시나리오 난이도/명확성 재검토

---

## Deliverables

| # | 산출물 | 위치 | 상태 |
|---|--------|------|------|
| 1 | 충돌 시나리오 브랜치 3개 (P4 마커 + context 파일) | `conflict/1-token-util`, `conflict/2-otp-handler`, `conflict/3-sms-permissions` | ✅ 완료 |
| 2 | Git vs Perforce 충돌 구조 비교 문서 | `docs/vcs-conflict-comparison.md` | ✅ 완료 |
| 3 | 에이전트 평가 기준 문서 (ground truth + 평가 지표) | `docs/evaluation-criteria.md` | ⬜ 미착수 |
| 4 | 시나리오 현실성 리뷰 완료 및 보정 | (변경 시 해당 브랜치 업데이트) | ⬜ 미착수 |
| 5 | 에이전트 dry-run 결과 보고서 | `docs/dryrun-results.md` | ⬜ 미착수 |

---

## 인수 조건 (Acceptance Criteria)

1. **마커 형식**: 모든 충돌 파일의 마커가 `<<<<<<< yours:파일명 (CL:XXXXX)` / `>>>>>>> theirs:파일명 (CL:YYYYY)` 형식을 따를 것
2. **컨텍스트 파일**: 각 브랜치에 `p4-conflict-context.txt`가 존재하고, 두 CL의 의도를 명확히 설명할 것
3. **충돌 복잡도**: 각 케이스는 최소 2개 이상의 충돌 구간을 포함하며, 단순 라인 충돌이 아닌 **로직 의미 충돌**을 포함할 것
4. **비교 문서**: Git/P4 마커 형식, 커밋/CL 메시지 형식, 워크플로 차이가 실제 예시 코드와 함께 문서화될 것
5. **재현성**: 각 브랜치를 체크아웃하면 항상 동일한 충돌 상태가 재현될 것

---

## 참고 자료

- `docs/vcs-conflict-comparison.md` — Git vs Perforce 충돌 구조 비교 (이 태스크 산출물)
- Perforce 공식 문서: `p4 resolve`, `p4 describe` 명령어 레퍼런스
- 각 충돌 브랜치의 `p4-conflict-context.txt`
