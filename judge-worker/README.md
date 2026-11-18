# judge-worker

Redis 큐에서 제출을 꺼내 **컴파일 → 테스트케이스별 실행 → 출력 비교 → 판정**까지 처리하는 Python 워커입니다.
실행과 자원 측정은 [`sandbox/`](../sandbox)의 C++ runner가 담당합니다.

```
API ──LPUSH──▶ codearena:submissions ──BRPOP──▶ judge-worker ──▶ sandbox runner
                                                     │
API ◀──BRPOP── codearena:results ◀──LPUSH────────────┘
```

## 지원 언어

| 언어 | 컴파일 | 시간 제한 보정 | 비고 |
|---|---|---|---|
| C++17 | `g++ -O2` | ×1 | |
| Python 3 | `py_compile` (문법 오류 → CE) | ×3 + 2초 | |
| Java 21 | `javac` | ×2 + 1초 | JVM 특성상 주소 공간 제한 대신 `-Xmx`, RSS로 판정 |

## 판정

`AC` 정답 · `WA` 오답 · `TLE` 시간 초과 · `MLE` 메모리 초과 · `RE` 런타임 에러 · `CE` 컴파일 에러 · `SE` 시스템 에러

첫 번째로 실패한 테스트케이스에서 채점을 멈추고 그 판정을 최종 결과로 합니다.

## 구조

```
judge_worker/
├── languages.py   언어별 컴파일/실행 명령, 시간 보정
├── problems.py    문제 메타데이터와 테스트케이스 로딩
├── judge.py       채점 핵심 로직 (Redis와 분리 → 단위 테스트 가능)
├── worker.py      Redis 큐 소비 루프
├── cli.py         Redis 없이 로컬 채점
└── submit.py      개발용 제출 도구
problems/<id>/     meta.json + N.in / N.out
tests/             pytest (26개: 언어별 AC/WA/TLE/MLE/RE/CE, 큐 동작)
```

## 실행 (Docker, Windows/macOS/Linux 공통)

sandbox runner가 Linux 전용 시스템 콜(`fork`, `setrlimit`, `wait4`)을 쓰기 때문에 Docker로 실행합니다.

```bash
# 레포 루트에서

# 테스트
docker build -f judge-worker/Dockerfile --target test -t codearena-judge-test .
docker run --rm codearena-judge-test

# 워커 실행
docker compose up -d --build redis judge-worker
docker compose logs -f judge-worker

# 제출해 보기
docker compose exec -T judge-worker python -m judge_worker.submit --problem 1000 --lang python < judge-worker/tests/solutions/ac.py
```

## 실행 (Linux / WSL에서 직접)

```bash
cd judge-worker
python3 -m venv .venv && source .venv/bin/activate
pip install -e '.[dev]'
pytest
python -m judge_worker.cli --problem 1000 --lang cpp tests/solutions/wa_overflow.cpp
```
