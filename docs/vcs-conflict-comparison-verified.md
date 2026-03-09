# VCS Conflict Comparison (Verified)

이 문서는 **Git**과 **Perforce (Helix Core)** 에서 충돌(conflict)이 발생했을 때
실제 파일이 어떤 형태가 되는지, 그리고 이 정보를 **에이전트가 안정적으로 파싱/수정**하려면
무엇을 알아야 하는지 검증 기반으로 정리한 최종본이다.

목표는 다음 2가지다.

1. 사람이 Git/Perforce 충돌 파일을 정확히 이해할 수 있게 한다.
2. 에이전트가 충돌을 감지하고 적절히 수정할 수 있도록 입력 규격을 명확히 정의한다.

---

## 검증 결론 요약

### 확정적으로 맞는 내용

- Git은 충돌 시 **작업 트리 파일에 conflict markers를 남기고 중단**한다.
- Git은 `merge`, `diff3`, `zdiff3` conflict style을 지원한다.
- Git rebase에서는 충돌 해석에서 **ours/theirs의 의미가 merge와 다르게 보일 수 있으므로** 연산 종류를 함께 전달해야 한다.
- Perforce는 resolve 과정에서 **base / theirs / yours**를 기준으로 병합하며,
  충돌이 남는 경우 워크스페이스 파일에 marker가 들어간 merge file이 생길 수 있다.
- Perforce의 `p4 resolve -am` 과 `p4 resolve -af` 는 의미가 다르며,
  에이전트 설계 시 반드시 구분해야 한다.

### 수정해서 써야 하는 내용

- Git conflict marker의 상단 라벨이 항상 `HEAD`인지, 항상 브랜치명인지 **버전 기준으로 단정하면 안 된다.**
  실제 라벨 텍스트는 명령 맥락, 표시 방식, 설정, Git의 출력 경로에 따라 달라질 수 있다.
- 따라서 에이전트는 `<<<<<<< HEAD` 같은 문자열을 **고정 규칙으로 해석하면 안 되고**,
  단순히 “왼쪽 쪽(현재 체크아웃 쪽/ours side label)” 정도로만 추상화해야 한다.
- Perforce `-af`는 단순한 “강제 merge” 한 줄 설명보다,
  **충돌이 있어도 merged result를 워크스페이스 파일에 기록하며 marker가 남을 수 있다**고 설명하는 편이 정확하다.

---

## 1. Git 충돌 시 파일이 어떻게 보이는가

### 1.1 기본 merge 스타일 (`merge`)

Git의 기본 conflict style은 `merge`다.
충돌이 나면 작업 트리 파일 안에 보통 다음과 비슷한 marker가 들어간다.

```text
<<<<<<< HEAD
content from current side
=======
content from incoming side
>>>>>>> feature
```

중요한 점:

- 위 예시는 **대표 형태**이지, 라벨 문자열 자체를 고정 규칙으로 보면 안 된다.
- 상단이 `HEAD`로 보일 수 있고, 다른 라벨이 붙을 수도 있다.
- 하단도 브랜치명, 커밋/설명 라벨 등으로 보일 수 있다.

즉, 에이전트는 다음만 믿어야 한다.

- `<<<<<<<` : 첫 번째 충돌 구간 시작
- `=======` : 구간 분리자
- `>>>>>>>` : 두 번째 충돌 구간 종료

라벨 문자열은 **보조 정보**로만 취급한다.

### 1.2 `diff3` 스타일

`merge.conflictStyle=diff3` 이면 공통 조상(base) 구간이 추가된다.

```text
<<<<<<< HEAD
ours
||||||| base
common ancestor
=======
theirs
>>>>>>> feature
```

이 경우 에이전트는 한 파일만 보고도 다음 3가지를 동시에 볼 수 있다.

- ours
- base
- theirs

이는 자동 해결 품질을 높이는 데 매우 유리하다.

### 1.3 `zdiff3` 스타일

`zdiff3` 역시 base 정보를 제공한다.
실무적으로는 `diff3` 계열로 취급하고,
에이전트 관점에서는 **base가 파일 안에 존재한다**는 점이 핵심이다.

### 1.4 Git에서 base가 항상 파일에 들어있는 것은 아니다

기본 `merge` 스타일에서는 **base가 파일에 없다.**
따라서 에이전트가 고품질 자동 해결을 하려면 다음 중 하나가 필요하다.

- 저장소 설정을 `diff3` 또는 `zdiff3` 로 맞춘다.
- 또는 별도로 `git merge-base` 등으로 base를 계산해 함께 전달한다.

---

## 2. Git merge 와 rebase 는 충돌 해석이 같지 않다

Git에서 가장 위험한 오해 중 하나는
**merge conflict와 rebase conflict를 같은 의미 체계로 처리하는 것**이다.

