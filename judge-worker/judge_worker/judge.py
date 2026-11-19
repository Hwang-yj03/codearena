"""채점 핵심 로직. Redis와 무관하게 동작하도록 분리해 단위 테스트가 가능하다.

흐름: 소스 저장 → (샌드박스에서) 컴파일 → 테스트케이스마다 (샌드박스에서) 실행 → 출력 비교 → 최종 판정

보안 원칙: 제출 코드와, 제출 코드를 다루는 컴파일러는 모두 신뢰하지 않는다.
둘 다 sandbox runner를 거쳐 권한 없는 계정(nobody) + seccomp 아래에서 실행한다.
"""
from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
import tempfile
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path

from .languages import Language, get_language
from .problems import Problem

log = logging.getLogger("judge")

COMPILE_TIME_MS = 15_000
COMPILE_MEMORY_MB = 2048
COMPILE_LOG_LIMIT = 4000   # 사용자에게 보여줄 컴파일 에러 최대 길이
CUSTOM_OUTPUT_LIMIT = 64 * 1024
NOBODY = (65534, 65534)


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


def default_sandbox_user() -> tuple[int, int] | None:
    """root로 실행 중이면 nobody로 권한을 내린다. 아니면(로컬 개발) 권한 분리 없이 실행한다."""
    uid, gid = os.getenv("SANDBOX_UID"), os.getenv("SANDBOX_GID")
    if uid is not None and gid is not None:
        return int(uid), int(gid)
    if os.geteuid() == 0:
        return NOBODY
    return None


