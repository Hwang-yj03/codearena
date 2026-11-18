"""채점 핵심 로직. Redis와 무관하게 동작하도록 분리해 단위 테스트가 가능하다.

흐름: 소스 저장 → 컴파일 → 테스트케이스마다 sandbox runner 실행 → 출력 비교 → 최종 판정
"""
from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path

from .languages import Language, get_language
from .problems import Problem

COMPILE_TIMEOUT_SEC = 15
COMPILE_LOG_LIMIT = 4000  # 사용자에게 보여줄 컴파일 에러 최대 길이


class Verdict(str, Enum):
    AC = "AC"    # Accepted
    WA = "WA"    # Wrong Answer
    TLE = "TLE"  # Time Limit Exceeded
    MLE = "MLE"  # Memory Limit Exceeded
    RE = "RE"    # Runtime Error
    CE = "CE"    # Compile Error
    SE = "SE"    # System Error (채점 시스템 문제)


@dataclass
class CaseResult:
    index: int
    verdict: Verdict
    time_ms: int
    memory_kb: int


@dataclass
class JudgeResult:
    verdict: Verdict
    time_ms: int = 0
    memory_kb: int = 0
    passed: int = 0
    total: int = 0
    compile_log: str = ""
    cases: list[CaseResult] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["verdict"] = self.verdict.value
        for c in d["cases"]:
            c["verdict"] = c["verdict"].value
        return d


def outputs_match(expected: str, actual: str) -> bool:
    """줄 끝 공백과 마지막 빈 줄 차이는 무시한다 (대부분의 저지와 동일한 규칙)."""
    def normalize(s: str) -> list[str]:
        lines = [line.rstrip() for line in s.replace("\r\n", "\n").split("\n")]
        while lines and lines[-1] == "":
            lines.pop()
        return lines
    return normalize(expected) == normalize(actual)


class Judge:
    def __init__(self, runner_path: Path):
        self.runner_path = Path(runner_path)
        if not self.runner_path.is_file():
            raise FileNotFoundError(f"sandbox runner not found: {self.runner_path}")

    def judge(self, language: str, source: str, problem: Problem) -> JudgeResult:
        lang = get_language(language)
        workdir = Path(tempfile.mkdtemp(prefix="codearena-"))
        try:
            return self._judge_in(workdir, lang, source, problem)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    # ------------------------------------------------------------------

    def _judge_in(self, workdir: Path, lang: Language, source: str, problem: Problem) -> JudgeResult:
        src = workdir / lang.source_name
        src.write_text(source, encoding="utf-8")
        exe = workdir / "main"
        subs = {"{src}": str(src), "{exe}": str(exe), "{dir}": str(workdir),
                "{mem}": str(problem.memory_limit_mb)}

        def fill(cmd: list[str]) -> list[str]:
            out = []
            for part in cmd:
                for k, v in subs.items():
                    part = part.replace(k, v)
                out.append(part)
            return out

        total = len(problem.testcases)

        # 1) 컴파일
        if lang.compile_cmd:
            try:
                proc = subprocess.run(fill(lang.compile_cmd), cwd=workdir, capture_output=True,
                                      text=True, timeout=COMPILE_TIMEOUT_SEC)
            except subprocess.TimeoutExpired:
                return JudgeResult(Verdict.CE, total=total, compile_log="compile timed out")
            if proc.returncode != 0:
                log = (proc.stderr or proc.stdout).replace(str(workdir) + "/", "")
                return JudgeResult(Verdict.CE, total=total, compile_log=log[:COMPILE_LOG_LIMIT])

        # 2) 테스트케이스별 실행
        time_limit = int(problem.time_limit_ms * lang.time_factor + lang.time_bonus_ms)
        run_cmd = fill(lang.run_cmd)
        result = JudgeResult(Verdict.AC, total=total)
        out_path = workdir / "out.txt"

        for case in problem.testcases:
            cmd = [str(self.runner_path), "--time", str(time_limit),
                   "--memory", str(problem.memory_limit_mb),
                   "--input", str(case.input_path), "--output", str(out_path)]
            if not lang.as_limit:
                cmd.append("--no-as-limit")
            cmd += ["--", *run_cmd]

            try:
                proc = subprocess.run(cmd, cwd=workdir, capture_output=True, text=True,
                                      timeout=time_limit / 1000 * 3 + 5)
                run = json.loads(proc.stdout)
            except (subprocess.TimeoutExpired, json.JSONDecodeError):
                result.verdict = Verdict.SE
                break

            verdict = Verdict(run["status"]) if run["status"] != "OK" else None
            if verdict is None:
                actual = out_path.read_text(encoding="utf-8", errors="replace")
                expected = case.output_path.read_text(encoding="utf-8")
                verdict = Verdict.AC if outputs_match(expected, actual) else Verdict.WA

            result.cases.append(CaseResult(case.index, verdict, run["time_ms"], run["memory_kb"]))
            result.time_ms = max(result.time_ms, run["time_ms"])
            result.memory_kb = max(result.memory_kb, run["memory_kb"])

            if verdict != Verdict.AC:
                result.verdict = verdict  # 첫 실패에서 중단 (백준 방식)
                break
            result.passed += 1

        return result
