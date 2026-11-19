# 5단계: 채점 샌드박스 보안

온라인 저지는 **모르는 사람이 보낸 코드를 우리 서버에서 실행하는 서비스**다.
그래서 "제출 코드는 악의적이다"를 전제로, 한 겹이 뚫려도 다음 겹이 막도록 여러 층으로 설계했다.

## 위협과 방어

| 공격 | 예시 | 막는 층 | 검증 테스트 |
|---|---|---|---|
| 정답 파일 읽기 | `ifstream("/app/.../1.out")` | 권한 분리 (nobody) + 파일 권한 600 | `test_cannot_read_testcase_answers`, `test_cheating_submission_gets_wrong_answer` |
| 컴파일 시점에 정답 끌어오기 | `#include "/app/.../1.out"` | **컴파일러도** nobody로 실행 | `test_cannot_include_answers_at_compile_time` |
| 서버 설정 훔치기 | `os.environ["REDIS_URL"]` | 환경 변수 화이트리스트 | `test_environment_is_clean` |
| 열린 연결 가로채기 | 상속된 Redis 소켓 fd 사용 | fd 전부 닫기 (`close_range`) | `test_inherited_file_descriptors_are_closed` |
| 외부 통신 (유출, 공격 발판) | `socket.create_connection(...)` | seccomp: `socket` 차단 | `test_python_cannot_open_sockets`, `test_cpp_cannot_open_sockets` |
| 포크 폭탄 | `while (1) fork();` | seccomp: 프로세스 생성 시 즉시 종료 | `test_fork_bomb_is_killed_immediately` |
| 셸 명령 실행 | `system("cat /etc/passwd")`, `subprocess` | seccomp: 프로세스 생성 차단 | `test_shell_commands_are_blocked`, `test_python_subprocess_is_blocked` |
| 워커 죽이기 | `os.kill(os.getppid(), 9)` | seccomp: 시그널은 자기 자신에게만 + uid 분리 | `test_cannot_signal_other_processes` |
| 다른 프로세스 조작 | `ptrace` | seccomp | `test_ptrace_is_blocked` |
| 테스트케이스 변조 | 정답 파일 덮어쓰기 | 권한 분리 | `test_cannot_write_outside_workspace` |
| 디스크 채우기 | 무한 출력 | `RLIMIT_FSIZE` 64MB + tmpfs 512MB | `test_output_flood_is_stopped` |
| 정리 방해 | 하위 폴더 만들고 권한 잠그기 | nobody 권한으로 권한 복구 후 삭제 | `test_workspace_is_removed_even_if_submission_creates_directories` |
| 권한 상승 | setuid 바이너리, `setuid(0)` | `no_new_privs` + seccomp + 컨테이너 capability 최소화 | (구조로 차단) |

정상 코드가 깨지지 않는지도 함께 확인한다: 깊은 재귀(`test_deep_recursion_is_allowed`), Java 스레드(`test_java_threads_still_work`), `assert` 실패 시 RE 판정(`test_abort_is_reported_as_runtime_error`).

## 방어 층

```
┌ Docker 컨테이너 ──────────────────────────────────────────────────────────┐
│  cap_drop ALL (+SETUID, SETGID, KILL) · no-new-privileges · read_only      │
│  tmpfs /tmp 512MB · pids_limit 256 · mem 2GB · cpus 2                       │
│                                                                              │
│  judge-worker (root, capability 3개)                                         │
│    └ runner ──fork──▶ 자식                                                    │
│                       1. 입력·출력 파일을 root로 연다 (정답 디렉터리는 600)   │
│                       2. fd 3번 이후 전부 닫기                                │
│                       3. rlimit: CPU, 주소 공간, 스택, 파일 크기, 스레드, fd │
│                       4. setgroups → setresgid → setresuid (nobody)          │
│                       5. PR_SET_NO_NEW_PRIVS                                 │
│                       6. seccomp BPF 필터                                     │
│                       7. execve(제출 코드, 깨끗한 환경 변수)                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

**순서가 중요하다.**
- 입력 파일은 권한을 내려놓기 **전에** 열어야 nobody가 정답 디렉터리에 접근하지 않고도 입력을 받는다.
- rlimit은 권한을 내려놓기 **전에** 걸어야 제출 코드가 hard limit을 올릴 수 없다.
- seccomp는 **마지막에** 건다. 먼저 걸면 바로 다음의 `setuid` 호출 자체가 막힌다.

## 설계 결정

### seccomp: 허용 목록이 아니라 차단 목록을 쓴 이유

허용 목록(필요한 시스템 콜만 허용)이 이론적으로 더 안전하다. 하지만 JVM과 Python은 버전, glibc, CPU에 따라 쓰는 시스템 콜이 달라서 허용 목록은 쉽게 깨진다.
대신 **위험한 기능 단위로 차단**하고(네트워크, 프로세스 생성, 디버깅, 권한 변경, 커널 조작), 파일 접근은 uid 분리로, 자원은 rlimit과 컨테이너로 막는 **다층 구조**를 택했다.

### seccomp 세부 구현

- **아키텍처 검사:** `seccomp_data.arch`가 다르면 즉시 종료. x86_64에서는 x32 ABI(시스템 콜 번호에 `0x40000000` 비트)로 필터를 우회하는 것도 막는다.
- **스레드는 허용, 프로세스는 차단:** `clone`의 첫 인자에 `CLONE_THREAD`가 있을 때만 허용한다. JVM은 스레드가 필요하고, 포크 폭탄은 막아야 하기 때문이다.
- **clone3 → ENOSYS:** `clone3`는 인자가 구조체 포인터라 BPF로 검사할 수 없다. `ENOSYS`를 돌려주면 glibc가 검사 가능한 `clone`으로 대체한다.
- **시그널은 자기 자신에게만:** 필터를 설치하는 시점의 pid를 BPF에 박아 넣어 `kill`, `tgkill`의 대상이 자기 자신일 때만 허용한다. `abort()`와 `assert` 실패는 정상적으로 RE가 된다.
- **응답 방식 구분:** 소켓은 `EACCES`를 돌려줘 프로그램이 정상적으로 실패하게 하고, 프로세스 생성이나 `ptrace`처럼 의도가 분명한 공격은 `SIGSYS`로 즉시 종료한다.

### 컴파일러도 신뢰하지 않는다

컴파일러 자체는 믿을 수 있지만, 컴파일러가 **제출 코드의 지시대로 파일을 읽는다**(`#include`). 그래서 컴파일도 `--mode compile`로 같은 샌드박스에서 nobody로 실행한다.
compile 모드는 컴파일러가 `cc1plus`, `as`, `ld`를 띄울 수 있게 프로세스 생성만 허용하고 나머지 차단은 같다.

