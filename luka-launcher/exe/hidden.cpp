// luka-hidden.exe: runs a command on its own, never-shown Windows desktop and waits for it. The launcher uses it for the
// replay editor's renders, so the game window does not appear on the player's screen (it renders the same, since a
// hidden desktop still gets a real OpenGL/Vulkan context). Usage: luka-hidden.exe <command line of the program>.
// Everything after the first token of this program's own command line is passed on unchanged, and the program inherits
// this one's working folder and standard handles (the launcher reads the game's output through them). The program runs
// in a job object that ends it when this process ends, so stopping the helper stops the game. Exit code: the program's.
#include <windows.h>
#include <string>

static const wchar_t* skipFirstToken(const wchar_t* s) {
    bool quoted = false;
    while (*s && (quoted || (*s != L' ' && *s != L'\t'))) {
        if (*s == L'"') quoted = !quoted;
        s++;
    }
    while (*s == L' ' || *s == L'\t') s++;
    return s;
}

static void inheritable(DWORD which) {
    HANDLE h = GetStdHandle(which);
    if (h && h != INVALID_HANDLE_VALUE) SetHandleInformation(h, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT);
}

int wmain() {
    const wchar_t* rest = skipFirstToken(GetCommandLineW());
    if (!*rest) return 2;
    std::wstring command(rest);

    wchar_t name[64];
    wsprintfW(name, L"luka-render-%lu", GetCurrentProcessId());
    HDESK desk = CreateDesktopW(name, nullptr, nullptr, 0, GENERIC_ALL, nullptr);
    if (!desk) return 3;

    HANDLE job = CreateJobObjectW(nullptr, nullptr);
    if (job) {
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits = {};
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
    }

    inheritable(STD_INPUT_HANDLE);
    inheritable(STD_OUTPUT_HANDLE);
    inheritable(STD_ERROR_HANDLE);
    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.lpDesktop = name;
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    PROCESS_INFORMATION pi = {};
    if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, TRUE, CREATE_SUSPENDED, nullptr, nullptr, &si, &pi)) {
        CloseDesktop(desk);
        return 4;
    }
    if (job) AssignProcessToJobObject(job, pi.hProcess);
    ResumeThread(pi.hThread);
    CloseHandle(pi.hThread);
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hProcess);
    if (job) CloseHandle(job);
    CloseDesktop(desk);
    return (int) code;
}
