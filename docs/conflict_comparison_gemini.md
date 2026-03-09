# Git vs Perforce: Conflict 파일 구조 비교 및 에이전트 파싱 가이드

## 1. 충돌이 발생하는 시점 및 해결 워크플로

| 항목 | Git | Perforce (Helix Core) |
|------|-----|----------|
| **충돌 발생 시점** | `merge`, `rebase`, `cherry-pick` 실행 시 | `submit` 시도 또는 `sync` 실행 시 |
| **충돌 상태의 위치** | 작업 디렉토리 (unstaged 상태로 변경됨) | 서버 스케줄 상태 (`scheduled for resolve`) |
| **충돌 감지 명령어** | `git status` 또는 명령어의 Exit Code 1 반환 | `p4 submit` 실패 ("must resolve") 또는 `p4 resolve -n` |
| **마커 생성 명령어** | 명령어 실행 시 자동 삽입 | `p4 resolve -af` (강제 병합 실행 시) |
| **해결 완료 명령어** | 파일 수정 → `git add` → `git merge --continue` | 파일 수정 → 대화형 프롬프트에서 `ae` → `p4 submit` |
| **히스토리 단위** | Commit (SHA 해시) | Changelist (CL 번호) |

---

## 2. 충돌 마커 형식 비교

### 2.1. Git 마커

Git 충돌 마커는 **7글자** 기호(`<<<<<<<`, `=======`, `>>>>>>>`)를 사용하며, 실행 컨텍스트(`merge` vs `rebase`)와 `conflictstyle` 설정에 따라 레이블과 내부 구조가 크게 달라집니다.

#### 기본 Merge 충돌 (merge.conflictstyle = merge)
```text
<<<<<<< HEAD
// 내 로컬 브랜치의 변경 (Ours)
=======
// 상대 브랜치에서 들어온 변경 (Theirs)
>>>>>>> feature/other-branch
```
> **[주의] 레이블의 가변성:** 머지 전략(현재 기본값 `ort` 등) 및 호출 경로에 따라 `<<<<<<<` 뒤의 레이블은 `HEAD`가 될 수도 있고 실제 브랜치명이 될 수도 있습니다. 또한 `>>>>>>>` 뒤에는 커밋 해시가 올 수도 있습니다. 따라서 에이전트 파서는 이 레이블 텍스트를 고정값으로 단정하여 파싱하면 안 됩니다.

#### Rebase 중 충돌 (Ours / Theirs 역전 현상)
```text
<<<<<<< HEAD
// upstream 브랜치의 내용 (rebase 대상, Theirs 역할)
=======
// 내가 작성한 커밋의 변경 (Ours 역할)
>>>>>>> abc1234 (commit message here)
```
> **[매우 중요]** `rebase` 중에는 `HEAD`가 upstream(병합 대상)을 가리킵니다. 따라서 `merge`와 비교했을 때 **위아래 의미가 정확히 반대**가 됩니다. 에이전트가 코드를 올바르게 수정하려면 현재 연산이 `merge`인지 `rebase`인지 반드시 컨텍스트로 전달받아야 합니다.

#### diff3 / zdiff3 스타일 (공통 조상 표시)
`git config merge.conflictstyle diff3` 설정 시 활성화됩니다 (기본값 아님).
```text
<<<<<<< HEAD
// 내 변경 (Ours)
||||||| merged common ancestors
// 공통 조상 (Base - 분기 전 원본)
=======
// 상대방 변경 (Theirs)
>>>>>>> feature/other-branch
```
* Git 2.35+에서는 `zdiff3`을 사용하여 공통 엣지 라인을 제거한 간결한 버전을 사용할 수도 있습니다.
* 에이전트가 충돌을 논리적으로 해결하려면 Base 코드가 필요하므로, 에이전트 환경에서는 `diff3` 이상의 스타일을 강제하거나 별도로 `git merge-base`를 조회하는 로직이 필요합니다.

---

### 2.2. Perforce (Helix Core) 마커

Perforce는 Git과 완전히 다른 **4글자** 마커(`>>>>`, `====`, `<<<<`)를 사용합니다. 중요한 점은 충돌 발생 즉시 파일에 마커가 생기지 않으며, `p4 resolve` 명령어의 플래그에 따라 동작이 달라진다는 점입니다.

#### `-am` vs `-af` 플래그의 동작 차이
* `p4 resolve -am` (Accept Merge): 안전한 병합만 시도합니다. 충돌이 감지되면 **파일을 건드리지 않고(untouched) unresolved 상태로 남겨둡니다.** (마커 없음)
* `p4 resolve -af` (Accept Force): 충돌 여부와 상관없이 **강제로 병합 결과를 워크스페이스 파일에 덮어쓰며, 이때 충돌 마커가 삽입됩니다.** 에이전트가 파일을 읽고 수정하려면 반드시 이 옵션을 거쳐야 합니다.

