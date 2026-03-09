# Git vs Perforce: Conflict 파일 구조 비교

## 1. 충돌이 발생하는 시점

| 항목 | Git | Perforce |
|------|-----|----------|
| **충돌 발생 시점** | `merge` / `rebase` 실행 시 | `submit` 시도 시 |
| **충돌 상태의 위치** | 작업 디렉토리 (unstaged) | 워크스페이스 파일 (p4 resolve 후) |
| **충돌 감지 명령어** | `git merge <branch>` | `p4 submit` → "must resolve" 오류 |
| **마커 생성 명령어** | 자동 (merge 실패 시) | `p4 resolve -af` (force merge) |
| **해결 완료 명령어** | `git add <file>` → `git merge --continue` | 대화형 프롬프트에서 `ae` 입력 → `p4 submit` |
| **히스토리 단위** | commit (SHA) | changelist (CL 번호) |

---

## 2. 충돌 마커 비교

### Git

Git 충돌 마커는 **Git 버전**과 **명령어 종류**에 따라 레이블이 달라집니다.

#### git merge (Git < 2.33, recursive 전략)

```
<<<<<<< HEAD
// 내 브랜치 (현재 체크아웃된 상태)
=======
// 상대 브랜치에서 들어온 변경
>>>>>>> feature/other-branch
```

#### git merge (Git ≥ 2.33, ort 전략 — 현재 기본값)

```
<<<<<<< main
// 내 브랜치 (현재 체크아웃된 상태)
=======
// 상대 브랜치에서 들어온 변경
>>>>>>> feature/other-branch
```

> `<<<<<<<` 뒤 레이블이 `HEAD` 대신 **실제 브랜치명**으로 표시됩니다.

#### git rebase 중 충돌

```
<<<<<<< HEAD
// upstream 브랜치의 내용 (rebase 대상)
=======
// 내가 작성한 커밋의 변경
>>>>>>> abc1234 (commit message here)
```

> rebase 중 HEAD는 **detached 상태**로 upstream을 가리킵니다.
> `>>>>>>>` 뒤에는 브랜치명이 아닌 **커밋 SHA + 메시지**가 붙습니다.
> merge와 달리 **위아래 순서가 반대**입니다 — `<<<<<<<`(HEAD) 쪽이 upstream, `>>>>>>>`쪽이 내 커밋.

#### diff3 스타일 (공통 조상 표시)

`git config merge.conflictstyle diff3` 설정 시 활성화됩니다 (기본값 아님):

```
<<<<<<< HEAD
// 내 변경
||||||| base
// 공통 조상 (분기 전 원본)
=======
// 상대방 변경
>>>>>>> feature/other-branch
```

Git 2.35+에서는 `merge.conflictstyle zdiff3`으로 공통 엣지 라인을 제거한 간결한 버전도 사용 가능합니다.

**특징:**
- `<<<<<<<` / `=======` / `>>>>>>>` 는 모두 **정확히 7글자**
- `<<<<<<<` 뒤 레이블은 Git 버전에 따라 `HEAD` 또는 실제 브랜치명
- rebase / cherry-pick에서는 `>>>>>>>` 뒤에 **commit SHA**가 붙음
- diff3 base 섹션(`|||||||`)은 기본 비활성화, 설정 필요

---

### Perforce

Perforce는 Git과 **완전히 다른 마커 형식**을 사용합니다.

`p4 resolve -af` (force merge) 실행 후 생성되는 마커:

```
>>>> ORIGINAL SmsPermissions.java#12
// 공통 조상 버전 내용
==== THEIRS SmsPermissions.java#13
// depot에 이미 submit된 상대방 변경
==== YOURS SmsPermissions.java
// 내 워크스페이스에 있던 변경 (pending changelist)
<<<<
```

실제 공식 문서 예시:

```
>>>> ORIGINAL README#26
+\ Copyright 1993, 1997 Christopher Seiwald.
==== THEIRS README#27
+\ Copyright 1993, 1997, 2004 Christopher Seiwald.
==== YOURS README
+\ Copyright 1993, 1997, 2005 Christopher Seiwald.
<<<<
```

