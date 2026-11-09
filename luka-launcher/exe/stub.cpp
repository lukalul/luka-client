// Luka.exe: one file. It carries only the launcher and a small Java to show it, compressed and appended to this program.
// First start unpacks them to %LOCALAPPDATA%\Luka and starts the launcher; everything else is downloaded by the launcher's Play button.
//
// Built by build-exe.ps1 in two roles:
//   stub.exe --pack <folder> <version> <out.exe>   appends <folder> to a copy of itself
//   Luka.exe                                       the product
//
// Layout of the finished exe: [program][LZMS block][Footer]
// The block unpacks to repeated records: [u32 nameBytes][u64 fileBytes][utf-8 path][file data].

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <shlobj.h>
#include <shellapi.h>
#include <compressapi.h>
#include <gdiplus.h>
#include <cstdint>
#include <string>
#include <vector>
#include "resource.h"

#pragma comment(lib, "Cabinet.lib")
#pragma comment(lib, "Gdiplus.lib")
#pragma comment(lib, "Shell32.lib")
#pragma comment(lib, "Ole32.lib")
#pragma comment(lib, "User32.lib")
#pragma comment(lib, "Gdi32.lib")

namespace {

const char MAGIC[8] = {'L', 'U', 'K', 'A', 'P', 'A', 'K', '1'};

#pragma pack(push, 1)
struct Footer {
    uint64_t compressedBytes;
    uint64_t rawBytes;
    char version[32];
    char magic[8];
};
#pragma pack(pop)

std::wstring selfPath() {
    std::wstring path(32768, L'\0');
    DWORD n = GetModuleFileNameW(nullptr, path.data(), static_cast<DWORD>(path.size()));
    path.resize(n);
    return path;
}

std::wstring widen(const std::string &text) {
    if (text.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), nullptr, 0);
    std::wstring out(n, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), out.data(), n);
    return out;
}

std::string narrow(const std::wstring &text) {
    if (text.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), nullptr, 0, nullptr, nullptr);
    std::string out(n, '\0');
    WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), out.data(), n, nullptr, nullptr);
    return out;
}

bool readAll(const std::wstring &path, std::vector<char> &out) {
    HANDLE f = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
    if (f == INVALID_HANDLE_VALUE) return false;
    LARGE_INTEGER size{};
    GetFileSizeEx(f, &size);
    out.resize(static_cast<size_t>(size.QuadPart));
    size_t done = 0;
    while (done < out.size()) {
        DWORD chunk = 0;
        if (!ReadFile(f, out.data() + done, static_cast<DWORD>(std::min<size_t>(out.size() - done, 1 << 30)), &chunk, nullptr) || !chunk) break;
        done += chunk;
    }
    CloseHandle(f);
    return done == out.size();
}

bool writeAll(const std::wstring &path, const char *data, size_t bytes) {
    HANDLE f = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (f == INVALID_HANDLE_VALUE) return false;
    size_t done = 0;
    while (done < bytes) {
        DWORD chunk = 0;
        if (!WriteFile(f, data + done, static_cast<DWORD>(std::min<size_t>(bytes - done, 1 << 30)), &chunk, nullptr) || !chunk) break;
        done += chunk;
    }
    CloseHandle(f);
    return done == bytes;
}

void makeParents(const std::wstring &path) {
    for (size_t i = 3; i < path.size(); i++)
        if (path[i] == L'\\') CreateDirectoryW(path.substr(0, i).c_str(), nullptr);
}

void deleteTree(const std::wstring &dir) {
    WIN32_FIND_DATAW find;
    HANDLE h = FindFirstFileW((dir + L"\\*").c_str(), &find);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        std::wstring name = find.cFileName;
        if (name == L"." || name == L"..") continue;
        std::wstring full = dir + L"\\" + name;
        if (find.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            deleteTree(full);
            RemoveDirectoryW(full.c_str());
        } else {
            SetFileAttributesW(full.c_str(), FILE_ATTRIBUTE_NORMAL);
            DeleteFileW(full.c_str());
        }
    } while (FindNextFileW(h, &find));
    FindClose(h);
}

