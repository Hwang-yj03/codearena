"""악성 제출 코드가 실제로 막히는지 확인한다.

각 테스트는 실제 공격 코드를 샌드박스에서 실행하고, 공격이 실패했는지를 출력이나 판정으로 확인한다.
권한 분리(nobody)는 root에서만 가능하므로 root가 아니면 건너뛴다 (Docker test 이미지는 root로 실행된다).
"""
import os
import tempfile
import textwrap
from pathlib import Path

import pytest

from judge_worker.judge import Verdict
from judge_worker.problems import load_problem

pytestmark = pytest.mark.skipif(os.geteuid() != 0, reason="sandbox privilege drop requires root")

SIGSYS = 31  # seccomp가 금지된 시스템 콜을 막을 때 보내는 시그널
SIGXFSZ = 25


def run(judge, lang: str, code: str, stdin: str = "", **kw) -> dict:
    return judge.run_custom(lang, textwrap.dedent(code), stdin, **kw)


# ------------------------------------------------------------------ 정답 훔치기

def test_cannot_read_testcase_answers(judge, problems_dir):
    answer = problems_dir / "1000" / "3.out"
    result = run(judge, "cpp", f"""
        #include <fstream>
        #include <iostream>
        int main() {{
            std::ifstream f("{answer}");
            std::string s;
            if (f >> s) std::cout << "STOLEN " << s; else std::cout << "BLOCKED";
        }}
    """)
    assert result["stdout"] == "BLOCKED"


def test_cheating_submission_gets_wrong_answer(judge, problems_dir):
    """정답 파일을 읽어 출력하는 '부정행위 코드'가 실제 채점에서 통과하지 못한다."""
    pdir = problems_dir / "1000"
    code = textwrap.dedent(f"""
        import glob, sys
        data = sys.stdin.read()
        for name in sorted(glob.glob("{pdir}/*.in")):
            if open(name).read() == data:
                print(open(name[:-3] + ".out").read(), end="")
    """)
    result = judge.judge("python", code, load_problem(problems_dir, "1000"))
    assert result.verdict != Verdict.AC


def test_cannot_include_answers_at_compile_time(judge, problems_dir):
    answer = problems_dir / "1000" / "1.out"
    result = run(judge, "cpp", f"""
        int main() {{ return
        #include "{answer}"
        ; }}
    """)
    assert result["status"] == "CE"
    assert "Permission denied" in result["compile_log"]


# ------------------------------------------------------------------ 서버 정보 훔치기

def test_environment_is_clean(judge, monkeypatch):
    monkeypatch.setenv("REDIS_URL", "redis://secret-host:6379/0")
    monkeypatch.setenv("DB_PASSWORD", "super-secret")
    result = run(judge, "python", "import os; print(','.join(sorted(os.environ)))")
    assert result["stdout"].strip() == "HOME,LANG,PATH,TMPDIR"


def test_runs_as_unprivileged_user(judge):
    result = run(judge, "python", "import os; print(os.getuid(), os.getgid(), os.getgroups())")
    assert result["stdout"].strip() == "65534 65534 []"


def test_inherited_file_descriptors_are_closed(judge):
    # 워커가 열어 둔 Redis 연결 등이 제출 코드에 넘어가면 안 된다
    result = run(judge, "python", "import os; print(sorted(int(x) for x in os.listdir('/proc/self/fd')))")
    fds = eval(result["stdout"])
    assert [fd for fd in fds if fd > 2] in ([], [3])  # 3은 listdir가 연 디렉터리 자체


# ------------------------------------------------------------------ 네트워크

def test_python_cannot_open_sockets(judge):
    result = run(judge, "python", """
        import socket
        try:
            socket.create_connection(("1.1.1.1", 80), timeout=1)
            print("CONNECTED")
        except OSError as e:
            print("BLOCKED", e.errno)
    """)
    assert result["stdout"].startswith("BLOCKED 13")  # EACCES


def test_cpp_cannot_open_sockets(judge):
    result = run(judge, "cpp", """
        #include <sys/socket.h>
        #include <cstdio>
        int main() { std::printf(socket(AF_INET, SOCK_STREAM, 0) < 0 ? "BLOCKED" : "OPENED"); }
    """)
    assert result["stdout"] == "BLOCKED"


# ------------------------------------------------------------------ 프로세스

