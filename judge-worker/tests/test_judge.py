import pytest

from judge_worker.judge import Judge, Verdict, outputs_match
from judge_worker.problems import load_problem


@pytest.fixture(scope="module")
def judge(runner):
    return Judge(runner)


@pytest.mark.parametrize("lang,file,expected,passed", [
    ("cpp", "ac.cpp", Verdict.AC, 3),
    ("cpp", "wa_overflow.cpp", Verdict.WA, 2),
    ("cpp", "tle.cpp", Verdict.TLE, 0),
    ("cpp", "mle.cpp", Verdict.MLE, 0),
    ("cpp", "re.cpp", Verdict.RE, 0),
    ("cpp", "ce.cpp", Verdict.CE, 0),
    ("python", "ac.py", Verdict.AC, 3),
    ("python", "wa.py", Verdict.WA, 0),
    ("python", "ce.py", Verdict.CE, 0),
    ("python", "re.py", Verdict.RE, 0),
    ("java", "ac.java", Verdict.AC, 3),
    ("java", "ce.java", Verdict.CE, 0),
])
def test_problem_1000(judge, problems_dir, solution, lang, file, expected, passed):
    result = judge.judge(lang, solution(file), load_problem(problems_dir, "1000"))
    assert result.verdict == expected, result.to_dict()
    assert result.passed == passed
    assert result.total == 3


def test_compile_error_log_is_returned_without_temp_path(judge, problems_dir, solution):
    result = judge.judge("cpp", solution("ce.cpp"), load_problem(problems_dir, "1000"))
    assert "undefined_variable" in result.compile_log
    assert "codearena-" not in result.compile_log


@pytest.mark.parametrize("lang,file", [("cpp", "sum_ac.cpp"), ("python", "sum_ac.py")])
def test_large_input(judge, problems_dir, solution, lang, file):
    result = judge.judge(lang, solution(file), load_problem(problems_dir, "1001"))
    assert result.verdict == Verdict.AC, result.to_dict()


def test_python_gets_extra_time_but_still_tle(judge, problems_dir, solution):
    result = judge.judge("python", solution("tle.py"), load_problem(problems_dir, "1000"))
    assert result.verdict == Verdict.TLE


def test_unknown_language(judge, problems_dir):
    with pytest.raises(ValueError):
        judge.judge("brainfuck", "+", load_problem(problems_dir, "1000"))


@pytest.mark.parametrize("expected,actual,ok", [
    ("3\n", "3", True),
    ("3\n", "3   \n\n\n", True),
    ("1 2\n3\n", "1 2\r\n3\r\n", True),
    ("3\n", "4\n", False),
    ("1\n2\n", "1\n", False),
])
def test_outputs_match(expected, actual, ok):
    assert outputs_match(expected, actual) is ok
