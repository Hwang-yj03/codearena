"""개발용: 소스 코드를 큐에 넣고 채점 결과를 기다린다. (3단계에서 API 서버가 이 역할을 대신한다)

    python -m judge_worker.submit --problem 1000 --lang cpp < main.cpp
    docker compose exec -T judge-worker python -m judge_worker.submit --problem 1000 --lang python < a.py
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

import redis

from .worker import RESULT_QUEUE, SUBMISSION_QUEUE


def main():
    p = argparse.ArgumentParser(description="enqueue a submission and wait for the verdict")
    p.add_argument("--problem", required=True)
    p.add_argument("--lang", required=True, choices=["cpp", "python", "java"])
    p.add_argument("--timeout", type=int, default=30)
    a = p.parse_args()

    r = redis.Redis.from_url(os.getenv("REDIS_URL", "redis://localhost:6379/0"), decode_responses=True)
    sid = int(time.time() * 1000)
    r.lpush(SUBMISSION_QUEUE, json.dumps({
        "submission_id": sid, "problem_id": a.problem, "language": a.lang, "source": sys.stdin.read(),
    }))
    print(f"submitted #{sid}, waiting...", file=sys.stderr)

    deadline = time.time() + a.timeout
    while time.time() < deadline:
        item = r.brpop([RESULT_QUEUE], timeout=1)
        if item is None:
            continue
        result = json.loads(item[1])
        if result.get("submission_id") == sid:
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return
        r.rpush(RESULT_QUEUE, item[1])  # 다른 제출의 결과면 되돌려 놓는다
    sys.exit("timed out waiting for result (is the worker running?)")


if __name__ == "__main__":
    main()
