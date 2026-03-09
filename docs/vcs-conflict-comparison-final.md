# Git vs Perforce: Conflict 파일 구조 비교

이 문서는 **Git**과 **Perforce(Helix Core)** 에서 텍스트 충돌이 실제 파일에 어떻게 표현되는지, 그리고 이를 **에이전트가 감지·해석·수정**하려면 어떤 차이를 고려해야 하는지를 정리한 문서다.

핵심 목적은 다음 두 가지다.

1. Git과 Perforce에서 충돌이 난 파일이 **실제로 어떤 형태**로 워크스페이스에 나타나는지 비교한다.
2. 이 파일을 에이전트에 전달했을 때 에이전트가 **충돌을 정확히 감지하고 적절히 수정**할 수 있도록, 파싱 규칙과 필요한 추가 컨텍스트를 정리한다.

---

## 1. 충돌이 발생하는 시점

| 항목 | Git | Perforce |
|------|-----|----------|
| **충돌이 드러나는 시점** | `git merge`, `git rebase`, `git cherry-pick` 등 적용 시점 | 보통 `p4 submit` 또는 `p4 resolve`가 필요한 통합 상태에서 드러남 |
| **충돌 상태가 반영되는 위치** | 작업 트리 파일과 인덱스에 충돌 상태가 남음 | 워크스페이스 파일에 resolve 결과가 반영되며, 자동/수동 resolve 방식에 따라 마커가 남을 수 있음 |
| **충돌 감지 방식** | 병합/재적용 도중 명령이 멈추고 충돌 파일이 표시됨 | submit 또는 resolve 시 unresolved 상태가 감지됨 |
| **충돌 마커 생성 방식** | 충돌이 있는 파일에 Git 형식 마커가 기록됨 | `p4 resolve -af` 같은 강제 merge 경로에서는 충돌 마커가 파일에 남을 수 있음 |
| **해결 완료 후 다음 단계** | 파일 수정 → `git add` → `git merge --continue` / `git rebase --continue` / `git cherry-pick --continue` | 파일 수정 → resolve 완료 처리(예: `ae`) → `p4 submit` |
| **히스토리 단위** | commit (SHA) | changelist / file revision |

### 해설