### 2.1 merge 중 충돌

일반 merge에서는 보통 현재 체크아웃한 브랜치 기준과,
합치려는 반대편 변경을 비교하는 식으로 이해하면 된다.

### 2.2 rebase 중 충돌

rebase에서는 Git이 커밋을 다시 적용하는 방식으로 동작하므로,
사용자가 직관적으로 기대하는 ours/theirs와 실제 표시 해석이 뒤집혀 보일 수 있다.

즉, 에이전트는 단순히 conflict marker만 보고 판단하면 안 되고,
반드시 아래 컨텍스트 중 하나를 추가 입력으로 받아야 한다.

- 현재 연산이 `merge` 인지
- `rebase` 인지
- `cherry-pick` 인지

이 컨텍스트 없이 “ours를 유지해라” 같은 지시는 오해될 수 있다.

---

## 3. Perforce 충돌 시 파일이 어떻게 보이는가

Perforce는 resolve 과정에서 base / theirs / yours 관점을 명시적으로 사용한다.
공식 문서 기준으로 merge file에는 다음 계열의 정보가 들어갈 수 있다.

```text
>>>> ORIGINAL VERSION
base content
==== THEIRS VERSION
incoming content
==== YOUR VERSION
your workspace content
<<<<
```

실제 표시 텍스트는 환경에 따라 다소 달라질 수 있으나,
핵심 구조는 다음과 같다.

- ORIGINAL = base
- THEIRS = depot / incoming 쪽
- YOURS = workspace / local 쪽

### 3.1 Git과의 중요한 차이

Perforce는 resolve 과정에서 **base 정보가 merge file에 기본적으로 드러나는 편**이다.
즉, Git 기본 merge 스타일보다 에이전트 입장에서 해석이 더 쉽다.

### 3.2 revision 번호 해석

Perforce 표시에서 보이는 `#n` 은 보통 **file revision** 이며,
changelist 번호와 같은 개념으로 읽으면 안 된다.

### 3.3 `YOURS` 쪽 revision 표기

문맥상 `YOURS` 는 워크스페이스의 로컬 편집본이므로,
항상 depot revision 표기가 붙는다고 가정하면 안 된다.

---

## 4. Perforce resolve 옵션은 어떻게 이해해야 하는가

이 부분은 에이전트 설계에서 특히 중요하다.

### 4.1 `p4 resolve -am`

의미:

- 자동 merge를 시도한다.
- **conflict가 없을 때만** merge result를 채택한다.
- conflict가 검출되면 파일은 unresolved 상태로 남길 수 있다.

즉, `-am` 은 “자동 병합 시도 + 충돌 있으면 건너뜀”에 가깝다.

### 4.2 `p4 resolve -af`

의미:

- 자동 merge를 강하게 진행한다.
- conflict가 있어도 **merged result를 워크스페이스 파일에 기록할 수 있다.**
- 이 경우 파일 안에 **conflict markers가 남은 상태**가 될 수 있다.

즉, `-af` 는 에이전트 입장에서
“이미 marker가 들어간 병합 결과 파일을 후처리해야 하는 상황”을 만들어낼 수 있다.

### 4.3 `-f` 를 너무 좁게 해석하지 말 것

문서화할 때 `-af = force merge` 라고만 요약하면 부족하다.
공식 문서 맥락상 `-f` 는 이미 resolve된 파일을 다시 resolve하게 하는 의미까지 포함하므로,
최종 설명은 **공식 의미를 넘어서 단순화하지 않는 편이 안전하다.**

---

## 5. 충돌 해결 완료 상태 비교

### Git

충돌 해결 후 일반적으로 다음 흐름을 따른다.

```bash
git add <file>
git merge --continue
```

또는 상황에 따라 commit으로 마무리될 수 있다.
rebase 중이면 보통 다음 형태다.

```bash
git add <file>
git rebase --continue
```

즉, Git은 **작업 트리 파일 수정 + index 반영**이 해결 완료의 핵심이다.

### Perforce

Perforce는 unresolved 파일이 남아 있으면 submit이 막힌다.
resolve를 완료한 뒤 submit해야 한다.

즉, Perforce는 **resolve 상태 관리**가 Git보다 더 명시적이다.

---

## 6. 에이전트가 충돌을 안정적으로 처리하려면 어떤 입력이 필요한가

단순히 “충돌 파일 텍스트”만 넘기면 부족하다.
최소 아래 정보를 함께 넘기는 것이 좋다.

```yaml
vcs: git | perforce
operation: merge | rebase | cherry-pick | submit-resolve
conflict_style: git-merge | git-diff3 | git-zdiff3 | p4-merge
base_present_in_file: true | false
ours_label: string | null
theirs_label: string | null
base_label: string | null
identifier_kind: branch | commit | file-revision | unknown
```