void addFiles(const std::wstring &root, const std::wstring &relative, std::vector<char> &out) {
    WIN32_FIND_DATAW find;
    HANDLE h = FindFirstFileW((root + L"\\" + relative + L"*").c_str(), &find);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        std::wstring name = find.cFileName;
        if (name == L"." || name == L"..") continue;
        std::wstring rel = relative + name;
        if (find.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            addFiles(root, rel + L"\\", out);
            continue;
        }
        std::vector<char> data;
        if (!readAll(root + L"\\" + rel, data)) continue;
        std::string utf8 = narrow(rel);
        uint32_t nameBytes = static_cast<uint32_t>(utf8.size());
        uint64_t fileBytes = data.size();
        out.insert(out.end(), reinterpret_cast<char *>(&nameBytes), reinterpret_cast<char *>(&nameBytes) + 4);
        out.insert(out.end(), reinterpret_cast<char *>(&fileBytes), reinterpret_cast<char *>(&fileBytes) + 8);
        out.insert(out.end(), utf8.begin(), utf8.end());
        out.insert(out.end(), data.begin(), data.end());
    } while (FindNextFileW(h, &find));
    FindClose(h);
}

int pack(const std::wstring &folder, const std::string &version, const std::wstring &outExe) {
    std::vector<char> raw;
    addFiles(folder, L"", raw);
    if (raw.empty()) return 2;
    COMPRESSOR_HANDLE c = nullptr;
    if (!CreateCompressor(COMPRESS_ALGORITHM_LZMS, nullptr, &c)) return 3;
    SIZE_T room = 0;
    Compress(c, raw.data(), raw.size(), nullptr, 0, &room);
    std::vector<char> packed(room);
    SIZE_T size = 0;
    if (!Compress(c, raw.data(), raw.size(), packed.data(), packed.size(), &size)) return 4;
    CloseCompressor(c);
    std::vector<char> self;
    if (!readAll(selfPath(), self)) return 5;
    Footer footer{};
    footer.compressedBytes = size;
    footer.rawBytes = raw.size();
    strncpy_s(footer.version, version.c_str(), sizeof footer.version - 1);
    memcpy(footer.magic, MAGIC, 8);
    self.insert(self.end(), packed.begin(), packed.begin() + static_cast<ptrdiff_t>(size));
    self.insert(self.end(), reinterpret_cast<char *>(&footer), reinterpret_cast<char *>(&footer) + sizeof footer);
    return writeAll(outExe, self.data(), self.size()) ? 0 : 6;
}

// ---- splash ----

HWND splash;
volatile LONG progressPermille;
Gdiplus::Image *logoImage;
const int SPLASH_W = 420, SPLASH_H = 240;

Gdiplus::Image *loadLogo(HINSTANCE instance) {
    HRSRC found = FindResourceW(instance, MAKEINTRESOURCEW(IDR_LOGO), L"PNG");
    if (!found) return nullptr;
    HGLOBAL data = LoadResource(instance, found);
    DWORD size = SizeofResource(instance, found);
    HGLOBAL copy = GlobalAlloc(GMEM_MOVEABLE, size);
    memcpy(GlobalLock(copy), LockResource(data), size);
    GlobalUnlock(copy);
    IStream *stream = nullptr;
    CreateStreamOnHGlobal(copy, TRUE, &stream);
    Gdiplus::Image *image = Gdiplus::Image::FromStream(stream);
    stream->Release();
    return image;
}

void paintSplash(HDC dc) {
    using namespace Gdiplus;
    Bitmap back(SPLASH_W, SPLASH_H, PixelFormat32bppPARGB);
    Graphics g(&back);
    g.SetSmoothingMode(SmoothingModeAntiAlias);
    g.SetTextRenderingHint(TextRenderingHintAntiAliasGridFit);
    LinearGradientBrush bg(Point(0, 0), Point(0, SPLASH_H), Color(255, 26, 19, 37), Color(255, 15, 12, 20));
    g.FillRectangle(&bg, 0, 0, SPLASH_W, SPLASH_H);
    if (logoImage) g.DrawImage(logoImage, (SPLASH_W - 96) / 2, 44, 96, 96);
    FontFamily family(L"Segoe UI");
    Font font(&family, 12, FontStyleRegular, UnitPixel);
    SolidBrush text(Color(255, 185, 176, 198));
    StringFormat center;
    center.SetAlignment(StringAlignmentCenter);
    g.DrawString(L"Getting luka ready", -1, &font, RectF(0, 160, SPLASH_W, 20), &center, &text);
    SolidBrush track(Color(255, 43, 35, 56));
    g.FillRectangle(&track, 60, 192, SPLASH_W - 120, 4);
    int fill = (SPLASH_W - 120) * progressPermille / 1000;
    LinearGradientBrush bar(Point(60, 0), Point(SPLASH_W - 60, 0), Color(255, 157, 78, 221), Color(255, 181, 123, 255));
    if (fill > 0) g.FillRectangle(&bar, 60, 192, fill, 4);
    Graphics screen(dc);
    screen.DrawImage(&back, 0, 0);
}

