"""Redis 큐를 소비하는 채점 워커.

입력 큐  (LIST)  codearena:submissions
    {"submission_id": 1, "problem_id": "1000", "language": "cpp", "source": "..."}
결과 큐  (LIST)  codearena:results
    {"submission_id": 1, "verdict": "AC", "time_ms": 3, ... }

API 서버는 제출 시 LPUSH, 워커는 BRPOP으로 꺼내 처리한다 (FIFO).

장애 원칙: 워커 프로세스는 어떤 제출이나 Redis 장애 때문에도 죽지 않는다.
- Redis 연결 문제 → 잠시 쉬었다가 재시도 (점점 길게, 최대 10초)
- 채점 중 예상 못한 예외 → 그 제출만 SE로 결과를 보내고 다음 제출로
"""
from __future__ import annotations

import json
import logging
import os
import signal
import time
from pathlib import Path

import redis

from .judge import Judge, JudgeResult, Verdict, default_sandbox_user
from .problems import load_problem

SUBMISSION_QUEUE = "codearena:submissions"
RESULT_QUEUE = "codearena:results"
POLL_TIMEOUT_SEC = 5
# redis-py 8부터 socket_timeout 기본값이 5초다. BRPOP 대기 시간과 같으면 조용할 때 타임아웃 예외가 난다.
# 소켓 대기 시간은 BRPOP 대기 시간보다 반드시 길어야 한다.
SOCKET_TIMEOUT_SEC = POLL_TIMEOUT_SEC + 10
MAX_BACKOFF_SEC = 10

log = logging.getLogger("judge-worker")


def handle(judge: Judge, problems_root: Path, payload: dict) -> dict:
    sid = payload.get("submission_id")
    try:
        problem = load_problem(problems_root, payload["problem_id"])
        result = judge.judge(payload["language"], payload["source"], problem)
    except (KeyError, ValueError, FileNotFoundError) as e:
        log.warning("submission %s rejected: %s", sid, e)
        result = JudgeResult(Verdict.SE, compile_log=str(e))
    except Exception:  # noqa: BLE001 - 워커는 어떤 제출 때문에도 죽으면 안 된다
        log.exception("submission %s failed unexpectedly", sid)
        result = JudgeResult(Verdict.SE)
    return {"submission_id": sid, **result.to_dict()}


class Worker:
    def __init__(self, client: redis.Redis, judge: Judge, problems_root: Path):
        self.client = client
        self.judge = judge
        self.problems_root = problems_root
        self.running = True

    def stop(self, *_):
        log.info("shutdown requested, finishing current submission")
        self.running = False

    def run_once(self, timeout: int = POLL_TIMEOUT_SEC) -> dict | None:
        item = self.client.brpop([SUBMISSION_QUEUE], timeout=timeout)
        if item is None:
            return None
        _, raw = item
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            log.error("invalid payload: %r", raw[:200])
            return None
        out = handle(self.judge, self.problems_root, payload)
        self.client.lpush(RESULT_QUEUE, json.dumps(out))
        log.info("submission %s -> %s (%dms, %dKB)", out["submission_id"], out["verdict"],
                 out["time_ms"], out["memory_kb"])
        return out

    def run_forever(self, sleep=time.sleep):
        log.info("waiting for submissions on %s", SUBMISSION_QUEUE)
        backoff = 1
        while self.running:
            try:
                self.run_once()
                backoff = 1
            except (redis.exceptions.ConnectionError, redis.exceptions.TimeoutError) as e:
                log.warning("redis unavailable (%s), retrying in %ss", e, backoff)
                sleep(backoff)
                backoff = min(backoff * 2, MAX_BACKOFF_SEC)


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    base = Path(__file__).resolve().parent.parent
    client = redis.Redis.from_url(
        os.getenv("REDIS_URL", "redis://localhost:6379/0"),
        decode_responses=True,
        socket_timeout=SOCKET_TIMEOUT_SEC,
        socket_connect_timeout=5,
        health_check_interval=30,
    )
    judge = Judge(Path(os.getenv("RUNNER_PATH", base.parent / "sandbox/build/runner")), default_sandbox_user())
    worker = Worker(client, judge, Path(os.getenv("PROBLEMS_DIR", base / "problems")))
    signal.signal(signal.SIGTERM, worker.stop)
    signal.signal(signal.SIGINT, worker.stop)
    worker.run_forever()


if __name__ == "__main__":
    main()
