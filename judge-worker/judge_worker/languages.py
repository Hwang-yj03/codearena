"""언어별 컴파일/실행 설정.

새 언어를 추가할 때는 Language 하나만 등록하면 된다.
명령어 안의 {src}, {exe}, {dir}, {mem} 은 채점 시점에 치환된다.
명령은 sandbox runner가 실행하므로 이름만 쓰면 runner에 넘긴 PATH에서 찾는다.
"""
from __future__ import annotations

from dataclasses import dataclass

# JVM이 CPU 수만큼 GC·JIT 스레드를 만들지 않게 고정한다 (스레드 수 제한·채점 시간 편차 대비).
# -XX:-UsePerfData: /tmp/hsperfdata_* 파일을 만들지 않는다 (읽기 전용 파일 시스템 대비)
_JVM_FLAGS = ["-XX:ActiveProcessorCount=1", "-XX:-UsePerfData"]


@dataclass(frozen=True)
class Language:
    key: str
    source_name: str
    compile_cmd: list[str] | None
    run_cmd: list[str]
    # 인터프리터/VM 언어는 기본 시간이 더 걸리므로 보정 배수를 둔다 (백준 방식)
    time_factor: float = 1.0
    time_bonus_ms: int = 0
    # JVM은 가상 메모리를 크게 예약하므로 RLIMIT_AS를 걸면 실행 자체가 안 된다
    as_limit: bool = True


LANGUAGES: dict[str, Language] = {
    "cpp": Language(
        key="cpp",
        source_name="main.cpp",
        compile_cmd=["g++", "-O2", "-std=c++17", "-DONLINE_JUDGE", "-o", "{exe}", "{src}"],
        run_cmd=["{exe}"],
    ),
    "python": Language(
        key="python",
        source_name="main.py",
        # 문법 오류를 CE로 잡는다. py_compile과 달리 __pycache__ 파일을 남기지 않는다
        compile_cmd=["python3", "-S", "-c",
                     "import sys; compile(open(sys.argv[1], encoding='utf-8').read(), 'main.py', 'exec')", "{src}"],
        run_cmd=["python3", "-S", "{src}"],
        time_factor=3.0,
        time_bonus_ms=2000,
    ),
    "java": Language(
        key="java",
        source_name="Main.java",
        compile_cmd=["javac", *[f"-J{f}" for f in _JVM_FLAGS], "-encoding", "UTF-8", "-d", "{dir}", "{src}"],
        run_cmd=["java", *_JVM_FLAGS, "-Xmx{mem}m", "-Xss64m", "-XX:+UseSerialGC", "-cp", "{dir}", "Main"],
        time_factor=2.0,
        time_bonus_ms=1000,
        as_limit=False,
    ),
}


def get_language(key: str) -> Language:
    try:
        return LANGUAGES[key]
    except KeyError as e:
        raise ValueError(f"unsupported language: {key}") from e
