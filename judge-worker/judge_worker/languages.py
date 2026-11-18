"""언어별 컴파일/실행 설정.

새 언어를 추가할 때는 Language 하나만 등록하면 된다.
명령어 안의 {src}, {exe}, {dir}, {mem} 은 채점 시점에 치환된다.
"""
from __future__ import annotations

from dataclasses import dataclass, field


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
    env: dict[str, str] = field(default_factory=dict)


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
        compile_cmd=["python3", "-m", "py_compile", "{src}"],  # 문법 오류를 CE로 잡기 위함
        run_cmd=["python3", "-S", "{src}"],
        time_factor=3.0,
        time_bonus_ms=2000,
    ),
    "java": Language(
        key="java",
        source_name="Main.java",
        compile_cmd=["javac", "-encoding", "UTF-8", "-d", "{dir}", "{src}"],
        run_cmd=["java", "-Xmx{mem}m", "-Xss64m", "-XX:+UseSerialGC", "-cp", "{dir}", "Main"],
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