#### 마커 구조 (p4 resolve -af 실행 후)
```text
>>>> ORIGINAL SmsPermissions.java#12
// 공통 조상 버전 내용 (Base)
==== THEIRS SmsPermissions.java#13  (또는 ==== THEIR VERSION)
// depot에 이미 submit된 상대방 변경
==== YOURS SmsPermissions.java      (또는 ==== YOUR VERSION)
// 내 워크스페이스에 있던 변경 (Ours)
<<<<
```

**특징:**
* `ORIGINAL` 섹션이 **항상 포함**되어 있어 에이전트가 공통 조상(Base)을 별도 설정 없이 바로 파악할 수 있습니다.
* 식별자로 브랜치명이나 커밋 해시 대신 **파일 리비전 번호**(`#n`)를 사용합니다.
* Perforce 버전 및 환경에 따라 `THEIRS`가 `THEIR VERSION`으로 표기될 수 있으므로, 에이전트 파서의 정규식(Regex)은 이를 모두 허용하도록 관대하게 설계되어야 합니다.

---

## 3. 실제 예시: 같은 충돌을 Git/P4로 표현

### Git 충돌 파일 (diff3 스타일 적용 시)
```java
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
<<<<<<< HEAD
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits
||||||| merged common ancestors
    private static final String HASH_TYPE = "SHA-1";
    private static final int NUM_HASHED_BYTES = 8;
=======
    // Upgraded from SHA-1: stronger collision resistance
    private static final String HASH_TYPE = "SHA-512";
    private static final int NUM_HASHED_BYTES = 16;
>>>>>>> fix/token-hash-strength
```

### Perforce 충돌 파일 (p4 resolve -af 후)
```java
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
>>>> ORIGINAL PackageBasedTokenUtil.java#31
    private static final String HASH_TYPE = "SHA-1";
    private static final int NUM_HASHED_BYTES = 8;
==== THEIRS PackageBasedTokenUtil.java#32
    // Upgraded from SHA-1: stronger collision resistance
    private static final String HASH_TYPE = "SHA-512";
    private static final int NUM_HASHED_BYTES = 16;
==== YOURS PackageBasedTokenUtil.java
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits
<<<<
```

---

## 4. 커밋/Changelist 메시지 비교

### Git — Merge Commit
히스토리에 부모가 2개인 병합 전용 커밋이 남습니다.
```text
Merge branch 'fix/token-hash-strength' into feature/token-cache

# Conflicts:
#       src/java/com/android/internal/telephony/PackageBasedTokenUtil.java
```

### Perforce — Changelist (Submit 전 편집)
별도의 Merge Commit 객체가 없으며, 선형 히스토리를 유지합니다. 내가 작성 중인 Changelist의 Description 안에 해결 내용을 기술합니다.
```text
Change:      89432
Client:      dev-build-client-1
User:        jisoo.kim
Status:      pending
Description:
    feature/token-cache: cache generateToken() results in-process

    [RESOLVED against #32] Kept SHA-512/16B (theirs), kept cache logic (ours).

Files:
    //depot/telephony/src/java/com/android/internal/telephony/PackageBasedTokenUtil.java#edit
```

---

## 5. 🤖 AI 에이전트 파서 설계를 위한 통합 추상화 모델 (Abstract Model)

Git과 Perforce는 마커의 형태, Base 코드의 존재 유무, Ours/Theirs의 기준(특히 Git Rebase)이 서로 다릅니다. 에이전트가 충돌 텍스트만 보고 상황을 유추하게 하면 치명적인 오류가 발생할 수 있습니다. 

이를 방지하기 위해 에이전트에게 파일 내용과 함께 아래와 같은 **작업 컨텍스트 메타데이터(JSON 스키마)**를 분리하여 주입하는 구조를 권장합니다.

**에이전트 주입용 Context JSON Schema 제안:**
```json
{
  "vcs": "git" | "perforce",
  "operation": "merge" | "rebase" | "cherry-pick" | "submit-resolve",
  "conflict_style": "git-merge" | "git-diff3" | "git-zdiff3" | "p4-merge",
  "base_present_in_file": true | false,
  "labels": {
    "ours_label": "HEAD" | "YOURS" | "YOUR VERSION" | "commit-sha",
    "theirs_label": "branch-name" | "THEIRS" | "THEIR VERSION",
    "base_label": "merged common ancestors" | "ORIGINAL"
  },
  "identifier_kind": "branch" | "commit-sha" | "file-revision"
}
```

이러한 메타데이터 기반 접근 방식을 도입하면, 에이전트 내부 로직에서 `if (operation === 'rebase') { invertOursTheirs(); }`와 같이 예외 상황을 안전하게 라우팅하고 하나의 통합된 추상 모델로 두 VCS의 충돌을 모두 해결할 수 있습니다.
