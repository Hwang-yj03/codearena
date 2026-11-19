// codearena sandbox runner (v0.2)
//
// 신뢰할 수 없는 프로그램(제출 코드, 또는 제출 코드를 컴파일하는 컴파일러)을 자식 프로세스로 실행하고
// 시간·메모리를 측정해 JSON으로 출력한다. 여러 겹의 방어를 순서대로 건다.
//
//   1. 자원 제한   setrlimit: CPU 시간, 주소 공간, 스택, 출력 크기, 프로세스(스레드) 수, 열린 파일 수
//   2. 입출력 고정 입력·출력 파일은 권한을 내려놓기 "전에" 열어서 넘긴다 (정답 파일은 직접 열 수 없다)
//   3. 권한 분리   --uid/--gid 로 nobody 같은 계정으로 바꾼다 (root로 실행했을 때만)
//   4. 환경 청소   상속된 파일 디스크립터를 닫고, 환경 변수는 --env 로 준 것만 넘긴다
//   5. seccomp    네트워크, 프로세스 생성, 디버깅, 권한 변경, 커널 조작 시스템 콜을 커널 수준에서 차단
//
// 사용법:
//   runner --time <ms> --memory <MB> --input <file> --output <file> [options] -- <command> [args...]
//
// 옵션:
//   --mode run|compile   run(기본): 프로세스 생성 금지, 엄격한 seccomp
//                        compile : 컴파일러가 하위 프로세스(cc1plus, as, ld)를 띄울 수 있게 프로세스 생성 허용
//   --error <file>       stderr를 저장할 파일 (기본 /dev/null). 컴파일 에러 메시지 수집용
//   --workdir <dir>      실행 디렉터리
//   --uid <n> --gid <n>  이 계정으로 권한을 내려서 실행 (root일 때만 가능)
//   --env KEY=VALUE      자식에게 넘길 환경 변수 (반복 가능). 지정하지 않으면 PATH만 넘긴다
//   --no-as-limit        주소 공간 제한을 끈다. JVM처럼 가상 메모리를 크게 예약하는 런타임용
//   --no-seccomp         seccomp를 끈다 (seccomp를 지원하지 않는 환경에서 개발할 때만)
//
// 출력 예:
//   {"status":"OK","exit_code":0,"signal":0,"time_ms":12,"memory_kb":3456}
//   status: OK | TLE | MLE | RE | SE
//   seccomp에 막힌 시스템 콜을 호출하면 SIGSYS(31)로 종료되어 RE가 된다.

#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <grp.h>
#include <sched.h>
#include <signal.h>
#include <unistd.h>

#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>

#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

enum class Mode { Run, Compile };

struct Options {
    long time_ms = 1000;
    long memory_mb = 256;
    std::string input = "/dev/null";
    std::string output = "/dev/null";
    std::string error = "/dev/null";
    std::string workdir;
    Mode mode = Mode::Run;
    bool as_limit = true;
    bool seccomp = true;
    long uid = -1;
    long gid = -1;
    std::vector<std::string> env;
    std::vector<std::string> command;
};

[[noreturn]] void usage() {
    std::fprintf(stderr,
        "usage: runner --time <ms> --memory <MB> --input <file> --output <file> "
        "[--mode run|compile] [--error <file>] [--workdir <dir>] [--uid <n> --gid <n>] "
        "[--env KEY=VALUE]... [--no-as-limit] [--no-seccomp] -- <command> [args...]\n");
    std::exit(2);
}

