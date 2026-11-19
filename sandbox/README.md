# sandbox

제출 프로그램을 자식 프로세스로 실행하고 시간·메모리 제한을 걸어 결과를 JSON으로 출력합니다.

## 빌드 및 테스트

```bash
./tests/run_tests.sh
# 또는
cmake -B build && cmake --build build
```

## 사용법

```bash
build/runner --time 1000 --memory 256 --input in.txt --output out.txt -- ./solution

# JVM처럼 가상 메모리를 크게 예약하는 런타임은 주소 공간 제한을 끄고 실사용량(RSS)으로만 판정
build/runner --time 2000 --memory 256 --input in.txt --output out.txt --no-as-limit -- java -Xmx256m Main
# {"status":"OK","exit_code":0,"signal":0,"time_ms":12,"memory_kb":3456}
```

| status | 의미 |
|---|---|
| OK | 정상 종료 (정답 여부는 worker가 판단) |
| TLE | 시간 초과 (CPU 시간 초과, 또는 대기 상태로 제한의 3배 경과) |
| MLE | 메모리 초과 |
| RE | 런타임 에러 (비정상 종료 코드, 시그널) |
| SE | 채점 시스템 에러 (실행 파일 없음 등, 제출 코드가 실행되기 전 실패) |

## 동작 원리 (v0.2)

자식 프로세스에서 아래 순서로 방어를 건 뒤 `execve`한다.

1. 입력·출력 파일을 root로 연다 (정답 디렉터리는 root만 읽을 수 있음)
2. fd 3번 이후를 모두 닫는다
3. `setrlimit`: CPU 시간, 주소 공간, 스택(= 메모리 제한), 파일 크기 64MB, 스레드 수, fd 수
4. `--uid/--gid`가 있으면 `setgroups → setresgid → setresuid`로 권한을 내려놓는다
5. `PR_SET_NO_NEW_PRIVS`
6. seccomp BPF: 네트워크, 프로세스 생성(run 모드), 디버깅, 권한 변경, 커널 조작 차단
7. `--env`로 준 환경 변수만 넘겨 `execve`

부모는 `wait4()`로 CPU 시간과 최대 메모리(`ru_maxrss`)를 측정하고, CPU를 쓰지 않는 무한 대기는 wall time으로 잡는다.
`execve` 전 실패는 `O_CLOEXEC` 파이프로 알려 받아 `SE`로 판정한다 (제출 코드의 `exit(127)`과 구분).

| 모드 | 용도 | 프로세스 생성 |
|---|---|---|
| `run` | 제출 코드 실행 | 금지 (스레드만 허용) |
| `compile` | 컴파일러 실행 (`#include`로 정답을 읽는 공격 대비) | 허용 |

자세한 위협 모델과 검증 과정은 [docs/07-sandbox-security.md](../docs/07-sandbox-security.md)에 있습니다.
