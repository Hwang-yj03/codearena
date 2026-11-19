import os
import shutil
import subprocess
from pathlib import Path

import pytest

from judge_worker.judge import Judge, default_sandbox_user

ROOT = Path(__file__).resolve().parent.parent          # judge-worker/
SANDBOX = ROOT.parent / "sandbox"
RUNNER = SANDBOX / "build" / "runner"
IS_ROOT = os.geteuid() == 0


@pytest.fixture(scope="session")
def runner() -> Path:
    """테스트 시작 시 sandbox runner를 빌드한다."""
    RUNNER.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["g++", "-O2", "-std=c++17", str(SANDBOX / "src/runner.cpp"), "-o", str(RUNNER)],
                   check=True)
    return RUNNER


@pytest.fixture(scope="session")
def problems_dir(tmp_path_factory) -> Path:
    """운영과 같은 조건: 테스트케이스는 워커(root)만 읽을 수 있다."""
    if not IS_ROOT:
        return ROOT / "problems"
    secured = tmp_path_factory.mktemp("secured") / "problems"
    shutil.copytree(ROOT / "problems", secured)
    for path in [secured, *secured.rglob("*")]:
        path.chmod(0o700 if path.is_dir() else 0o600)
    secured.parent.chmod(0o755)
    return secured


@pytest.fixture(scope="session")
def judge(runner) -> Judge:
    return Judge(runner, default_sandbox_user())


@pytest.fixture
def solution():
    def read(name: str) -> str:
        return (Path(__file__).parent / "solutions" / name).read_text(encoding="utf-8")
    return read