LRESULT CALLBACK splashProc(HWND hwnd, UINT message, WPARAM w, LPARAM l) {
    switch (message) {
        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC dc = BeginPaint(hwnd, &ps);
            paintSplash(dc);
            EndPaint(hwnd, &ps);
            return 0;
        }
        case WM_ERASEBKGND:
            return 1;
        case WM_TIMER:
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
        default:
            return DefWindowProcW(hwnd, message, w, l);
    }
}

void showSplash(HINSTANCE instance) {
    WNDCLASSEXW wc{sizeof wc};
    wc.lpfnWndProc = splashProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hIcon = LoadIconW(instance, MAKEINTRESOURCEW(IDI_LUKA));
    wc.lpszClassName = L"LukaSplash";
    RegisterClassExW(&wc);
    int x = (GetSystemMetrics(SM_CXSCREEN) - SPLASH_W) / 2, y = (GetSystemMetrics(SM_CYSCREEN) - SPLASH_H) / 2;
    splash = CreateWindowExW(WS_EX_APPWINDOW, wc.lpszClassName, L"luka", WS_POPUP, x, y, SPLASH_W, SPLASH_H, nullptr, nullptr, instance, nullptr);
    HRGN round = CreateRoundRectRgn(0, 0, SPLASH_W + 1, SPLASH_H + 1, 16, 16);
    SetWindowRgn(splash, round, FALSE);
    ShowWindow(splash, SW_SHOW);
    UpdateWindow(splash);
    SetTimer(splash, 1, 33, nullptr);
}

// ---- unpack and start ----

struct Job {
    std::wstring home, version;
    std::vector<char> *compressed;
    uint64_t rawBytes;
    bool ok;
    std::wstring error;
};

DWORD WINAPI unpack(void *argument) {
    Job *job = static_cast<Job *>(argument);
    DECOMPRESSOR_HANDLE d = nullptr;
    if (!CreateDecompressor(COMPRESS_ALGORITHM_LZMS, nullptr, &d)) {
        job->error = L"Windows could not start its decompressor.";
        PostMessageW(splash, WM_CLOSE, 0, 0);
        return 0;
    }
    std::vector<char> raw(static_cast<size_t>(job->rawBytes));
    SIZE_T got = 0;
    InterlockedExchange(&progressPermille, 150);
    BOOL fine = Decompress(d, job->compressed->data(), job->compressed->size(), raw.data(), raw.size(), &got);
    CloseDecompressor(d);
    if (!fine || got != job->rawBytes) {
        job->error = L"This download of Luka.exe is damaged. Please download it again.";
        PostMessageW(splash, WM_CLOSE, 0, 0);
        return 0;
    }
    InterlockedExchange(&progressPermille, 600);
    std::wstring staging = job->home + L"\\app.new";
    deleteTree(staging);
    RemoveDirectoryW(staging.c_str());
    makeParents(staging + L"\\");
    size_t at = 0;
    while (at + 12 <= raw.size()) {
        uint32_t nameBytes;
        uint64_t fileBytes;
        memcpy(&nameBytes, raw.data() + at, 4);
        memcpy(&fileBytes, raw.data() + at + 4, 8);
        at += 12;
        std::wstring name = widen(std::string(raw.data() + at, nameBytes));
        at += nameBytes;
        if (name.find(L"..") != std::wstring::npos) { job->error = L"Unsafe file in package."; PostMessageW(splash, WM_CLOSE, 0, 0); return 0; }
        std::wstring full = staging + L"\\" + name;
        makeParents(full);
        if (!writeAll(full, raw.data() + at, static_cast<size_t>(fileBytes))) {
            job->error = L"Could not write to " + full;
            PostMessageW(splash, WM_CLOSE, 0, 0);
            return 0;
        }
        at += static_cast<size_t>(fileBytes);
        InterlockedExchange(&progressPermille, 600 + static_cast<LONG>(380.0 * at / raw.size()));
    }
    std::wstring app = job->home + L"\\app";
    deleteTree(app);
    RemoveDirectoryW(app.c_str());
    if (!MoveFileExW(staging.c_str(), app.c_str(), MOVEFILE_REPLACE_EXISTING)) {
        job->error = L"Could not finish installing. Is luka already open?";
        PostMessageW(splash, WM_CLOSE, 0, 0);
        return 0;
    }
    std::string v = narrow(job->version);
    writeAll(app + L"\\.version", v.data(), v.size());
    InterlockedExchange(&progressPermille, 1000);
    job->ok = true;
    PostMessageW(splash, WM_CLOSE, 0, 0);
    return 0;
}

