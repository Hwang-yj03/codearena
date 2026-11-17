// codearena sandbox runner (v0.1)
// 제출 프로그램을 자식 프로세스로 실행하고 시간/메모리 제한을 걸어 결과를 JSON으로 출력한다.
//
// 사용법:
//   runner --time <ms> --memory <MB> --input <file> --output <file> -- <command> [args...]
//
// 출력 예:
//   {"status":"OK","exit_code":0,"signal":0,"time_ms":12,"memory_kb":3456}
//
// status: OK | TLE(시간 초과) | MLE(메모리 초과) | RE(런타임 에러) | SE(시스템 에러)
//
// v0.1 범위: rlimit 기반 제한만. 파일/네트워크 격리(seccomp, namespace)는 v0.2에서 추가.

#include <sys/resource.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

struct Options {
    long time_ms = 1000;
    long memory_mb = 256;
    std::string input = "/dev/null";
    std::string output = "/dev/null";
    std::vector<char*> command;
};

[[noreturn]] void usage() {
    std::fprintf(stderr,
        "usage: runner --time <ms> --memory <MB> --input <file> --output <file> -- <command> [args...]\n");
    std::exit(2);
}

Options parse(int argc, char** argv) {
    Options opt;
    int i = 1;
    for (; i < argc; ++i) {
        std::string a = argv[i];
        if (a == "--") { ++i; break; }
        if (i + 1 >= argc) usage();
        if (a == "--time") opt.time_ms = std::atol(argv[++i]);
        else if (a == "--memory") opt.memory_mb = std::atol(argv[++i]);
        else if (a == "--input") opt.input = argv[++i];
        else if (a == "--output") opt.output = argv[++i];
        else usage();
    }
    for (; i < argc; ++i) opt.command.push_back(argv[i]);
    if (opt.command.empty() || opt.time_ms <= 0 || opt.memory_mb <= 0) usage();
    opt.command.push_back(nullptr);
    return opt;
}

void print_result(const char* status, int exit_code, int sig, long time_ms, long memory_kb) {
    std::printf("{\"status\":\"%s\",\"exit_code\":%d,\"signal\":%d,\"time_ms\":%ld,\"memory_kb\":%ld}\n",
                status, exit_code, sig, time_ms, memory_kb);
}

long elapsed_ms(const timeval& a, const timeval& b) {
    return (b.tv_sec - a.tv_sec) * 1000L + (b.tv_usec - a.tv_usec) / 1000L;
}

// 자식 프로세스: 제한 설정 후 exec. 실패하면 127로 종료.
[[noreturn]] void run_child(const Options& opt) {
    // CPU 시간 제한(초 단위, 올림). 정밀한 판정은 부모가 wall/cpu time으로 다시 한다.
    rlim_t cpu_sec = static_cast<rlim_t>((opt.time_ms + 999) / 1000) + 1;
    rlimit cpu{cpu_sec, cpu_sec + 1};
    setrlimit(RLIMIT_CPU, &cpu);

    // 주소 공간 제한. 실제 사용량 판정은 ru_maxrss로 한다.
    rlim_t mem = static_cast<rlim_t>(opt.memory_mb) * 1024 * 1024 * 2;
    rlimit as{mem, mem};
    setrlimit(RLIMIT_AS, &as);

    rlimit no_core{0, 0};
    setrlimit(RLIMIT_CORE, &no_core);

    rlimit fsize{64L * 1024 * 1024, 64L * 1024 * 1024};  // 출력 파일 최대 64MB
    setrlimit(RLIMIT_FSIZE, &fsize);

    int in = open(opt.input.c_str(), O_RDONLY);
    int out = open(opt.output.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (in < 0 || out < 0) _exit(127);
    dup2(in, STDIN_FILENO);
    dup2(out, STDOUT_FILENO);
    int devnull = open("/dev/null", O_WRONLY);
    if (devnull >= 0) dup2(devnull, STDERR_FILENO);

    execvp(opt.command[0], opt.command.data());
    _exit(127);
}

}  // namespace

int main(int argc, char** argv) {
    Options opt = parse(argc, argv);

    timeval start{};
    gettimeofday(&start, nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        print_result("SE", -1, 0, 0, 0);
        return 1;
    }
    if (pid == 0) run_child(opt);

    // wall time 감시: sleep 같은 CPU를 안 쓰는 무한 대기도 잡기 위해 제한의 3배에서 강제 종료
    int status = 0;
    rusage usage{};
    const long wall_limit_ms = opt.time_ms * 3;
    bool killed_by_wall = false;
    while (true) {
        pid_t r = wait4(pid, &status, WNOHANG, &usage);
        if (r == pid) break;
        if (r < 0 && errno != EINTR) {
            print_result("SE", -1, 0, 0, 0);
            return 1;
        }
        timeval now{};
        gettimeofday(&now, nullptr);
        if (elapsed_ms(start, now) > wall_limit_ms) {
            kill(pid, SIGKILL);
            killed_by_wall = true;
            wait4(pid, &status, 0, &usage);
            break;
        }
        usleep(1000);
    }

    long cpu_ms = usage.ru_utime.tv_sec * 1000L + usage.ru_utime.tv_usec / 1000L
                + usage.ru_stime.tv_sec * 1000L + usage.ru_stime.tv_usec / 1000L;
    long memory_kb = usage.ru_maxrss;  // Linux: KB 단위
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    int sig = WIFSIGNALED(status) ? WTERMSIG(status) : 0;

    const char* verdict = "OK";
    if (killed_by_wall || cpu_ms > opt.time_ms || sig == SIGXCPU) {
        verdict = "TLE";
        if (cpu_ms < opt.time_ms) cpu_ms = opt.time_ms;  // 표시용
    } else if (memory_kb > opt.memory_mb * 1024) {
        verdict = "MLE";
    } else if (sig != 0 || exit_code != 0) {
        // RLIMIT_AS에 걸린 할당 실패는 보통 SIGSEGV/SIGABRT로 끝난다. v0.2에서 cgroup으로 정확히 구분 예정.
        verdict = "RE";
    }

    print_result(verdict, exit_code, sig, cpu_ms, memory_kb);
    return 0;
}