class Judge:
    def __init__(self, runner_path: Path, sandbox_user: tuple[int, int] | None = None):
        self.runner_path = Path(runner_path)
        if not self.runner_path.is_file():
            raise FileNotFoundError(f"sandbox runner not found: {self.runner_path}")
        self.sandbox_user = sandbox_user
        if sandbox_user is None:
            log.warning("sandbox user not set: submissions run with the worker's own privileges (dev only)")

    # ------------------------------------------------------------------ public

    def judge(self, language: str, source: str, problem: Problem) -> JudgeResult:
        lang = get_language(language)
        with self._workspace() as workdir:
            return self._judge_in(workdir, lang, source, problem)

    def run_custom(self, language: str, source: str, stdin: str, time_limit_ms: int = 2000,
                   memory_limit_mb: int = 256) -> dict:
        """테스트케이스 대신 직접 준 입력으로 실행한다 ("예제로 실행해 보기"와 보안 테스트에 쓴다)."""
        lang = get_language(language)
        with self._workspace() as workdir:
            src, subs = self._prepare(workdir, lang, source, memory_limit_mb)
            compile_log = self._compile(workdir, lang, subs)
            if compile_log is not None:
                return {"status": "CE", "stdout": "", "compile_log": compile_log}
            inp = workdir / "custom.in"
            inp.write_text(stdin, encoding="utf-8")
            run = self._run(workdir, lang, subs, inp, time_limit_ms, memory_limit_mb)
            out = (workdir / "out.txt").read_bytes()[:CUSTOM_OUTPUT_LIMIT].decode("utf-8", "replace")
            return {**run, "stdout": out, "compile_log": ""}

    # ------------------------------------------------------------------ internals

    class _Workspace:
        def __init__(self, judge: "Judge"):
            self.judge = judge
            self.path: Path | None = None

        def __enter__(self) -> Path:
            self.path = Path(tempfile.mkdtemp(prefix="codearena-"))
            if self.judge.sandbox_user is not None:
                # nobody가 컴파일 결과물을 쓸 수 있어야 한다. 제출 하나에 한 디렉터리만 쓰고 바로 지운다
                self.path.chmod(0o777)
            return self.path

        def __exit__(self, *exc):
            if self.judge.sandbox_user is not None:
                # 제출 코드(nobody)가 만든 하위 폴더는 권한을 줄인 root(CAP_DAC_OVERRIDE 없음)가 지울 수 없다.
                # 같은 nobody 권한으로 (1) 제출 코드가 막아 둔 폴더 권한을 되돌리고 (2) 내용을 지운 뒤,
                # 빈 폴더만 root가 지운다
                for cmd in (["chmod", "-R", "u+rwX", str(self.path)],
                            ["find", str(self.path), "-mindepth", "1", "-delete"]):
                    self.judge._sandbox(mode="compile", cmd=cmd, workdir=self.path, time_ms=5000, memory_mb=256,
                                        stdin="/dev/null", stdout="/dev/null", stderr="/dev/null")
            shutil.rmtree(self.path, ignore_errors=True)
            if self.path.exists():
                log.error("workspace not cleaned up: %s", self.path)

    def _workspace(self) -> "Judge._Workspace":
        return Judge._Workspace(self)

    def _prepare(self, workdir: Path, lang: Language, source: str, memory_mb: int):
        src = workdir / lang.source_name
        src.write_text(source, encoding="utf-8")
        src.chmod(0o644)
        subs = {"{src}": str(src), "{exe}": str(workdir / "main"), "{dir}": str(workdir), "{mem}": str(memory_mb)}
        return src, subs

    @staticmethod
    def _fill(cmd: list[str], subs: dict[str, str]) -> list[str]:
        out = []
        for part in cmd:
            for k, v in subs.items():
                part = part.replace(k, v)
            out.append(part)
        return out

    def _sandbox(self, *, mode: str, cmd: list[str], workdir: Path, time_ms: int, memory_mb: int,
                 stdin: Path | str, stdout: Path | str, stderr: Path | str, as_limit: bool = True) -> dict:
        args = [str(self.runner_path), "--mode", mode, "--time", str(time_ms), "--memory", str(memory_mb),
                "--input", str(stdin), "--output", str(stdout), "--error", str(stderr), "--workdir", str(workdir)]
        if self.sandbox_user is not None:
            args += ["--uid", str(self.sandbox_user[0]), "--gid", str(self.sandbox_user[1])]
        # 워커의 환경 변수(REDIS_URL 등)는 넘기지 않는다. 실행에 꼭 필요한 것만 명시한다
        for key, value in (("PATH", os.environ.get("PATH", "/usr/bin:/bin")), ("LANG", "C.UTF-8"),
                           ("HOME", str(workdir)), ("TMPDIR", str(workdir))):
            args += ["--env", f"{key}={value}"]
        if not as_limit:
            args.append("--no-as-limit")
        args += ["--", *cmd]
        try:
            proc = subprocess.run(args, capture_output=True, text=True, timeout=time_ms / 1000 * 3 + 10)
            return json.loads(proc.stdout)
        except (subprocess.TimeoutExpired, json.JSONDecodeError) as e:
            log.error("sandbox runner failed: %s", e)
            return {"status": "SE", "exit_code": -1, "signal": 0, "time_ms": 0, "memory_kb": 0}

    def _compile(self, workdir: Path, lang: Language, subs: dict[str, str]) -> str | None:
        """성공하면 None, 실패하면 사용자에게 보여줄 메시지를 돌려준다."""
        if not lang.compile_cmd:
            return None
        log_path = workdir / "compile.log"
        res = self._sandbox(mode="compile", cmd=self._fill(lang.compile_cmd, subs), workdir=workdir,
                            time_ms=COMPILE_TIME_MS, memory_mb=COMPILE_MEMORY_MB, stdin="/dev/null",
                            stdout="/dev/null", stderr=log_path, as_limit=False)
        if res["status"] == "OK" and res["exit_code"] == 0:
            return None
        if res["status"] == "TLE":
            return "컴파일 시간이 너무 오래 걸립니다"
        text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
        return text.replace(str(workdir) + "/", "")[:COMPILE_LOG_LIMIT] or "컴파일에 실패했습니다"

    def _run(self, workdir: Path, lang: Language, subs: dict[str, str], stdin: Path, time_ms: int,
             memory_mb: int) -> dict:
        err_path = workdir / "err.txt"
        res = self._sandbox(mode="run", cmd=self._fill(lang.run_cmd, subs), workdir=workdir,
                            time_ms=time_ms, memory_mb=memory_mb, stdin=stdin,
                            stdout=workdir / "out.txt", stderr=err_path, as_limit=lang.as_limit)
        if res["status"] in ("RE", "SE") and err_path.exists():
            # 운영자 진단용으로 서버 로그에만 남긴다. 사용자에게 보여주면 테스트 데이터가 새어 나갈 수 있다
            tail = err_path.read_bytes()[-800:].decode("utf-8", "replace").strip()
            if tail:
                log.info("%s (exit=%s, signal=%s) stderr: %s", res["status"], res["exit_code"], res["signal"], tail)
        return res

    def _judge_in(self, workdir: Path, lang: Language, source: str, problem: Problem) -> JudgeResult:
        total = len(problem.testcases)
        _, subs = self._prepare(workdir, lang, source, problem.memory_limit_mb)

        compile_log = self._compile(workdir, lang, subs)
        if compile_log is not None:
            return JudgeResult(Verdict.CE, total=total, compile_log=compile_log)

        time_limit = int(problem.time_limit_ms * lang.time_factor + lang.time_bonus_ms)
        result = JudgeResult(Verdict.AC, total=total)
        out_path = workdir / "out.txt"

        for case in problem.testcases:
            run = self._run(workdir, lang, subs, case.input_path, time_limit, problem.memory_limit_mb)
            if run["status"] == "SE":
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