Options parse(int argc, char** argv) {
    Options opt;
    int i = 1;
    for (; i < argc; ++i) {
        std::string a = argv[i];
        if (a == "--") { ++i; break; }
        if (a == "--no-as-limit") { opt.as_limit = false; continue; }
        if (a == "--no-seccomp") { opt.seccomp = false; continue; }
        if (i + 1 >= argc) usage();
        std::string v = argv[++i];
        if (a == "--time") opt.time_ms = std::atol(v.c_str());
        else if (a == "--memory") opt.memory_mb = std::atol(v.c_str());
        else if (a == "--input") opt.input = v;
        else if (a == "--output") opt.output = v;
        else if (a == "--error") opt.error = v;
        else if (a == "--workdir") opt.workdir = v;
        else if (a == "--uid") opt.uid = std::atol(v.c_str());
        else if (a == "--gid") opt.gid = std::atol(v.c_str());
        else if (a == "--env") opt.env.push_back(v);
        else if (a == "--mode") {
            if (v == "run") opt.mode = Mode::Run;
            else if (v == "compile") opt.mode = Mode::Compile;
            else usage();
        } else usage();
    }
    for (; i < argc; ++i) opt.command.push_back(argv[i]);
    if (opt.command.empty() || opt.time_ms <= 0 || opt.memory_mb <= 0) usage();
    if ((opt.uid >= 0) != (opt.gid >= 0)) usage();
    if (opt.env.empty()) opt.env.push_back("PATH=/usr/local/bin:/usr/bin:/bin");
    return opt;
}

void print_result(const char* status, int exit_code, int sig, long time_ms, long memory_kb) {
    std::printf("{\"status\":\"%s\",\"exit_code\":%d,\"signal\":%d,\"time_ms\":%ld,\"memory_kb\":%ld}\n",
                status, exit_code, sig, time_ms, memory_kb);
    std::fflush(stdout);
}

long elapsed_ms(const timeval& a, const timeval& b) {
    return (b.tv_sec - a.tv_sec) * 1000L + (b.tv_usec - a.tv_usec) / 1000L;
}

// 실행 파일 경로를 자식에게 넘길 PATH 기준으로 찾는다 (runner 자신의 PATH가 아니라)
std::string resolve(const std::string& cmd, const std::vector<std::string>& env) {
    if (cmd.find('/') != std::string::npos) return cmd;
    std::string path = "/usr/local/bin:/usr/bin:/bin";
    for (const auto& e : env) if (e.rfind("PATH=", 0) == 0) path = e.substr(5);
    size_t start = 0;
    while (start <= path.size()) {
        size_t end = path.find(':', start);
        std::string dir = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
        std::string full = (dir.empty() ? "." : dir) + "/" + cmd;
        if (access(full.c_str(), X_OK) == 0) return full;
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return cmd;
}

// ---------------------------------------------------------------- seccomp

#if defined(__x86_64__)
constexpr unsigned kArch = AUDIT_ARCH_X86_64;
#elif defined(__aarch64__)
constexpr unsigned kArch = AUDIT_ARCH_AARCH64;
#else
#error "seccomp filter supports x86_64 and aarch64 only"
#endif

class Filter {
public:
    void kill(unsigned nr) { deny(nr, SECCOMP_RET_KILL_PROCESS); }
    void err(unsigned nr, int e) { deny(nr, SECCOMP_RET_ERRNO | (e & SECCOMP_RET_DATA)); }

    // nr 시스템 콜은 첫 번째 인자에 flag 비트가 있을 때만 허용 (clone에서 스레드만 허용)
    void allow_if_arg0_has(unsigned nr, unsigned flag) {
        add(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, nr, 0, 4));
        add(BPF_STMT(BPF_LD | BPF_W | BPF_ABS, arg0_low()));
        add(BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, flag, 1, 0));
        add(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS));
        add(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));
    }

    // nr 시스템 콜은 첫 번째 인자가 value일 때만 허용 (kill/tgkill을 자기 자신에게만)
    void allow_if_arg0_is(unsigned nr, unsigned value) {
        add(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, nr, 0, 4));
        add(BPF_STMT(BPF_LD | BPF_W | BPF_ABS, arg0_low()));
        add(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, value, 1, 0));
        add(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM));
        add(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));
    }

    bool install() {
        std::vector<sock_filter> prog;
        // 아키텍처가 다르면(32비트 호환 ABI로 우회 시도 등) 즉시 종료
        prog.push_back(BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)));
        prog.push_back(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, kArch, 1, 0));
        prog.push_back(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS));
        prog.push_back(BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)));
