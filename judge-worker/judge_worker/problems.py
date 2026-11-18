"""문제 메타데이터와 테스트케이스 로딩.

디렉터리 구조:
    problems/<id>/meta.json   {"time_limit_ms": 1000, "memory_limit_mb": 256}
    problems/<id>/1.in, 1.out, 2.in, 2.out, ...

3단계에서 API 서버가 생기면 DB/오브젝트 스토리지에서 받아오는 구현으로 교체한다.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class TestCase:
    index: int
    input_path: Path
    output_path: Path


@dataclass(frozen=True)
class Problem:
    id: str
    time_limit_ms: int
    memory_limit_mb: int
    testcases: list[TestCase]


def load_problem(root: Path, problem_id: str) -> Problem:
    pdir = root / str(problem_id)
    meta_path = pdir / "meta.json"
    if not meta_path.is_file():
        raise FileNotFoundError(f"problem not found: {problem_id}")
    meta = json.loads(meta_path.read_text(encoding="utf-8"))

    cases: list[TestCase] = []
    for inp in pdir.glob("*.in"):
        out = inp.with_suffix(".out")
        if not out.is_file():
            raise FileNotFoundError(f"missing output for {inp.name}")
        cases.append(TestCase(int(inp.stem), inp, out))
    cases.sort(key=lambda c: c.index)
    if not cases:
        raise ValueError(f"problem {problem_id} has no testcases")

    return Problem(
        id=str(problem_id),
        time_limit_ms=int(meta["time_limit_ms"]),
        memory_limit_mb=int(meta["memory_limit_mb"]),
        testcases=cases,
    )
