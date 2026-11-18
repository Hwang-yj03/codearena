import json

import fakeredis
import pytest

from judge_worker.judge import Judge
from judge_worker.worker import RESULT_QUEUE, SUBMISSION_QUEUE, Worker


@pytest.fixture
def worker(runner, problems_dir):
    client = fakeredis.FakeRedis(decode_responses=True)
    return Worker(client, Judge(runner), problems_dir)


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
