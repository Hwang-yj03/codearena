"""Redis 없이 로컬에서 바로 채점해 보는 도구.

    python -m judge_worker.cli --problem 1000 --lang cpp tests/solutions/ac.cpp
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from .judge import Judge
from .problems import load_problem


def main():
    base = Path(__file__).resolve().parent.parent
    p = argparse.ArgumentParser(description="CodeArena local judge")
    p.add_argument("source", type=Path)
    p.add_argument("--problem", required=True)
    p.add_argument("--lang", required=True, choices=["cpp", "python", "java"])
    p.add_argument("--runner", default=os.getenv("RUNNER_PATH", base.parent / "sandbox/build/runner"))
    p.add_argument("--problems-dir", default=base / "problems", type=Path)
    a = p.parse_args()

    problem = load_problem(a.problems_dir, a.problem)
    result = Judge(Path(a.runner)).judge(a.lang, a.source.read_text(encoding="utf-8"), problem)
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