#if defined(__x86_64__)
        // x32 ABI(시스템 콜 번호에 0x40000000 비트) 우회 차단
        prog.push_back(BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0x40000000, 0, 1));
        prog.push_back(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS));
#endif
        prog.insert(prog.end(), body_.begin(), body_.end());
        prog.push_back(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));

        sock_fprog fprog{static_cast<unsigned short>(prog.size()), prog.data()};
        return syscall(SYS_seccomp, SECCOMP_SET_MODE_FILTER, 0, &fprog) == 0
            || prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &fprog) == 0;
    }

private:
    std::vector<sock_filter> body_;

    void add(sock_filter f) { body_.push_back(f); }

    void deny(unsigned nr, unsigned action) {
        add(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, nr, 0, 1));
        add(BPF_STMT(BPF_RET | BPF_K, action));
        // 다음 검사를 위해 A 레지스터에 시스템 콜 번호를 다시 싣는다 (조건부 검사가 A를 바꾸므로)
        add(BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)));
    }

    static unsigned arg0_low() {
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
        return offsetof(seccomp_data, args[0]);
#else
        return offsetof(seccomp_data, args[0]) + 4;
#endif
    }
};

bool install_seccomp(Mode mode, pid_t self) {
    Filter f;

    // 커널·시스템 조작: 정상적인 풀이 코드나 컴파일러가 절대 부르지 않는다 → 즉시 종료
    for (unsigned nr : {
             SYS_ptrace, SYS_process_vm_readv, SYS_process_vm_writev,
             SYS_mount, SYS_umount2, SYS_pivot_root, SYS_chroot, SYS_unshare, SYS_setns,
             SYS_reboot, SYS_kexec_load, SYS_init_module, SYS_finit_module, SYS_delete_module,
             SYS_bpf, SYS_perf_event_open, SYS_keyctl, SYS_add_key, SYS_request_key,
             SYS_setuid, SYS_setgid, SYS_setreuid, SYS_setregid, SYS_setresuid, SYS_setresgid,
             SYS_setgroups, SYS_setfsuid, SYS_setfsgid, SYS_capset,
             SYS_swapon, SYS_swapoff, SYS_acct, SYS_settimeofday, SYS_clock_settime, SYS_adjtimex,
             SYS_userfaultfd, SYS_io_uring_setup, SYS_io_uring_enter, SYS_io_uring_register,
             SYS_name_to_handle_at, SYS_open_by_handle_at, SYS_fanotify_init, SYS_quotactl,
             SYS_syslog, SYS_vhangup, SYS_setsid, SYS_setpgid,
#ifdef SYS_kexec_file_load
             SYS_kexec_file_load,
#endif
#ifdef SYS_iopl
             SYS_iopl, SYS_ioperm,
#endif
         }) {
        f.kill(nr);
    }
    f.err(SYS_personality, EPERM);

    // 네트워크: 소켓을 아예 만들 수 없다. 프로그램은 EACCES를 받고 정상적으로 실패한다
    for (unsigned nr : {SYS_socket, SYS_socketpair}) f.err(nr, EACCES);

    // glibc는 clone3이 없으면(ENOSYS) clone으로 대체한다. clone은 인자를 검사할 수 있어서 clone3를 막는다
    f.err(SYS_clone3, ENOSYS);

    if (mode == Mode::Run) {
        // 프로세스 생성 금지(포크 폭탄, system() 차단). 스레드(CLONE_THREAD)는 허용: JVM이 필요로 한다
#ifdef SYS_fork
        f.kill(SYS_fork);
        f.kill(SYS_vfork);
#endif
        f.allow_if_arg0_has(SYS_clone, CLONE_THREAD);
        // 시그널은 자기 자신에게만 (abort(), raise()는 tgkill(자기 pid, ...)를 쓴다)
        f.allow_if_arg0_is(SYS_kill, static_cast<unsigned>(self));
        f.allow_if_arg0_is(SYS_tgkill, static_cast<unsigned>(self));
        f.err(SYS_tkill, EPERM);
    }
    return f.install();
}