**특징:**
- 여는 마커는 `>>>>` (4글자), 닫는 마커는 `<<<<` (4글자) — Git의 7글자와 다름
- 섹션 구분자는 `====` (4글자)
- `ORIGINAL` 섹션이 **항상 포함**되어 공통 조상을 별도 설정 없이 볼 수 있음
- 파일 **리비전 번호** (`#26`, `#27`) 표시 — CL 번호 아님
- depot 경로는 마커 안에 없음 (resolve 프롬프트 헤더에만 표시)
- `YOURS`에는 리비전 번호가 없음 (아직 submit 안 된 상태이므로)

**`-am` vs `-af` 동작 차이:**

| 옵션 | 동작 |
|------|------|
| `p4 resolve -am` | 자동 merge 시도. 충돌 발생 시 **파일을 수정하지 않고 skip** (마커 없음) |
| `p4 resolve -af` | 강제 merge. 충돌이 있어도 **마커를 파일에 삽입** |

---

## 3. 실제 예시: 같은 충돌을 Git/P4로 표현

아래는 `PackageBasedTokenUtil.java`에서 동일한 충돌이 두 VCS에서 어떻게 다르게 나타나는지 비교한 예시입니다.

### Git 충돌 파일 (Git ≥ 2.33 기준)

```java
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
<<<<<<< feature/token-cache
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits

    static final int NUM_BASE64_CHARS = 11;

    /** Cache previously computed tokens to avoid repeated PackageManager queries. */
    private static final Map<String, String> sTokenCache = new ConcurrentHashMap<>();
=======
    // Upgraded from SHA-256: stronger collision resistance for app identity tokens.
    private static final String HASH_TYPE = "SHA-512";
    private static final int NUM_HASHED_BYTES = 16; // 16 bytes = 128 bits

    static final int NUM_BASE64_CHARS = 21;
>>>>>>> fix/token-hash-strength
```

### Perforce 충돌 파일 (동일 내용, p4 resolve -af 후)

```java
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
>>>> ORIGINAL PackageBasedTokenUtil.java#31
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits

    static final int NUM_BASE64_CHARS = 11;
==== THEIRS PackageBasedTokenUtil.java#32
    // Upgraded from SHA-256: stronger collision resistance for app identity tokens.
    private static final String HASH_TYPE = "SHA-512";
    private static final int NUM_HASHED_BYTES = 16; // 16 bytes = 128 bits

    static final int NUM_BASE64_CHARS = 21;
==== YOURS PackageBasedTokenUtil.java
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits

    static final int NUM_BASE64_CHARS = 11;

    /** Cache previously computed tokens to avoid repeated PackageManager queries. */
    private static final Map<String, String> sTokenCache = new ConcurrentHashMap<>();
<<<<
```

**차이점 요약:**

| | Git | Perforce |
|--|-----|----------|
| 마커 문자 수 | 7글자 (`<<<<<<<`, `=======`, `>>>>>>>`) | 4글자 (`>>>>`, `====`, `<<<<`) |
| `<<<<<<<` 레이블 | 브랜치명 또는 `HEAD` | 없음 (`>>>> ORIGINAL 파일명#리비전`) |
| `>>>>>>>` 레이블 | 브랜치명 / commit SHA | 없음 (`<<<<`로 블록 닫기) |
| 식별자 유형 | 브랜치명 / commit SHA | 파일 리비전 번호 (`#n`) |
| 공통 조상 표시 | 기본 없음 (`diff3` 설정 필요) | **기본 포함** (`ORIGINAL` 섹션) |
| 섹션 순서 | ours → theirs | base(ORIGINAL) → theirs → ours |

---

## 4. 커밋/Changelist 메시지 비교

### Git — merge commit 메시지

```
Merge branch 'fix/token-hash-strength' into feature/token-cache

# Conflicts:
#       src/java/com/android/internal/telephony/PackageBasedTokenUtil.java
```

또는 수동으로 작성할 경우:

```
merge fix/token-hash-strength: resolve hash algorithm conflict

Kept SHA-512/16B from fix branch (security). Kept token cache from feature
branch (performance). Both changes are orthogonal after resolution.

Conflicts resolved in:
  src/java/com/android/internal/telephony/PackageBasedTokenUtil.java
```

