# 빌드 컨텍스트는 레포 루트 (sandbox 소스가 필요하기 때문)
#   docker compose build judge-worker
#   docker build -f judge-worker/Dockerfile --target test -t codearena-judge-test .

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

COPY judge-worker/problems judge-worker/problems

ENV RUNNER_PATH=/usr/local/bin/codearena-runner \
    PROBLEMS_DIR=/app/judge-worker/problems \
    REDIS_URL=redis://redis:6379/0 \
    PYTHONUNBUFFERED=1

# ---------- test: 컨테이너 안에서 pytest 실행 ----------
FROM base AS test
RUN pip install --no-cache-dir pytest fakeredis
COPY judge-worker/tests judge-worker/tests
WORKDIR /app/judge-worker
CMD ["pytest", "-v"]

# ---------- runtime: 실제 워커 ----------
FROM base AS runtime
# 제출 코드를 root로 실행하지 않기 위해 일반 사용자로 전환
RUN useradd --create-home --shell /usr/sbin/nologin judge
USER judge
CMD ["codearena-worker"]
