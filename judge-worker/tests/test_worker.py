import json

import fakeredis
import pytest

from judge_worker.worker import RESULT_QUEUE, SUBMISSION_QUEUE, Worker


@pytest.fixture
def worker(judge, problems_dir):
    client = fakeredis.FakeRedis(decode_responses=True)
    return Worker(client, judge, problems_dir)


def test_consumes_submission_and_publishes_result(worker, solution):
    worker.client.lpush(SUBMISSION_QUEUE, json.dumps({
        "submission_id": 42, "problem_id": "1000", "language": "cpp", "source": solution("ac.cpp"),
    }))
    out = worker.run_once(timeout=1)
    assert out["submission_id"] == 42 and out["verdict"] == "AC"
    assert json.loads(worker.client.rpop(RESULT_QUEUE))["verdict"] == "AC"


def test_fifo_order(worker, solution):
    for sid in (1, 2, 3):
        worker.client.lpush(SUBMISSION_QUEUE, json.dumps({
            "submission_id": sid, "problem_id": "1000", "language": "python", "source": solution("ac.py"),
        }))
    assert [worker.run_once(timeout=1)["submission_id"] for _ in range(3)] == [1, 2, 3]


def test_unknown_problem_becomes_system_error(worker):
    worker.client.lpush(SUBMISSION_QUEUE, json.dumps({
        "submission_id": 7, "problem_id": "9999", "language": "cpp", "source": "int main(){}",
    }))
    out = worker.run_once(timeout=1)
    assert out["verdict"] == "SE"


def test_empty_queue_returns_none(worker):
    assert worker.run_once(timeout=1) is None


def test_worker_survives_redis_timeouts(worker, solution):
    """redis-py 8의 socket_timeout 기본값(5초)과 BRPOP 대기가 부딪혀 워커가 죽던 문제의 회귀 테스트"""
    import redis

    calls = {"n": 0}
    real_run_once = worker.run_once

    def flaky_run_once(timeout=1):
        calls["n"] += 1
        if calls["n"] <= 2:
            raise redis.exceptions.TimeoutError("Timeout reading from socket")
        worker.running = False
        return real_run_once(timeout=1)

    worker.run_once = flaky_run_once
    slept = []
    worker.run_forever(sleep=slept.append)

    assert calls["n"] == 3
    assert slept == [1, 2]  # 점점 길게 쉬었다가 재시도


def test_unexpected_judge_error_becomes_system_error(worker, solution, monkeypatch):
    def boom(*a, **kw):
        raise PermissionError("unexpected")

    monkeypatch.setattr(worker.judge, "judge", boom)
    worker.client.lpush(SUBMISSION_QUEUE, json.dumps({
        "submission_id": 9, "problem_id": "1000", "language": "cpp", "source": solution("ac.cpp"),
    }))

    out = worker.run_once(timeout=1)

    assert out["verdict"] == "SE"
    assert json.loads(worker.client.rpop(RESULT_QUEUE))["submission_id"] == 9
