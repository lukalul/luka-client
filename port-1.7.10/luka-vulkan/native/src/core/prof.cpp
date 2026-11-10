#include "core/prof.h"

#include "core/log.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace at::prof {
    static bool readEnabled() {
        const char *value = std::getenv("ASTATINE_PROFILE");
        return value && std::strcmp(value, "1") == 0;
    }

    bool enabled = readEnabled();
    std::uint64_t total[COUNT];
    std::uint64_t calls[COUNT];

    static const char *nativeName(int code) {
        static const char *names[] = {"DRAW", "DRAW_MESH", "TEXTURE_ALLOCATE", "TEXTURE_UPLOAD", "TEXTURE_DETACH",
                                      "COPY_FROM_TARGET", "FRAMEBUFFER_RELEASE", "FRAMEBUFFER_BLIT", "PRESENT",
                                      "SET_VSYNC", "TERRAIN_SETUP", "TERRAIN_MARK_DIRTY", "TERRAIN_ABORT",
                                      "TERRAIN_UPLOAD", "TERRAIN_SORT", "TERRAIN_DRAW", "BEGIN_CAPTURE",
                                      "END_CAPTURE"};
        int index = code - 100;
        return index >= 0 && index < static_cast<int>(sizeof(names) / sizeof(names[0])) ? names[index] : "?";
    }

    static std::uint64_t frames;
    static std::uint64_t windowStart;

    void frame() {
        if (!enabled) return;
        std::uint64_t t = now();
        if (windowStart == 0) windowStart = t;
        frames++;
        if (t - windowStart < 2'000'000'000ull) return;
        double span = static_cast<double>(t - windowStart) / 1e6;
        double n = static_cast<double>(frames);
        std::string line;
        char part[160];
        std::snprintf(part, sizeof(part), "frames=%llu fps=%.0f | us/frame:", static_cast<unsigned long long>(frames),
                      n * 1000.0 / span);
        line += part;
        auto add = [&](const char *name, int bucket) {
            if (calls[bucket] == 0) return;
            std::snprintf(part, sizeof(part), " %s=%.1f(x%.1f)", name, static_cast<double>(total[bucket]) / 1e3 / n,
                          static_cast<double>(calls[bucket]) / n);
            line += part;
        };
        add("idle", IDLE);
        add("execute", EXECUTE);
        add("submit", SUBMIT);
        add("queuePresent", QUEUE_PRESENT);
        add("fenceWait", FENCE_WAIT);
        add("acquire", ACQUIRE);
        add("gpu", GPU);
        add("gpuGap", GPU_GAP);
        add("submitToDone", SUBMIT_TO_DONE);
        add("gpu.other", ZONE + ZONE_OTHER);
        add("gpu.terrain", ZONE + ZONE_TERRAIN);
        add("gpu.cutout", ZONE + ZONE_TERRAIN_CUTOUT);
        add("gpu.translucent", ZONE + ZONE_TERRAIN_TRANSLUCENT);
        add("gpu.meshes", ZONE + ZONE_MESHES);
        add("gpu.immediate", ZONE + ZONE_IMMEDIATE);
        add("gpu.copies", ZONE + ZONE_COPIES);
        add("gpu.present", ZONE + ZONE_PRESENT);
        for (int code = 100; code < 100 + (ZONE - NATIVE); code++) add(nativeName(code), NATIVE + code - 100);
        static const char *parts[PART_KINDS] = {"setup.recenter", "setup.cull", "setup.visible", "publish.copy", "publish.schedule",
                                                "draw.prepare", "draw.walk", "draw.order", "draw.emit", "draw.bind", "draw.alloc",
                                                "draw.record", "n.list", "n.visible", "n.ordered", "n.drawn", "n.ranges", "n.verts"};
        for (int index = 0; index < PART_KINDS; index++) add(parts[index], PART + index);
        log::info("[prof] %s", line.c_str());
        std::memset(total, 0, sizeof(total));
        std::memset(calls, 0, sizeof(calls));
        frames = 0;
        windowStart = t;
    }
}