### 각 필드 설명

- `vcs`  
  Git인지 Perforce인지 구분한다.

- `operation`  
  Git에서는 특히 중요하다. merge / rebase / cherry-pick 에 따라 충돌 해석이 달라질 수 있다.

- `conflict_style`  
  marker family를 명시한다. Git 기본 merge와 diff3는 파싱 방식이 다르다.

- `base_present_in_file`  
  base가 파일 안에 이미 있는지 여부다. 자동 수정 품질에 큰 영향을 준다.

- `ours_label`, `theirs_label`, `base_label`  
  사람이 보기엔 유용하지만, 신뢰도는 낮다. 파싱 기준이 아니라 참고 정보로만 사용한다.

- `identifier_kind`  
  라벨이 브랜치명인지, 커밋 관련 식별자인지, Perforce file revision인지 구분한다.

---

## 7. 에이전트 파싱 규칙 권장안

### 7.1 Git용 규칙

에이전트는 Git 충돌 파일을 볼 때 다음을 지켜야 한다.

1. `<<<<<<<`, `=======`, `>>>>>>>` 를 구조적 marker로 파싱한다.
2. 라벨 문자열(`HEAD`, `main`, `feature`, SHA 등)은 보조 정보로만 사용한다.
3. `|||||||` 가 있으면 base가 포함된 conflict로 인식한다.
4. `operation=rebase` 이면 ours/theirs 의미를 merge와 동일하게 취급하지 않는다.

### 7.2 Perforce용 규칙

에이전트는 Perforce merge file을 볼 때 다음을 지켜야 한다.

1. `ORIGINAL`, `THEIRS`, `YOURS` 계열 구간을 우선 인식한다.
2. `ORIGINAL=base`, `THEIRS=incoming`, `YOURS=workspace` 로 추상화한다.
3. `#n` 표시는 changelist가 아니라 file revision일 수 있음을 전제로 한다.
4. marker 안의 영문 문구가 약간 달라도 구조 중심으로 파싱한다.

---

## 8. 실무 권장사항

### Git 저장소

에이전트 기반 자동 충돌 해결을 강화하려면 다음이 유리하다.

- 가능하면 `merge.conflictStyle=zdiff3` 또는 `diff3` 사용
- 충돌 처리 시 현재 연산(`merge` / `rebase` / `cherry-pick`)을 함께 기록
- base가 없는 경우 `git merge-base` 결과를 별도로 제공

### Perforce 저장소

- `p4 resolve -am` 과 `-af` 를 구분해서 운영 로그에 남길 것
- `-af` 로 생성된 marker 포함 파일은 후속 자동 처리 대상으로 명시할 것
- 에이전트 입력에 depot/local/base 의미를 명시적으로 포함할 것

---

## 9. 최종 판단

이 주제에 대해 에이전트 친화적으로 정리할 때,
가장 중요한 사실은 아래 4가지다.

1. **Git marker 라벨 문자열은 고정 규칙으로 믿으면 안 된다.**  
   구조 marker 자체를 기준으로 파싱해야 한다.

2. **Git rebase는 merge와 같은 충돌 의미 체계로 처리하면 위험하다.**  
   연산 종류를 추가 입력으로 반드시 넘겨야 한다.

3. **Perforce는 base/theirs/yours 구조가 더 직접적으로 드러난다.**  
   대신 resolve 옵션(`-am`, `-af`) 차이를 무시하면 잘못된 상태 해석이 생긴다.

4. **최고 품질의 자동 해결에는 base 정보가 중요하다.**  
   Git 기본 merge 스타일은 base가 없으므로 별도 보완이 필요하다.

---

## 참고 링크

아래 링크들은 이 문서를 검증하거나 후속 수정 시 직접 확인할 수 있는 공식 근거다.

### Git 공식 문서

- Git merge  
  https://git-scm.com/docs/git-merge

- Git config (`merge.conflictStyle`)  
  https://git-scm.com/docs/git-config

- Git rebase  
  https://git-scm.com/docs/git-rebase

- Git merge-base  
  https://git-scm.com/docs/git-merge-base

### Perforce 공식 문서

- `p4 resolve`  
  https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_resolve.html

- Editing the merge file  
  https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/P4Guide/edit-merge-file.html

- `p4 submit`  
  https://help.perforce.com/helix-core/server-apps/cmdref/current/Content/CmdRef/p4_submit.html

---

## 에이전트용 한 줄 규칙

> Git은 **marker 구조 중심**, Perforce는 **base/theirs/yours 중심**으로 파싱하고,
> Git에서는 반드시 **operation 종류(merge/rebase/cherry-pick)** 를 함께 전달하라.