### 워커가 컨테이너 안에서 root인 이유

일반 사용자는 다른 사용자로 바꿀 수 없다. 제출마다 nobody로 내려가려면 워커에 `CAP_SETUID`, `CAP_SETGID`가 필요하고, nobody 프로세스를 강제 종료하려면 `CAP_KILL`이 필요하다.
그래서 컨테이너 안에서는 root지만 **capability를 이 세 개만 남겼다.** 파일 권한을 무시하는 `CAP_DAC_OVERRIDE` 등은 없어서, 컨테이너의 root는 사실상 "다른 사용자로 내려갈 수만 있는 일반 계정"이다.

### 실행 실패와 제출 코드의 종료 구분

자식이 `execve` 전에 실패하면 종료 코드 127로 알 수 있지만, 제출 코드가 `exit(127)`을 하면 구분이 안 된다.
`O_CLOEXEC` 파이프를 만들어 두고 자식은 실패할 때만 errno를 쓴다. `execve`가 성공하면 파이프가 자동으로 닫히므로 부모는 "읽은 게 없음 = 실행 성공"으로 판단한다 (`posix_spawn`과 같은 기법).

## 검증 방법

1. **공격 코드 테스트 19개:** 실제 공격 코드를 샌드박스에서 실행하고 공격이 실패했는지 출력으로 확인한다.
2. **방어층을 끄고 같은 공격 실행:** 테스트가 엉뚱한 이유로 통과하는 것이 아님을 확인했다.
   - 권한 분리를 끄면 정답 파일이 실제로 읽힌다 (`STOLEN 4000000000`)
   - seccomp를 끄면 소켓이 만들어져 연결을 시도한다 (`ECONNREFUSED` 111 ↔ 켜면 `EACCES` 13)
3. **운영과 같은 권한으로 전체 테스트:** `capsh`로 capability를 SETUID, SETGID, KILL만 남기고 전체 테스트를 돌렸다.
   이 과정에서 **제출 코드가 만든 폴더를 권한이 줄어든 root가 지우지 못하는 문제**를 발견했다.
   전체 권한 root에서는 드러나지 않는 문제로, nobody 권한으로 권한을 복구한 뒤 지우도록 고쳤다.

## 남은 한계와 다음 단계

| 한계 | 영향 | 개선 방향 |
|---|---|---|
| 호스트 커널을 공유한다 | 커널 취약점이 있으면 컨테이너를 벗어날 수 있다 | gVisor(`runsc`) 런타임 또는 Firecracker 마이크로 VM |
| 네트워크 차단을 seccomp에만 의존 | 필터 실수 시 네트워크가 열린다 | 제출마다 네트워크 네임스페이스 분리 (`unshare(CLONE_NEWNET)`), 또는 워커를 Redis만 닿는 전용 네트워크에 |
| 메모리를 `ru_maxrss`로 측정 | 측정 시점과 실제 제한 사이에 여유가 있다 | 제출마다 cgroup v2 (`memory.max`, `memory.peak`) |
| `/proc`에서 다른 프로세스 목록이 보인다 | 민감 정보는 없지만 정보 노출 | PID 네임스페이스 분리 |
| Redis에 인증이 없다 | 컨테이너 내부망에서만 접근 가능 | `requirepass` + ACL |
| 워커 한 대가 한 번에 하나씩 채점 | 같은 uid의 제출이 동시에 돌지 않는 전제 | 동시 채점 시 제출마다 다른 uid 할당 |
