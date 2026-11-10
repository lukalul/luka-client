#pragma once

// Optional render-thread profiler (luka). Set ASTATINE_PROFILE=1 to log, every 2 seconds, where the render thread's
// time per frame goes. When the variable is not set every scope costs one branch.

#include <chrono>
#include <cstdint>

namespace at::prof {
    enum Bucket : int {
        IDLE = 0,       // render thread waiting for the game thread's commands
        EXECUTE = 1,    // decoding and running one command chunk (everything below happens inside it)
        NATIVE = 2,     // NATIVE + (code - 100): one bucket per native command (DRAW, DRAW_MESH, TERRAIN_DRAW, ...)
        SUBMIT = 40,    // vkEndCommandBuffer + vkQueueSubmit of the frame
        QUEUE_PRESENT = 41, // vkQueuePresentKHR / DXGI Present
        FENCE_WAIT = 42,    // waiting for an earlier frame's GPU fence before reusing its resources
        ACQUIRE = 43,       // swapchain image acquire
        GPU_GAP = 45,       // GPU clock between the end of one frame and the start of the next
        SUBMIT_TO_DONE = 46, // CPU time from vkQueueSubmit until the fence wait returned
        GPU = 44,           // GPU time of one frame (timestamp at the start of setup to the end of main)
        ZONE = 24,          // ZONE + kind: GPU time per kind of work (see Zone), from timestamps between kinds
        PART = 48,          // PART + n: pieces of the terrain commands (see Part), each inside its command's bucket
        COUNT = 72
    };

    // What the GPU is doing between two zone timestamps.
    enum Zone : int { ZONE_OTHER = 0, ZONE_TERRAIN = 1, ZONE_MESHES = 2, ZONE_IMMEDIATE = 3, ZONE_COPIES = 4, ZONE_PRESENT = 5,
                      ZONE_TERRAIN_CUTOUT = 6, ZONE_TERRAIN_TRANSLUCENT = 7, ZONE_KINDS = 8 };

    // Pieces of TERRAIN_SETUP and TERRAIN_DRAW, to see which of them a change has to make faster.
    enum Part : int { SETUP_RECENTER = 0, SETUP_CULL = 1, SETUP_VISIBLE = 2, PUBLISH_COPY = 3, PUBLISH_SCHEDULE = 4,
                      DRAW_PREPARE = 5, DRAW_WALK = 6, DRAW_ORDER = 7, DRAW_EMIT = 8, DRAW_BIND = 9, DRAW_ALLOC = 10, DRAW_RECORD = 11,
                      // counts, not times: entries per frame (shown in the place of microseconds)
                      N_LIST = 12, N_VISIBLE = 13, N_ORDERED = 14, N_DRAWN = 15, N_RANGES = 16, N_VERTS = 17, PART_KINDS = 18 };

    extern bool enabled;
    extern std::uint64_t total[COUNT];
    extern std::uint64_t calls[COUNT];

    inline std::uint64_t now() {
        return static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count());
    }

    struct Scope {
        int bucket;
        std::uint64_t start;
        explicit Scope(int b) : bucket(b), start(enabled ? now() : 0) {}
        ~Scope() {
            if (enabled) {
                total[bucket] += now() - start;
                calls[bucket]++;
            }
        }
        Scope(const Scope &) = delete;
        Scope &operator=(const Scope &) = delete;
    };

    // Times consecutive pieces of one function: part(n) closes the running piece and starts piece n.
    struct Lap {
        int bucket = -1;
        std::uint64_t start = 0;
        void part(int next) {
            if (!enabled) return;
            std::uint64_t t = now();
            if (bucket >= 0) {
                total[bucket] += t - start;
                calls[bucket]++;
            }
            bucket = next;
            start = t;
        }
        void stop() { part(-1); }
        Lap() = default;
        ~Lap() { stop(); }
        Lap(const Lap &) = delete;
        Lap &operator=(const Lap &) = delete;
    };

    // Adds a count to a piece that shows numbers of things instead of time.
    inline void count(int bucket, std::uint64_t n) {
        if (!enabled) return;
        total[bucket] += n * 1000;
        calls[bucket]++;
    }
    // Called once per presented frame on the render thread; logs and resets every 2 seconds.
    void frame();
}