std::wstring homeFolder() {
    wchar_t custom[MAX_PATH];
    if (GetEnvironmentVariableW(L"LUKA_HOME", custom, MAX_PATH)) return custom;
    PWSTR local = nullptr;
    SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, nullptr, &local);
    std::wstring home = std::wstring(local) + L"\\Luka";
    CoTaskMemFree(local);
    return home;
}

bool startLauncher(const std::wstring &home, const std::wstring &arguments) {
    std::wstring app = home + L"\\app";
    std::wstring command = L"\"" + app + L"\\runtime\\bin\\javaw.exe\" -Xshare:auto -Dsun.java2d.d3d=false -Dluka.home=\"" + home + L"\\luka\" -Dluka.app=\"" + app + L"\"";
    wchar_t manifest[2048];
    if (GetEnvironmentVariableW(L"LUKA_MANIFEST", manifest, 2048)) command += std::wstring(L" -Dluka.manifest=\"") + manifest + L"\"";
    command += L" -cp \"" + app + L"\\luka-boot.jar\" dev.luka.launcher.Boot " + arguments;
    STARTUPINFOW si{sizeof si};
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, FALSE, 0, nullptr, app.c_str(), &si, &pi)) return false;
    AllowSetForegroundWindow(pi.dwProcessId);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return true;
}

void fail(const std::wstring &text) { MessageBoxW(nullptr, text.c_str(), L"luka", MB_ICONERROR | MB_OK); }

}  // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR commandLine, int) {
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argc == 5 && wcscmp(argv[1], L"--pack") == 0) return pack(argv[2], narrow(argv[3]), argv[4]);

    SetProcessDPIAware();
    HANDLE single = CreateMutexW(nullptr, TRUE, L"Local\\LukaLauncherStub");
    if (GetLastError() == ERROR_ALREADY_EXISTS) return 0;

    std::vector<char> self;
    if (!readAll(selfPath(), self) || self.size() < sizeof(Footer)) { fail(L"Could not read Luka.exe."); return 1; }
    Footer footer;
    memcpy(&footer, self.data() + self.size() - sizeof footer, sizeof footer);
    if (memcmp(footer.magic, MAGIC, 8) != 0) { fail(L"This copy of Luka.exe is incomplete. Please download it again."); return 1; }
    std::wstring home = homeFolder();
    std::wstring version = widen(std::string(footer.version, strnlen(footer.version, sizeof footer.version)));

    std::vector<char> stamp;
    bool current = readAll(home + L"\\app\\.version", stamp) && std::string(stamp.begin(), stamp.end()) == narrow(version);
    if (!current) {
        ULONG_PTR token;
        Gdiplus::GdiplusStartupInput input;
        Gdiplus::GdiplusStartup(&token, &input, nullptr);
        logoImage = loadLogo(instance);
        CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        std::vector<char> compressed(self.end() - sizeof(Footer) - static_cast<ptrdiff_t>(footer.compressedBytes), self.end() - sizeof(Footer));
        self.clear();
        self.shrink_to_fit();
        showSplash(instance);
        Job job{home, version, &compressed, footer.rawBytes, false, {}};
        HANDLE worker = CreateThread(nullptr, 0, unpack, &job, 0, nullptr);
        MSG message;
        while (GetMessageW(&message, nullptr, 0, 0) > 0) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        WaitForSingleObject(worker, INFINITE);
        CloseHandle(worker);
        delete logoImage;
        Gdiplus::GdiplusShutdown(token);
        if (!job.ok) { fail(job.error); return 1; }
    }
    if (!startLauncher(home, commandLine ? commandLine : L"")) { fail(L"Could not start the launcher."); return 1; }
    ReleaseMutex(single);
    return 0;
}