**특징:**
- "Merge branch X into Y" 형태가 기본
- Conflicts 목록은 자동으로 주석 처리됨
- 히스토리에 merge commit이 남음 (부모가 2개)

---

### Perforce — changelist description (submit 전 편집)

```
Change:      89432
Client:      dev-build-client-1
User:        jisoo.kim
Status:      pending
Description:
    feature/token-cache: cache generateToken() results in-process

    PackageManager.getApplicationInfo()는 SMS 토큰 조회 시마다 호출되어
    패키지가 많은 기기에서 ~40ms 오버헤드 발생. ConcurrentHashMap으로
    결과를 캐시하고, 패키지 설치/제거 시 invalidateCache()로 무효화.

    [RESOLVED against #32] Kept SHA-512/16B (theirs), kept cache logic (ours).

Files:
    //depot/telephony/src/java/com/android/internal/telephony/PackageBasedTokenUtil.java#edit
```

**특징:**
- `Change:`, `User:`, `Status:` 등 메타데이터가 헤더에 고정 포함
- 별도의 "merge commit" 없음 — 내 CL description 안에 해결 내용을 기술
- resolve 참조 시 CL 번호가 아닌 **파일 리비전 번호** (`#32`) 사용
- `#edit`, `#add`, `#delete` 로 파일 변경 유형 명시
- 히스토리는 선형 (submit된 CL 번호 순서로 depot에 쌓임)

---

## 5. 전체 워크플로 비교

### Git

```
git checkout feature/token-cache
git merge fix/token-hash-strength
# → CONFLICT 발생, 파일에 <<<<<<< (HEAD 또는 브랜치명) / >>>>>>> fix/token-hash-strength 마커

vim PackageBasedTokenUtil.java  # 마커 수동 해결
git add PackageBasedTokenUtil.java
git merge --continue
# → merge commit 생성, 히스토리에 남음
```

### Perforce

```
# Dev B의 워크스페이스에서
p4 sync                         # depot 최신 상태 가져오기
p4 submit                       # → "CL:89432 needs resolve before submit" 오류

p4 resolve -am                  # 자동 merge 시도 — 충돌 파일은 skip됨 (마커 없음)
p4 resolve -af                  # 충돌 파일에 >>>> ORIGINAL / ==== THEIRS / ==== YOURS / <<<< 마커 삽입
vim PackageBasedTokenUtil.java  # 마커 수동 해결

# 대화형 p4 resolve 프롬프트에서 'ae' (Accept Edited) 입력
p4 resolve
# → Accept this change? [y/n/e/...] ae
p4 submit                       # → CL:89432 depot에 등록, 선형 히스토리에 추가
```

---

## 6. 에이전트 관점에서의 함의

AI 에이전트가 충돌을 자동 해결할 때 두 환경에서 받게 되는 입력이 다릅니다.

| 에이전트 입력 | Git | Perforce |
|--------------|-----|----------|
| **충돌 파일 마커** | `<<<<<<< HEAD` (또는 브랜치명) / `=======` / `>>>>>>>` | `>>>> ORIGINAL` / `==== THEIRS` / `==== YOURS` / `<<<<` |
| **공통 조상** | `git merge-base`로 별도 조회 필요 (또는 diff3 설정) | 마커 내 `ORIGINAL` 섹션에 기본 포함 |
| **변경 식별자** | 브랜치명 / commit SHA | 파일 리비전 번호 (`#n`) |
| **변경 의도 파악** | `git log <branch>` 또는 PR description | `p4 describe <CL번호>` 출력 |
| **해결 후 액션** | `git add` + `git merge --continue` | 대화형 프롬프트 `ae` + `p4 submit` |
| **컨텍스트 파일** | PR body, commit messages | `p4-conflict-context.txt` (이 레포에서 사용) |

이 레포의 conflict 브랜치들은 Perforce 스타일 마커(`ORIGINAL`/`THEIRS`/`YOURS` + 리비전 번호)와
`p4-conflict-context.txt`를 함께 제공하여 실제 P4 submit 충돌 시나리오를 재현합니다.