Git은 충돌을 **현재 작업 중인 연산의 일부 상태**로 다룬다. 예를 들어 `git merge`는 충돌이 발생하면 자동 병합을 중단하고, 충돌 마커가 포함된 파일을 작업 트리에 남긴다. 이후 사용자는 파일을 수정하고 `git add` 한 뒤 연산을 계속한다. Git 공식 문서는 충돌이 작업 트리에 표시되며, 충돌 해결 후 `git add`와 `git merge --continue`로 이어간다고 설명한다. [Git merge documentation](https://git-scm.com/docs/git-merge)

Perforce는 충돌을 **resolve가 필요한 통합 상태**로 다룬다. `p4 submit`은 unresolved 상태의 파일이 있으면 제출을 완료하지 못하며, `p4 resolve`를 통해 병합 결과를 확정해야 한다. `p4 submit` 문서는 changelist가 `pending` 또는 `submitted` 상태를 가지며, submit 전에 resolve가 완료되어야 함을 전제로 설명한다. [Perforce p4 submit documentation](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_submit.html)

---

## 2. 충돌 마커 비교

### 2.1 Git

Git의 텍스트 충돌 마커는 기본적으로 다음 구분자를 사용한다.

```text
<<<<<<<
=======
>>>>>>>
```

Git 공식 문서의 conflict presentation 예시는 다음과 같은 구조를 보여준다.

```text
<<<<<<< yours:sample.txt
Conflict resolution is hard;
let's go shopping.
=======
Git makes conflict resolution easy.
>>>>>>> theirs:sample.txt
```

출처: [Git merge documentation](https://git-scm.com/docs/git-merge)

#### 중요한 점: 레이블은 고정 규칙으로 단정하면 안 된다

`<<<<<<<` 와 `>>>>>>>` 뒤에 붙는 레이블은 늘 동일한 형식이 아니다. 공식 문서는 `yours:sample.txt`, `theirs:sample.txt` 같은 예시를 보여주지만, 실제 CLI 사용에서는 `HEAD`, 브랜치명, 파일명, 커밋 식별자 등이 나타날 수 있다.

따라서 에이전트는 다음과 같이 처리해야 한다.

- `<<<<<<<` / `=======` / `>>>>>>>` **구분자 자체**로 Git 충돌을 감지한다.
- 뒤에 붙는 문자열은 **보조 라벨**로만 취급한다.
- `HEAD`인지 실제 브랜치명인지에 의존해서 파싱하지 않는다.

#### 기본 스타일: `merge`

기본 conflict style은 `merge`이며, 공통 조상(base)은 파일에 포함되지 않는다.

```text
<<<<<<< HEAD
내 쪽(현재 연산 기준 one side)
=======
상대 쪽(other side)
>>>>>>> feature/other-branch
```

다만 여기서 “내 쪽”과 “상대 쪽”의 의미는 **현재 수행 중인 Git 연산 종류**에 따라 달라질 수 있다. 이 점은 아래 rebase 항목에서 중요하다.

#### `diff3` 스타일

`merge.conflictStyle=diff3` 를 설정하면 공통 조상이 파일에 포함된다.

```text
<<<<<<< ours
ours 내용
||||||| base
공통 조상 내용
=======
theirs 내용
>>>>>>> theirs
```

Git 공식 문서는 `merge.conflictStyle` 에 대해 `merge`, `diff3`, `zdiff3` 스타일을 설명하며, `zdiff3`는 `diff3`와 유사하지만 충돌 구간의 앞뒤에서 양쪽에 공통인 라인을 줄여 더 간결하게 보여준다고 설명한다. [Git config documentation](https://git-scm.com/docs/git-config)

#### `zdiff3` 스타일

`merge.conflictStyle=zdiff3` 는 base를 보여주되, conflict region 경계를 더 압축해서 보여주는 형태다.

실제 파싱 관점에서는 다음처럼 취급하면 된다.

- `<<<<<<<` / `|||||||` / `=======` / `>>>>>>>` 구조를 가진다.
- base 섹션이 존재한다.
- conflict region의 바깥쪽 공통 라인이 일부 제외될 수 있다.

즉, 에이전트는 `diff3` 와 `zdiff3` 를 **같은 계열**로 보되, “base 포함 여부”를 중심으로 처리하는 것이 안전하다.

#### `git merge` 에서의 의미

일반적인 2-head merge에서 Git의 기본 전략은 현재 `ort` 이다. Git 문서는 과거 `recursive`가 Git v2.33.0까지 기본이었고, 현재는 `ort`가 기본이며 `recursive`는 현재 `ort`의 동의어라고 설명한다. [Git merge documentation](https://git-scm.com/docs/git-merge)

하지만 이 사실이 곧바로 **충돌 마커 라벨 형식이 버전별로 일정하게 바뀐다**는 뜻은 아니다. 따라서 문서나 에이전트 로직에서 다음과 같은 가정은 피해야 한다.

- “Git 2.33 이상이면 항상 브랜치명이 나온다”
- “Git 2.33 미만이면 항상 `HEAD`가 나온다”

안전한 규칙은 다음이다.

- `<<<<<<<` 블록 = 현재 연산의 한쪽
- `>>>>>>>` 블록 = 반대쪽
- 정확한 semantic label은 **추가 컨텍스트**로 보강한다

#### `git rebase` / `git cherry-pick` 에서의 의미

Git rebase에서는 merge와 달리 **ours / theirs의 의미가 뒤집혀 보일 수 있다**. Git 공식 문서는 rebase merge가 working branch의 각 commit을 upstream 위에 재적용하기 때문에, 충돌 시 `ours`는 “지금까지 재구성된 쪽(즉 upstream 쪽)”이고 `theirs`는 “재적용 중인 원래 작업 브랜치 쪽”이라고 설명한다. 즉, **sides are swapped** 라고 명시한다. [Git rebase documentation](https://git-scm.com/docs/git-rebase)

예시:

```text
<<<<<<< HEAD
upstream 쪽 내용
=======
지금 replay 중인 내 커밋의 내용
>>>>>>> abc1234 (commit message)
```

따라서 에이전트가 Git 충돌을 해석할 때는 최소한 다음이 필요하다.

- 현재 연산이 `merge` 인지 `rebase` 인지
- 필요하면 `cherry-pick` 인지
- base를 파일에서 바로 얻을 수 있는지 (`diff3`/`zdiff3`) 또는 별도 조회가 필요한지

#### Git에서 base가 필요한 경우

기본 `merge` style에서는 공통 조상이 파일 안에 없으므로, 더 정확한 자동 해결을 하려면 별도로 base를 구해야 한다. Git은 이를 위해 `git merge-base` 를 제공한다. [Git merge-base documentation](https://git-scm.com/docs/git-merge-base)

즉, Git 에이전트 설계에서는 다음 둘 중 하나가 필요하다.

1. `merge.conflictStyle=diff3` 또는 `zdiff3` 를 활성화해서 base를 파일에 포함시키기
2. base commit을 별도로 조회해서 에이전트 입력에 넣기

---

### 2.2 Perforce

Perforce의 텍스트 충돌 마커는 Git과 형식이 다르다. 공식 문서는 conflicting chunks가 있는 경우 merge file 안에 **yours / theirs / base** 텍스트를 포함하고, 다음과 같은 파일 마커로 구분한다고 설명한다.

```text
>>>> ORIGINAL VERSION file#n
<text>
==== THEIR VERSION file#m
<text>
==== YOUR VERSION file
<text>
<<<<
```

출처: [Perforce p4 resolve documentation](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_resolve.html)

이를 실제 프로젝트 파일 스타일로 단순화하면 보통 다음처럼 보인다.

```text
>>>> ORIGINAL SmsPermissions.java#12
공통 조상 버전 내용
==== THEIRS SmsPermissions.java#13
depot에 있는 상대 변경
==== YOURS SmsPermissions.java
내 워크스페이스 변경
<<<<
```

#### Perforce 마커의 특징

- 여는 마커는 `>>>>` 이고 닫는 마커는 `<<<<` 이다.
- 중간 구분자는 `====` 이다.
- `ORIGINAL` 섹션이 기본 포함된다.
- `THEIRS` 와 `ORIGINAL` 에는 파일 revision 번호가 붙을 수 있다.
- `YOURS` 는 아직 submit되지 않은 워크스페이스 파일이므로 revision 번호가 없을 수 있다.
- Git처럼 branch name이나 commit SHA를 중심으로 식별하지 않고, **file revision** 중심으로 식별한다.

#### `p4 resolve -am` 과 `p4 resolve -af`

자동 resolve 옵션 차이는 에이전트 설계에서 매우 중요하다.

Perforce 공식 문서는 다음과 같이 설명한다.

- `p4 resolve -am`: merge를 수행하되 **conflict가 있으면 파일을 건드리지 않고 unresolved 상태로 둔다**
- `p4 resolve -af`: conflict가 있어도 merge 결과를 워크스페이스 파일에 기록하며, **conflict markers가 파일에 남을 수 있다**

즉, 에이전트가 실제 충돌 마커 파일을 받는 전형적인 경로는 `-af` 이다. [Perforce p4 resolve documentation](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_resolve.html)

정리하면 다음과 같다.

| 옵션 | 동작 |
|------|------|
| `p4 resolve -am` | merge를 시도하되 충돌이 있으면 파일은 untouched 상태로 남기고 unresolved 유지 |
| `p4 resolve -af` | conflict가 있어도 merge 결과를 파일에 써 넣고, 필요하면 충돌 마커를 남김 |

---

## 3. 실제 예시: 같은 충돌을 Git과 Perforce로 표현

아래는 동일한 성격의 충돌을 두 VCS가 어떻게 파일에 표현하는지 보여주는 비교 예시다.

### Git 충돌 파일 예시

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

### Perforce 충돌 파일 예시 (`p4 resolve -af` 이후)

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

### 차이점 요약

| 항목 | Git | Perforce |
|------|-----|----------|
| 마커 길이 | 7글자 중심 (`<<<<<<<`, `=======`, `>>>>>>>`) | 4글자 중심 (`>>>>`, `====`, `<<<<`) |
| 기본 base 포함 여부 | 기본 `merge` style에서는 없음 | 기본적으로 `ORIGINAL` 섹션 포함 가능 |
| 식별자 성격 | 브랜치명, `HEAD`, 파일명, commit SHA 등 다양 | file revision 중심 (`#31`, `#32`) |
| section 의미 | 연산 맥락에 따라 ours/theirs 해석 필요 | `ORIGINAL` / `THEIRS` / `YOURS` 가 더 직접적으로 드러남 |
| 연산 종류 의존성 | `merge` / `rebase` / `cherry-pick` 구분 중요 | resolve 시점 의미가 상대적으로 직접적 |

---

## 4. 커밋 메시지 / Changelist description 비교

### 4.1 Git

Git merge는 자동 merge commit 메시지를 만들 수 있고, 충돌이 해결되면 사용자가 이를 수정하거나 그대로 사용할 수 있다.

예시:

```text
Merge branch 'fix/token-hash-strength' into feature/token-cache

# Conflicts:
#       src/java/com/android/internal/telephony/PackageBasedTokenUtil.java
```

또는 수동으로 더 명확하게 작성할 수 있다.

```text
merge fix/token-hash-strength: resolve hash algorithm conflict

Kept SHA-512/16B from fix branch for stronger token security.
Kept token cache logic from feature branch for performance.
Resolved conflict in PackageBasedTokenUtil.java.
```

에이전트 관점에서 중요한 점은 다음이다.

- Git은 merge commit 자체가 **분기 이력**을 드러낸다.
- 충돌 의도 파악에 branch name, commit message, PR 본문이 유용하다.
- 파일만 보면 “왜 이 선택을 해야 하는지”가 부족할 수 있다.

### 4.2 Perforce

Perforce는 별도의 merge commit 개념 대신 changelist description에 의도를 남긴다. `p4 submit` 문서는 submit form에 `Change`, `Client`, `User`, `Status`, `Description`, `Files` 필드가 있음을 설명한다. [Perforce p4 submit documentation](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_submit.html)

예시:

```text
Change: 89432
Client: dev-build-client-1
User: jisoo.kim
Status: pending
Description:
        feature/token-cache: cache generateToken() results in-process

        Keep stronger SHA-512/16B token parameters from depot revision.
        Keep token cache logic from workspace change.
        Resolved conflict in PackageBasedTokenUtil.java.
Files:
        //depot/telephony/src/java/com/android/internal/telephony/PackageBasedTokenUtil.java#edit
```

에이전트 관점에서 중요한 점은 다음이다.

- Perforce는 충돌 해결 의도를 **description** 에 명시하는 것이 자연스럽다.
- 식별자는 commit SHA보다 **file revision / changelist** 중심이다.
- 선형 submit 흐름이라도, 파일 내부 충돌은 base/theirs/yours 삼자 정보가 더 직접적으로 제공될 수 있다.

---

## 5. 전체 워크플로 비교

### 5.1 Git

```bash
git checkout feature/token-cache
git merge fix/token-hash-strength
# 충돌 발생 시 파일에 Git conflict markers 기록

vim PackageBasedTokenUtil.java
# 수동 해결

git add PackageBasedTokenUtil.java
git merge --continue
```

rebase라면 마지막 단계는 보통 다음과 같다.

```bash
git rebase --continue
```

### 5.2 Perforce

```bash
p4 sync
# depot 최신 반영

p4 submit
# unresolved 상태가 있으면 submit 불가

p4 resolve -am
# 자동 merge 시도, conflict가 있으면 해당 파일은 unresolved 상태로 남음

p4 resolve -af
# conflict가 있어도 workspace 파일에 merge 결과를 기록, 필요하면 마커 포함

vim PackageBasedTokenUtil.java
# 수동 해결

p4 resolve
# 대화형 프롬프트에서 ae (accept edited) 등으로 해결 결과 확정

p4 submit
```

Perforce 가이드 문서는 충돌이 있는 merge file은 사용자가 편집해서 차이 마커를 제거한 뒤, 편집된 결과를 accept 하는 흐름을 설명한다. [Perforce conflict editing guide](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_resolve.html)

---

## 6. 에이전트 관점에서의 함의

충돌 해결 에이전트는 Git과 Perforce를 **같은 텍스트 diff 문제**로만 보면 안 된다. 입력 구조가 다르기 때문이다.

### 6.1 에이전트가 받아야 하는 최소 컨텍스트

| 항목 | Git | Perforce |
|------|-----|----------|
| VCS 종류 | 필수 | 필수 |
| 현재 연산 종류 | `merge` / `rebase` / `cherry-pick` 구분 필요 | `resolve` / `submit` 맥락 필요 |
| base 포함 여부 | `merge` style이면 보통 없음 | 대개 `ORIGINAL` 로 포함 가능 |
| 충돌 식별자 종류 | branch / HEAD / SHA / file label | file revision (`#n`) |
| 추가 의도 정보 | commit message, PR 설명, merge target | changelist description, `p4 describe` |

### 6.2 Git용 파싱 규칙

에이전트는 다음 순서로 Git 충돌을 처리하는 것이 안전하다.

1. `<<<<<<<`, `=======`, `>>>>>>>` 토큰으로 conflict block을 감지한다.
2. `|||||||` 가 있으면 base 포함 conflict로 분류한다.
3. 현재 연산이 `merge` 인지 `rebase` 인지 확인한다.
4. 라벨 문자열(`HEAD`, 브랜치명, SHA)은 참고 정보로만 사용한다.
5. base가 없으면 가능하면 `git merge-base` 결과를 추가 입력으로 받는다.

### 6.3 Perforce용 파싱 규칙

에이전트는 다음 순서로 Perforce 충돌을 처리하는 것이 안전하다.

1. `>>>>`, `====`, `<<<<` 패턴을 감지한다.
2. 섹션 헤더의 `ORIGINAL`, `THEIRS`, `YOURS` 를 파싱한다.
3. `#n` 형태의 revision 번호를 추출해 컨텍스트 메타데이터로 보관한다.
4. `ORIGINAL` 을 base, `THEIRS` 를 depot 쪽, `YOURS` 를 workspace 쪽으로 매핑한다.
5. 최종 출력에서는 충돌 마커를 제거한 깨끗한 파일과 해결 이유를 함께 남긴다.

### 6.4 권장 입력 스키마

에이전트에 아래와 같은 공통 스키마를 주면 Git/Perforce를 함께 다루기 쉬워진다.

```json
{
  "vcs": "git | perforce",
  "operation": "merge | rebase | cherry-pick | submit-resolve",
  "conflict_style": "git-merge | git-diff3 | git-zdiff3 | p4-merge",
  "base_present_in_file": true,
  "ours_label": "HEAD",
  "theirs_label": "feature/token-cache",
  "base_label": "PackageBasedTokenUtil.java#31",
  "identifier_kind": "branch | commit-sha | file-revision",
  "file_path": "src/.../PackageBasedTokenUtil.java",
  "conflict_text": "...raw conflict text...",
  "intent_context": "commit message / PR body / changelist description / p4 describe summary"
}
```

### 6.5 실무적으로 가장 중요한 차이

가장 중요한 차이는 다음 한 줄로 요약할 수 있다.

- **Git은 base가 기본적으로 파일에 없고, 현재 연산 종류에 따라 의미 해석이 달라진다.**
- **Perforce는 base/theirs/yours가 파일에 더 직접적으로 드러나는 대신, file revision 중심으로 읽어야 한다.**

즉, 에이전트 자동 해결 정확도를 높이려면:

- Git에서는 `diff3`/`zdiff3` 또는 별도 `merge-base` 제공이 매우 유리하다.
- Perforce에서는 `ORIGINAL`/`THEIRS`/`YOURS` 섹션을 보존해서 전달하는 것이 중요하다.
- Git rebase는 merge와 동일 로직으로 해석하면 오류가 날 수 있다.

---

## 7. 이 레포에서 충돌 재현 데이터를 만들 때의 권장 방식

이 레포에서 Git과 Perforce 충돌을 에이전트 학습/테스트 입력으로 만들려면 다음 방식이 좋다.

### Git 샘플 생성

- 기본 샘플: `merge.conflictStyle=merge`
- 고급 샘플: `merge.conflictStyle=diff3`
- 추가 샘플: `merge.conflictStyle=zdiff3`
- 연산 맥락은 반드시 함께 저장:
  - `merge`
  - `rebase`
  - `cherry-pick`

### Perforce 샘플 생성

- `p4 resolve -af` 결과 파일을 저장해 실제 marker 포함 샘플 확보
- 가능하면 같은 충돌에 대해 다음 컨텍스트도 함께 저장
  - depot file path
  - `ORIGINAL` / `THEIRS` revision
  - changelist description
  - `p4 describe` 요약

### 함께 저장하면 좋은 보조 파일

- `conflict-context.json`
- `resolution-rationale.md`
- `expected-resolved-file.java`

특히 Git과 Perforce를 같은 추상 스키마에 올려놓으려면, raw conflict file 외에 다음 필드를 별도 메타데이터로 두는 것이 좋다.

```json
{
  "vcs": "git",
  "operation": "rebase",
  "base_present_in_file": false,
  "semantic_ours": "upstream-so-far",
  "semantic_theirs": "replayed-commit"
}
```

또는:

```json
{
  "vcs": "perforce",
  "operation": "submit-resolve",
  "base_present_in_file": true,
  "semantic_base": "ORIGINAL",
  "semantic_theirs": "depot",
  "semantic_yours": "workspace"
}
```

---

## 8. 참고 링크

### Git 공식 문서

- [git merge](https://git-scm.com/docs/git-merge)
- [git config (`merge.conflictStyle`)](https://git-scm.com/docs/git-config)
- [git rebase](https://git-scm.com/docs/git-rebase)
- [git merge-base](https://git-scm.com/docs/git-merge-base)

### Perforce 공식 문서

- [p4 resolve](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_resolve.html)
- [p4 submit](https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_submit.html)

### 원본 문서

- [기존 vcs-conflict-comparison.md](https://raw.githubusercontent.com/minkyeongk/Android-telephony-opt/claude/clone-telephony-framework-KXYwL/docs/vcs-conflict-comparison.md)

