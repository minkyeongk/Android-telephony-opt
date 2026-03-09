# Git vs Perforce: Conflict 파일 구조 비교

## 1. 충돌이 발생하는 시점

| 항목 | Git | Perforce |
|------|-----|----------|
| **충돌 발생 시점** | `merge` / `rebase` 실행 시 | `submit` 시도 시 |
| **충돌 상태의 위치** | 작업 디렉토리 (unstaged) | 워크스페이스 파일 (p4 resolve 후) |
| **충돌 감지 명령어** | `git merge <branch>` | `p4 submit` → "must resolve" 오류 |
| **마커 생성 명령어** | 자동 (merge 실패 시) | `p4 resolve -am` |
| **해결 완료 명령어** | `git add <file>` → `git merge --continue` | `p4 resolve -e` → `p4 submit` |
| **히스토리 단위** | commit (SHA) | changelist (CL 번호) |

---

## 2. 충돌 마커 비교

### Git

```
<<<<<<< HEAD
// 내 브랜치 (현재 체크아웃된 상태)
=======
// 상대 브랜치에서 들어온 변경
>>>>>>> feature/other-branch
```

또는 리베이스 중에는:

```
<<<<<<< refs/heads/main
// 들어오는 커밋의 변경
=======
// 내가 작성한 커밋의 변경
>>>>>>> abc1234 (commit message here)
```

**특징:**
- `<<<<<<<` 뒤에 **브랜치명** 또는 **commit SHA**가 붙음
- `HEAD`는 항상 "지금 내가 서 있는 곳"
- rebase/cherry-pick에서는 위아래 순서가 merge와 반대

---

### Perforce

`p4 resolve -am` 실행 후 생성되는 마커:

```
<<<<<<< yours
// 내 워크스페이스에 있던 변경 (아직 submit 안 한 것)
=======
// depot에 이미 submit된 상대방 변경
>>>>>>> theirs
```

depot 경로가 표시되는 경우:

```
<<<<<<< yours://depot/telephony/src/.../SmsPermissions.java
// 내 pending changelist (CL:93710) 변경
=======
// depot에 submit된 CL:93845 변경
>>>>>>> theirs://depot/telephony/src/.../SmsPermissions.java
```

3-way merge 옵션(`p4 resolve -am3`) 사용 시 base도 표시:

```
<<<<<<< yours
// 내 변경
||||||| base
// 공통 조상 (분기 전 원본)
=======
// depot의 변경
>>>>>>> theirs
```

**특징:**
- `<<<<<<<` 뒤는 항상 `yours` (내 것) 또는 `theirs` (depot)
- 브랜치 개념이 없으므로 이름 대신 방향(yours/theirs)으로 표현
- 선택적으로 depot 경로(`//depot/...`) 포함

---

## 3. 실제 예시: 같은 충돌을 Git/P4로 표현

아래는 `PackageBasedTokenUtil.java`에서 동일한 충돌이 두 VCS에서 어떻게 다르게 나타나는지 비교한 예시입니다.

### Git 충돌 파일

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

### Perforce 충돌 파일 (동일 내용, p4 resolve -am 후)

```java
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
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
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)
```

**차이점 요약:**

| | Git | Perforce |
|--|-----|----------|
| `<<<<<<<` 레이블 | `feature/token-cache` (브랜치명) | `yours:파일명 (CL:89432 ...)` |
| `>>>>>>>` 레이블 | `fix/token-hash-strength` (브랜치명) | `theirs:파일명 (CL:89501 ...)` |
| 식별자 유형 | 브랜치명 / commit SHA | CL 번호 + 방향 |
| base 표시 | 기본 없음 (`git diff3` 옵션 필요) | `-am3` 옵션으로 선택 가능 |

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

    [RESOLVED against CL:89501] Kept SHA-512/16B (theirs), kept cache logic (ours).

Files:
    //depot/telephony/src/java/com/android/internal/telephony/PackageBasedTokenUtil.java#edit
```

**특징:**
- `Change:`, `User:`, `Status:` 등 메타데이터가 헤더에 고정 포함
- 별도의 "merge commit" 없음 — 내 CL description 안에 해결 내용을 기술
- `#edit`, `#add`, `#delete` 로 파일 변경 유형 명시
- 히스토리는 선형 (submit된 CL 번호 순서로 depot에 쌓임)

---

## 5. 전체 워크플로 비교

### Git

```
git checkout feature/token-cache
git merge fix/token-hash-strength
# → CONFLICT 발생, 파일에 <<<<<<< HEAD / >>>>>>> fix/token-hash-strength 마커

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

p4 resolve -am                  # 충돌 파일에 <<<<<<< yours / >>>>>>> theirs 마커 생성
vim PackageBasedTokenUtil.java  # 마커 수동 해결
p4 resolve -e PackageBasedTokenUtil.java  # "I edited the file manually" 표시
p4 submit                       # → CL:89432 depot에 등록, 선형 히스토리에 추가
```

---

## 6. 에이전트 관점에서의 함의

AI 에이전트가 충돌을 자동 해결할 때 두 환경에서 받게 되는 입력이 다릅니다.

| 에이전트 입력 | Git | Perforce |
|--------------|-----|----------|
| **충돌 파일** | `<<<<<<< HEAD` / `>>>>>>> branch` 마커 | `<<<<<<< yours` / `>>>>>>> theirs` 마커 |
| **변경 의도 파악** | `git log <branch>` 또는 PR description | `p4 describe <CL번호>` 출력 |
| **베이스 코드** | `git merge-base` 로 공통 조상 조회 | `p4 diff2` 또는 resolve의 base 섹션 |
| **해결 후 액션** | `git add` + `git merge --continue` | `p4 resolve -e` + `p4 submit` |
| **컨텍스트 파일** | PR body, commit messages | `p4-conflict-context.txt` (이 레포에서 사용) |

이 레포의 conflict 브랜치들은 Perforce 스타일 마커(`yours`/`theirs` + CL번호)와
`p4-conflict-context.txt`를 함께 제공하여 실제 P4 submit 충돌 시나리오를 재현합니다.
