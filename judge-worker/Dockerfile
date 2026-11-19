# 빌드 컨텍스트는 레포 루트 (sandbox 소스가 필요하기 때문)
#   docker compose build judge-worker
#   docker build -f judge-worker/Dockerfile --target test -t codearena-judge-test .
#   docker run --rm --cap-drop ALL --cap-add SETUID --cap-add SETGID --cap-add KILL codearena-judge-test

# ---------- base: 컴파일러, 런타임, sandbox runner, worker 패키지 ----------
FROM eclipse-temurin:21-jdk-noble AS base

RUN apt-get update \
 && apt-get install -y --no-install-recommends g++ python3 python3-venv \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY sandbox/src/runner.cpp sandbox/src/runner.cpp
RUN g++ -O2 -std=c++17 sandbox/src/runner.cpp -o /usr/local/bin/codearena-runner

RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:${PATH}"

COPY judge-worker/pyproject.toml judge-worker/pyproject.toml
COPY judge-worker/judge_worker judge-worker/judge_worker
RUN pip install --no-cache-dir ./judge-worker

# 테스트케이스(특히 정답 .out)는 root만 읽을 수 있게 한다.
# 제출 코드와 컴파일러는 nobody로 실행되므로 정답 파일을 열 수 없다.
# 입력 파일은 runner가 권한을 내려놓기 전에(root로) 열어서 stdin으로 넘긴다.
COPY judge-worker/problems judge-worker/problems
RUN chown -R root:root judge-worker/problems && chmod -R go-rwx judge-worker/problems

ENV RUNNER_PATH=/usr/local/bin/codearena-runner \
    PROBLEMS_DIR=/app/judge-worker/problems \
    REDIS_URL=redis://redis:6379/0 \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# ---------- test: 컨테이너 안에서 pytest 실행 ----------
FROM base AS test
RUN pip install --no-cache-dir pytest fakeredis
COPY judge-worker/tests judge-worker/tests
WORKDIR /app/judge-worker
CMD ["pytest", "-v"]

# ---------- runtime: 실제 워커 ----------
FROM base AS runtime
# 워커는 컨테이너 안에서 root로 돈다. 제출마다 nobody로 권한을 "내려놓기" 위해서다
# (일반 사용자는 다른 사용자로 바꿀 수 없다).
# 대신 docker-compose.yml에서 capability를 SETUID/SETGID/KILL 세 개만 남기고 모두 제거하고,
# 파일 시스템을 읽기 전용으로 만든다. 자세한 내용은 docs/07-sandbox-security.md
ENV SANDBOX_UID=65534 SANDBOX_GID=65534
CMD ["codearena-worker"]