// ---------------------------------------------------------------- child

// 자식이 execve 전에 실패하면 이 파이프로 알린다. O_CLOEXEC라서 execve가 성공하면 자동으로 닫혀
// 부모는 "아무것도 못 읽음 = 실행 성공"으로 구분할 수 있다 (제출 코드의 exit(127)과 헷갈리지 않는다)
int g_fail_pipe = -1;

[[noreturn]] void child_fail(const char* what) {
    int saved = errno;
    std::fprintf(stderr, "runner: %s: %s\n", what, std::strerror(saved));
    if (g_fail_pipe >= 0) {
        ssize_t ignored = write(g_fail_pipe, &saved, sizeof saved);
        (void)ignored;
    }
    _exit(127);
}

void set_limit(int resource, rlim_t value) {
    rlimit r{value, value};
    setrlimit(resource, &r);
}

[[noreturn]] void run_child(const Options& opt) {
    // 시간 초과 시 하위 프로세스까지 한 번에 죽일 수 있도록 별도 프로세스 그룹
    setpgid(0, 0);

    // 1) 입출력 파일은 권한을 내려놓기 전에 연다. 테스트케이스 파일은 root만 읽을 수 있어도 된다
    int in = open(opt.input.c_str(), O_RDONLY | O_CLOEXEC);
    int out = open(opt.output.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    int err = open(opt.error.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (in < 0 || out < 0 || err < 0) child_fail("open io");
    if (dup2(in, STDIN_FILENO) < 0 || dup2(out, STDOUT_FILENO) < 0 || dup2(err, STDERR_FILENO) < 0)
        child_fail("dup2");

    // 2) 상속된 나머지 파일 디스크립터를 모두 닫는다 (워커의 Redis 연결 등이 새지 않게).
    //    실패 알림 파이프만 남겨두고, 그 파이프는 O_CLOEXEC라 execve 순간 닫힌다
    int keep = g_fail_pipe;
    if (keep >= 0 && keep != 3) {
        if (dup2(keep, 3) < 0) child_fail("dup2 pipe");
        fcntl(3, F_SETFD, FD_CLOEXEC);
        g_fail_pipe = 3;
    }
    unsigned first_to_close = g_fail_pipe >= 0 ? 4U : 3U;
#ifdef SYS_close_range
    if (syscall(SYS_close_range, first_to_close, ~0U, 0U) != 0)
#endif
        for (int fd = static_cast<int>(first_to_close); fd < 4096; ++fd) close(fd);

    if (!opt.workdir.empty() && chdir(opt.workdir.c_str()) != 0) child_fail("chdir");

    // 3) 자원 제한. 권한을 내려놓기 전에 걸어야 제출 코드가 hard limit을 올릴 수 없다
    rlim_t cpu_sec = static_cast<rlim_t>((opt.time_ms + 999) / 1000) + 1;
    rlimit cpu{cpu_sec, cpu_sec + 1};
    setrlimit(RLIMIT_CPU, &cpu);
    rlim_t mem = static_cast<rlim_t>(opt.memory_mb) * 1024 * 1024;
    if (opt.as_limit) set_limit(RLIMIT_AS, mem * 2);
    set_limit(RLIMIT_STACK, mem);                       // 깊은 재귀(DFS)를 메모리 제한까지 허용
    set_limit(RLIMIT_CORE, 0);
    set_limit(RLIMIT_FSIZE, 64L * 1024 * 1024);          // 출력·생성 파일 최대 64MB
    set_limit(RLIMIT_NOFILE, opt.mode == Mode::Run ? 128 : 256);
    // 같은 uid의 프로세스+스레드 수. run 모드는 fork가 막혀 있어 스레드만 해당한다
    set_limit(RLIMIT_NPROC, opt.mode == Mode::Run ? 128 : 64);

    // 4) 권한 분리: 보조 그룹 → gid → uid 순서. 순서가 바뀌면 그룹 권한이 남는다
    if (opt.uid >= 0) {
        if (setgroups(0, nullptr) != 0) child_fail("setgroups");
        if (setresgid(opt.gid, opt.gid, opt.gid) != 0) child_fail("setgid");
        if (setresuid(opt.uid, opt.uid, opt.uid) != 0) child_fail("setuid");
        if (setuid(0) == 0) child_fail("privilege drop did not stick");
    }

    // 5) setuid 실행 파일 등으로 권한을 다시 얻는 길을 막는다 (seccomp의 전제 조건이기도 하다)
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) child_fail("no_new_privs");
    prctl(PR_SET_DUMPABLE, 0, 0, 0, 0);

    std::string exe = resolve(opt.command[0], opt.env);
    std::vector<char*> argv;
    for (const auto& a : opt.command) argv.push_back(const_cast<char*>(a.c_str()));
    argv.push_back(nullptr);
    std::vector<char*> envp;
    for (const auto& e : opt.env) envp.push_back(const_cast<char*>(e.c_str()));
    envp.push_back(nullptr);

    // 6) seccomp는 마지막에: 위에서 쓴 setuid 등이 이후로는 막힌다. execve 자체는 허용된다
    if (opt.seccomp && !install_seccomp(opt.mode, getpid())) child_fail("seccomp");

    execve(exe.c_str(), argv.data(), envp.data());
    child_fail("execve");
}

}  // namespace

