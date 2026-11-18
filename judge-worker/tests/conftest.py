import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent          # judge-worker/
SANDBOX = ROOT.parent / "sandbox"
RUNNER = SANDBOX / "build" / "runner"


@pytest.fixture(scope="session")
def runner() -> Path:
    """테스트 시작 시 sandbox runner를 빌드한다."""
    RUNNER.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["g++", "-O2", "-std=c++17", str(SANDBOX / "src/runner.cpp"), "-o", str(RUNNER)],
                   check=True)
    return RUNNER


@pytest.fixture(scope="session")
def problems_dir() -> Path:
    return ROOT / "problems"


@pytest.fixture
def solution():
    def read(name: str) -> str:
        return (Path(__file__).parent / "solutions" / name).read_text(encoding="utf-8")
    return read
