"""Redis 큐를 소비하는 채점 워커.

입력 큐  (LIST)  codearena:submissions
    {"submission_id": 1, "problem_id": "1000", "language": "cpp", "source": "..."}
결과 큐  (LIST)  codearena:results
    {"submission_id": 1, "verdict": "AC", "time_ms": 3, ... }

API 서버는 제출 시 LPUSH, 워커는 BRPOP으로 꺼내 처리한다 (FIFO).
"""
from __future__ import annotations

import json
import logging
import os
import signal
from pathlib import Path

import redis

from .judge import Judge, JudgeResult, Verdict
from .problems import load_problem

SUBMISSION_QUEUE = "codearena:submissions"
RESULT_QUEUE = "codearena:results"

log = logging.getLogger("judge-worker")


def handle(judge: Judge, problems_root: Path, payload: dict) -> dict:
    sid = payload.get("submission_id")
    try:
        problem = load_problem(problems_root, payload["problem_id"])
        result = judge.judge(payload["language"], payload["source"], problem)
    except (KeyError, ValueError, FileNotFoundError) as e:
        log.warning("submission %s rejected: %s", sid, e)
        result = JudgeResult(Verdict.SE, compile_log=str(e))
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

    def run_once(self, timeout: int = 5) -> dict | None:
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

    def run_forever(self):
        log.info("waiting for submissions on %s", SUBMISSION_QUEUE)
        while self.running:
            self.run_once()


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    base = Path(__file__).resolve().parent.parent
    client = redis.Redis.from_url(os.getenv("REDIS_URL", "redis://localhost:6379/0"),
                                  decode_responses=True)
    judge = Judge(Path(os.getenv("RUNNER_PATH", base.parent / "sandbox/build/runner")))
    worker = Worker(client, judge, Path(os.getenv("PROBLEMS_DIR", base / "problems")))
    signal.signal(signal.SIGTERM, worker.stop)
    signal.signal(signal.SIGINT, worker.stop)
    worker.run_forever()


if __name__ == "__main__":
    main()