def test_fork_bomb_is_killed_immediately(judge):
    result = run(judge, "cpp", """
        #include <unistd.h>
        int main() { while (true) fork(); }
    """)
    assert result["status"] == "RE" and result["signal"] == SIGSYS
    assert result["time_ms"] < 100


def test_shell_commands_are_blocked(judge):
    result = run(judge, "cpp", """
        #include <cstdlib>
        int main() { return std::system("cat /etc/passwd"); }
    """)
    assert result["status"] == "RE" and result["signal"] == SIGSYS
    assert "root:" not in result["stdout"]


def test_python_subprocess_is_blocked(judge):
    result = run(judge, "python", "import subprocess; print(subprocess.check_output(['id']))")
    assert result["status"] == "RE"
    assert "uid=" not in result["stdout"]


def test_cannot_signal_other_processes(judge):
    result = run(judge, "python", """
        import os
        try:
            os.kill(os.getppid(), 9)
            print("KILLED PARENT")
        except PermissionError:
            print("BLOCKED")
    """)
    assert result["stdout"].strip() == "BLOCKED"


def test_ptrace_is_blocked(judge):
    result = run(judge, "cpp", """
        #include <sys/ptrace.h>
        int main() { ptrace(PTRACE_TRACEME, 0, nullptr, nullptr); }
    """)
    assert result["status"] == "RE" and result["signal"] == SIGSYS


# ------------------------------------------------------------------ 파일 시스템

def test_cannot_write_outside_workspace(judge, problems_dir):
    target = problems_dir / "1000" / "1.out"
    result = run(judge, "python", f"""
        try:
            open("{target}", "w").write("0")
            print("OVERWRITTEN")
        except PermissionError:
            print("BLOCKED")
    """)
    assert result["stdout"].strip() == "BLOCKED"
    assert target.read_text() == "3\n"


def test_workspace_is_removed_even_if_submission_creates_directories(judge, monkeypatch):
    # pytest의 tmp_path는 상위 폴더가 root 전용이라 nobody가 들어갈 수 없다. /tmp 아래에 따로 만든다
    base = Path(tempfile.mkdtemp(prefix="ws-test-", dir="/tmp"))
    base.chmod(0o755)
    monkeypatch.setattr("tempfile.tempdir", str(base))
    result = run(judge, "python", """
        import os
        os.makedirs("a/b/c")
        open("a/b/c/junk", "w").write("x" * 1000)
        os.chmod("a/b", 0o500)  # 지우기 어렵게 권한까지 바꿔 둔다
        print("CREATED")
    """)
    assert result["stdout"].strip() == "CREATED"
    assert list(base.iterdir()) == []
    base.rmdir()


def test_output_flood_is_stopped(judge):
    result = run(judge, "cpp", """
        #include <cstdio>
        int main() { for (;;) std::fputs("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", stdout); }
    """, time_limit_ms=5000)
    assert result["status"] == "RE" and result["signal"] == SIGXFSZ


# ------------------------------------------------------------------ 정상 코드는 그대로 동작

def test_deep_recursion_is_allowed(judge):
    # 스택 제한을 메모리 제한까지 늘렸으므로 깊은 DFS가 가능해야 한다
    result = run(judge, "cpp", """
        #include <cstdio>
        int depth(int n) { volatile char pad[32]; pad[0] = 0; return n == 0 ? pad[0] : 1 + depth(n - 1); }
        int main() { std::printf("%d", depth(1000000)); }
    """)
    assert result["status"] == "OK" and result["stdout"] == "1000000"


def test_java_threads_still_work(judge):
    result = run(judge, "java", """
        public class Main {
            public static void main(String[] args) throws Exception {
                Thread t = new Thread(() -> System.out.print("thread "));
                t.start();
                t.join();
                System.out.print("ok");
            }
        }
    """)
    assert result["status"] == "OK", result
    assert result["stdout"] == "thread ok"


def test_abort_is_reported_as_runtime_error(judge):
    # abort()는 tgkill(자기 자신)을 쓴다. 자기 자신에게 보내는 시그널은 막지 않는다
    result = run(judge, "cpp", """
        #include <cassert>
        int main() { assert(1 + 1 == 3); }
    """)
    assert result["status"] == "RE" and result["signal"] == 6  # SIGABRT
