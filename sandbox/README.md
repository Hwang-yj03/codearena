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
| SE | 채점 시스템 에러 |

## 동작 원리 (v0.1)

- `fork()` 후 자식에서 `setrlimit`으로 CPU 시간, 주소 공간, 출력 파일 크기를 제한한 뒤 `execvp`
- 부모는 `wait4()`로 CPU 시간(`ru_utime + ru_stime`)과 최대 메모리(`ru_maxrss`)를 측정
- `sleep`처럼 CPU를 쓰지 않는 무한 대기는 wall time 감시로 강제 종료

## 한계와 다음 단계

v0.1은 **자원 제한만** 걸고, 파일 시스템·네트워크 격리는 하지 않습니다.
5단계에서 Docker 컨테이너 + seccomp + cgroup으로 격리를 강화할 예정입니다.