int main(int argc, char** argv) {
    Options opt = parse(argc, argv);
    if (opt.uid >= 0 && geteuid() != 0) {
        std::fprintf(stderr, "runner: --uid requires root\n");
        print_result("SE", -1, 0, 0, 0);
        return 1;
    }

    timeval start{};
    gettimeofday(&start, nullptr);

    int fail_pipe[2];
    if (pipe2(fail_pipe, O_CLOEXEC) != 0) {
        print_result("SE", -1, 0, 0, 0);
        return 1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        print_result("SE", -1, 0, 0, 0);
        return 1;
    }
    if (pid == 0) {
        close(fail_pipe[0]);
        g_fail_pipe = fail_pipe[1];
        run_child(opt);
    }
    close(fail_pipe[1]);

    // wall time 감시: sleep처럼 CPU를 쓰지 않는 무한 대기도 잡기 위해 제한의 3배에서 강제 종료
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
            kill(-pid, SIGKILL);
            killed_by_wall = true;
            wait4(pid, &status, 0, &usage);
            break;
        }
        usleep(1000);
    }
    // 남은 하위 프로세스 정리 (compile 모드에서 컴파일러가 띄운 프로세스 등)
    kill(-pid, SIGKILL);

    int child_errno = 0;
    bool setup_failed = read(fail_pipe[0], &child_errno, sizeof child_errno) > 0;
    close(fail_pipe[0]);

    long cpu_ms = usage.ru_utime.tv_sec * 1000L + usage.ru_utime.tv_usec / 1000L
                + usage.ru_stime.tv_sec * 1000L + usage.ru_stime.tv_usec / 1000L;
    long memory_kb = usage.ru_maxrss;  // Linux: KB 단위
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    int sig = WIFSIGNALED(status) ? WTERMSIG(status) : 0;

    const char* verdict = "OK";
    if (setup_failed) {
        // 제출 코드가 실행되기도 전에 실패 (실행 파일 없음, 권한 문제 등): 채점 시스템 문제
        verdict = "SE";
    } else if (killed_by_wall || cpu_ms > opt.time_ms || sig == SIGXCPU) {
        verdict = "TLE";
        if (cpu_ms < opt.time_ms) cpu_ms = opt.time_ms;  // 표시용
    } else if (memory_kb > opt.memory_mb * 1024) {
        verdict = "MLE";
    } else if (sig != 0 || exit_code != 0) {
        verdict = "RE";
    }

    print_result(verdict, exit_code, sig, cpu_ms, memory_kb);
    return 0;
}
