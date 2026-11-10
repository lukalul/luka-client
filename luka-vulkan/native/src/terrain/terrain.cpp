#include "terrain/terrain.h"

#include "core/error.h"
#include "core/log.h"
#include "core/prof.h"
#include "core/threads.h"
#include "render/blocks.h"
#include "render/renderer.h"
#include "terrain/snapshot.h"
#include "vulkan/device.h"
#include "vulkan/frame.h"
#include "vulkan/memory.h"

#include <algorithm>
#include <cstring>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <new>

#if defined(_M_X64) || defined(__x86_64__)
#include <emmintrin.h>
#define AT_TERRAIN_SSE 1
#endif

namespace at::terrain {
    static AT_INLINE i32 floorDiv(i32 value, i32 divisor) {
        i32 quotient = value / divisor;
        if ((value % divisor != 0) && ((value < 0) != (divisor < 0))) quotient--;
        return quotient;
    }

    static AT_INLINE i32 floorMod(i32 value, i32 divisor) {
        i32 remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }

    static AT_INLINE i32 floorToInt(double value) {
        return static_cast<i32>(std::floor(value));
    }

    static AT_INLINE u64 cloneKey(i32 x, i32 y, i32 z) {
        return (static_cast<u64>(static_cast<u32>(x) & 0x3FFFFFu) << 26) |
               (static_cast<u64>(static_cast<u32>(z) & 0x3FFFFFu) << 4) | static_cast<u64>(y & 15);
    }

    VertexArena::~VertexArena() = default;

    bool VertexArena::allocate(u32 count, u32 &buffer, u32 &first) {
        for (u32 i = 0; i < buffers.size(); i++) {
            if (u64 offset = buffers[i]->ranges.allocate(count, 1); offset != RangeAllocator::FAILED) {
                buffer = i;
                first = static_cast<u32>(offset);
                return true;
            }
        }
        if (count > capacity) return false;
        Context &context = renderer.context();
        auto block = std::make_unique<Block>(capacity);
        block->buffer = std::make_unique<GpuBuffer>(*context.device, *context.allocator, capacity * elementSize,
                                                    usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                                                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0, true);
        u64 offset = block->ranges.allocate(count, 1);
        buffers.push_back(std::move(block));
        buffer = static_cast<u32>(buffers.size() - 1);
        first = static_cast<u32>(offset);
        return true;
    }

    void VertexArena::release(u32 buffer, u32 first, u32 count) const {
        buffers[buffer]->ranges.release(first, count);
    }

    VkBuffer VertexArena::handle(u32 buffer) const {
        return buffers[buffer]->buffer->handle;
    }

    u64 VertexArena::usedVertices() const {
        u64 used = 0;
        for (const auto &block: buffers) used += block->ranges.used;
        return used;
    }

    Terrain::Terrain(Renderer &renderer)
        : renderer(renderer), arena(std::make_unique<VertexArena>(renderer)),
          staging(std::make_unique<StagingPool>(*renderer.context().device)),
          indexArena(std::make_unique<VertexArena>(renderer, sizeof(u32), 8ull << 20, VK_BUFFER_USAGE_INDEX_BUFFER_BIT)) {
        sortThread = std::thread([this] { sortWork(); });
        lowerPriority(sortThread);
    }

    void Terrain::sortWork() {
        thread_local std::vector<u32> order;
        thread_local std::vector<float> distances;
        while (true) {
            SortJob job;
            {
                std::unique_lock guard(sortLock);
                sortWake.wait(guard, [this] { return sortStopping || !sortJobs.empty(); });
                if (sortStopping) return;
                job = std::move(sortJobs.front());
                sortJobs.pop_front();
            }
            const std::vector<float> &centroids = *job.centroids;
            std::size_t quads = centroids.size() / 3;
            order.resize(quads);
            distances.resize(quads);
            for (std::size_t i = 0; i < quads; i++) {
                float dx = centroids[i * 3] - job.cameraX;
                float dy = centroids[i * 3 + 1] - job.cameraY;
                float dz = centroids[i * 3 + 2] - job.cameraZ;
                distances[i] = dx * dx + dy * dy + dz * dz;
                order[i] = static_cast<u32>(i);
            }
            std::stable_sort(order.begin(), order.end(), [](u32 a, u32 b) { return distances[a] > distances[b]; });
            SortResult result{job.x, job.y, job.z, job.token, static_cast<u32>(quads * 6), {}};
            StagingSpan span = staging->allocate(u64(quads) * 6 * sizeof(u32));
            if (span.valid()) {
                auto *indices = reinterpret_cast<u32 *>(span.address);
                for (std::size_t i = 0; i < quads; i++) {
                    u32 base = order[i] * 4;
                    u32 *out = indices + i * 6;
                    out[0] = base;
                    out[1] = base + 1;
                    out[2] = base + 2;
                    out[3] = base;
                    out[4] = base + 2;
                    out[5] = base + 3;
                }
                result.indices = StagedData(staging.get(), span);
            }
            std::lock_guard guard(sortLock);
            sortResults.push_back(std::move(result));
        }
    }

    Terrain::~Terrain() {
        {
            std::lock_guard guard(sortLock);
            sortStopping = true;
            sortJobs.clear();
        }
        sortWake.notify_all();
        if (sortThread.joinable()) sortThread.join();
        sortResults.clear();
        std::lock_guard guard(snapshotsLock);
        for (Snapshot *snapshot: snapshots) {
            snapshot->~Snapshot();
            ::operator delete(snapshot, static_cast<std::align_val_t>(alignof(Snapshot)));
        }
        snapshots.clear();
    }

    void Terrain::setTables(std::shared_ptr<const Tables> next) {
        std::lock_guard guard(tablesLock);
        tables = std::move(next);
    }

    void Terrain::configure(i32 distance) {
        clear();
        u32 units = 1;
        {
            std::lock_guard guard(tablesLock);
            if (tables) units = tables->vertexUnits;
        }
        if (units != arenaUnits) {
            std::shared_ptr<VertexArena> retired(arena.release());
            renderer.defer([retired] {});
            arena = std::make_unique<VertexArena>(renderer, static_cast<u64>(units) * sizeof(Vertex));
            arenaUnits = units;
        }
        viewDistance = std::max(distance, 2);
        size = viewDistance * 2 + 1;
        sections.clear();
        sections.resize(static_cast<std::size_t>(size) * size * 16);
        hot.assign(sections.size(), Hot{});
        buildSize.store(size, std::memory_order_relaxed);
        graphNodes.assign(sections.size(), GraphNode{});
        centerX = INT32_MIN;
        centerZ = INT32_MIN;
    }

    void Terrain::clear() {
        for (u32 layer = 0; layer < LAYERS; layer++) {
            ordered[layer].clear();
            orderedSorted[layer] = 0;
            orderedDead[layer] = 0;
        }
        for (Section &section: sections) {
            releaseGeometry(section);
            section = Section{};
        }
        hot.assign(sections.size(), Hot{});
        visibleSlots.clear();
        scheduleStale = true;
        culler.cancel();
        if (cullResult) culler.recycle(std::move(cullResult));
        activeList = nullptr;
        graphNodes.assign(sections.size(), GraphNode{});
        graphVersion++;
        listDirty = true;
        visible.clear();
        for (auto &list: layerVisible) list.clear();
        visibleCoordinates.clear();
        centerX = INT32_MIN;
        centerZ = INT32_MIN;
        clones.clear();
        cloneOrder.clear();
        {
            std::lock_guard publishGuard(publishLock);
            publishedVisible.clear();
            publishedSchedule.clear();
            gameVisible.clear();
        }
        std::lock_guard guard(resultsLock);
        results.clear();
    }

    Section *Terrain::slot(i32 x, i32 y, i32 z) {
        if (size == 0 || y < 0 || y > 15) return nullptr;
        std::size_t index = (static_cast<std::size_t>(floorMod(z, size)) * size + floorMod(x, size)) * 16 + y;
        return &sections[index];
    }

    Section *Terrain::at(i32 x, i32 y, i32 z) {
        Section *section = slot(x, y, z);
        if (!section || !section->valid || section->x != x || section->z != z) return nullptr;
        return section;
    }

    void Terrain::refresh(const Section &section) {
        Hot &entry = hot[slotIndex(section)];
        u8 pending = section.valid && section.dirty && !section.building ? 1 : 0;
        if (pending && !entry.pending) scheduleStale = true;
        entry.pending = pending;
    }

    void Terrain::forget(u32 layerIndex, u32 buffer, u32 first, u32 slot) {
        std::vector<Ordered> &list = ordered[layerIndex];
        std::size_t sorted = orderedSorted[layerIndex];
        auto middle = list.begin() + static_cast<std::ptrdiff_t>(sorted);
        auto found = std::lower_bound(list.begin(), middle, first, [buffer](const Ordered &entry, u32 wanted) {
            return entry.buffer != buffer ? entry.buffer < buffer : entry.first < wanted;
        });
        if (found != middle && found->buffer == buffer && found->first == first && found->slot == slot) {
            found->slot = DEAD;
            orderedDead[layerIndex]++;
            return;
        }
        for (std::size_t index = list.size(); index-- > sorted;) {
            if (list[index].slot != slot || list[index].buffer != buffer || list[index].first != first) continue;
            list[index] = list.back();
            list.pop_back();
            return;
        }
    }

    void Terrain::settle(u32 layerIndex) {
        std::vector<Ordered> &list = ordered[layerIndex];
        std::size_t sorted = orderedSorted[layerIndex];
        auto before = [](const Ordered &a, const Ordered &b) { return a.buffer != b.buffer ? a.buffer < b.buffer : a.first < b.first; };
        std::sort(list.begin() + static_cast<std::ptrdiff_t>(sorted), list.end(), before);
        orderedScratch.clear();
        orderedScratch.reserve(list.size());
        std::size_t left = 0, right = sorted;
        while (left < sorted || right < list.size()) {
            if (left < sorted && list[left].slot == DEAD) {
                left++;
                continue;
            }
            if (right == list.size() || (left < sorted && before(list[left], list[right]))) orderedScratch.push_back(list[left++]);
            else orderedScratch.push_back(list[right++]);
        }
        list.swap(orderedScratch);
        orderedSorted[layerIndex] = list.size();
        orderedDead[layerIndex] = 0;
    }

    void Terrain::releaseGeometry(Section &section) {
        std::size_t slot = slotIndex(section);
        for (u32 index = 0; index < LAYERS; index++) {
            SectionLayer &layer = section.layers[index];
            if (layer.vertices > 0 && index != TRANSLUCENT) forget(index, layer.buffer, layer.first, static_cast<u32>(slot));
            if (layer.vertices > 0) {
                VertexArena *target = arena.get();
                u32 buffer = layer.buffer, first = layer.first, count = layer.vertices;
                renderer.defer([target, buffer, first, count] { target->release(buffer, first, count); });
            }
            if (layer.indexCount > 0) {
                VertexArena *target = indexArena.get();
                u32 buffer = layer.indexBuffer, first = layer.indexFirst, count = layer.indexCount;
                renderer.defer([target, buffer, first, count] { target->release(buffer, first, count); });
            }
            layer = SectionLayer{};
        }
        hot[slot].layers = 0;
    }

    void Terrain::reset(Section &section, i32 x, i32 y, i32 z) {
        GraphNode &node = graphNodes[slotIndex(section)];
        node = GraphNode{.visibility = 0, .x = x, .z = z, .y = static_cast<u8>(y), .state = graph::VALID};
        graphVersion++;
        listDirty = true;
        releaseGeometry(section);
        u32 version = section.version + 1;
        section = Section{};
        section.x = x;
        section.y = y;
        section.z = z;
        section.valid = true;
        section.version = version;
        section.dirty = true;
        hot[slotIndex(section)] = Hot{.x = x, .z = z, .drawnFrame = 0, .y = static_cast<u8>(y), .valid = 1, .layers = 0, .pending = 1};
        scheduleStale = true;
    }

    void Terrain::recenter(double x, double z) {
        i32 baseX = floorToInt(x) - 8;
        i32 baseZ = floorToInt(z) - 8;
        if (baseX == centerX && baseZ == centerZ) return;
        centerX = baseX;
        centerZ = baseZ;
        i32 span = size * 16;
        auto coordinate = [span](i32 base, i32 slotIndex) {
            i32 i = slotIndex * 16;
            i32 j = i - base + span / 2;
            if (j < 0) j -= span - 1;
            return i - j / span * span;
        };
        for (i32 sx = 0; sx < size; sx++) {
            i32 blockX = coordinate(baseX, sx);
            for (i32 sz = 0; sz < size; sz++) {
                i32 blockZ = coordinate(baseZ, sz);
                i32 sectionX = blockX >> 4, sectionZ = blockZ >> 4;
                for (i32 y = 0; y < 16; y++) {
                    if (Section *section = slot(sectionX, y, sectionZ); !section->valid || section->x != sectionX || section->z != sectionZ)
                        reset(
                            *section, sectionX, y, sectionZ);
                }
            }
        }
    }

    void Terrain::invalidateClones(i32 minX, i32 minY, i32 minZ, i32 maxX, i32 maxY, i32 maxZ) {
        i32 fromX = floorDiv(minX, 16), toX = floorDiv(maxX, 16);
        i32 fromY = std::max(floorDiv(minY, 16), 0), toY = std::min(floorDiv(maxY, 16), 15);
        i32 fromZ = floorDiv(minZ, 16), toZ = floorDiv(maxZ, 16);
        for (i32 x = fromX; x <= toX; x++) {
            for (i32 z = fromZ; z <= toZ; z++) {
                for (i32 y = fromY; y <= toY; y++) clones.erase(cloneKey(x, y, z));
            }
        }
    }

    void Terrain::publish(u32 scheduleCap) {
        std::lock_guard guard(publishLock);
        prof::Lap lap;
        lap.part(prof::PART + prof::PUBLISH_COPY);
        publishedVisible = visibleCoordinates;
        lap.part(prof::PART + prof::PUBLISH_SCHEDULE);
        auto queued = static_cast<u32>(publishedSchedule.size() / 5);
        if (queued < scheduleCap && scheduleStale) {
            u32 wanted = scheduleCap - queued;
            scheduleScratch.resize(static_cast<std::size_t>(wanted) * 5);
            u32 count = schedule(scheduleScratch.data(), wanted);
            publishedSchedule.insert(publishedSchedule.end(), scheduleScratch.begin(),
                                     scheduleScratch.begin() + static_cast<std::ptrdiff_t>(count) * 5);
            if (count < wanted) scheduleStale = false; // every candidate was looked at; nothing more until one changes
        }
        if (debugWanted.exchange(false, std::memory_order_relaxed)) publishedDebug = debug();
    }

    u32 Terrain::takeVisible() {
        std::lock_guard guard(publishLock);
        gameVisible = publishedVisible;
        return static_cast<u32>(gameVisible.size() / 3);
    }

    u32 Terrain::takeSchedule(i32 *out, u32 max) {
        std::lock_guard guard(publishLock);
        u32 count = std::min(max, static_cast<u32>(publishedSchedule.size() / 5));
        if (count == 0) return 0;
        std::memcpy(out, publishedSchedule.data(), static_cast<std::size_t>(count) * 5 * sizeof(i32));
        publishedSchedule.erase(publishedSchedule.begin(), publishedSchedule.begin() + static_cast<std::ptrdiff_t>(count) * 5);
        return count;
    }

    std::string Terrain::debugText() {
        debugWanted.store(true, std::memory_order_relaxed);
        std::lock_guard guard(publishLock);
        return publishedDebug;
    }

    void Terrain::markDirty(i32 minX, i32 minY, i32 minZ, i32 maxX, i32 maxY, i32 maxZ, bool important,
                            u64 sequence) {
        i32 fromX = floorDiv(minX, 16), toX = floorDiv(maxX, 16);
        i32 fromY = std::max(floorDiv(minY, 16), 0), toY = std::min(floorDiv(maxY, 16), 15);
        i32 fromZ = floorDiv(minZ, 16), toZ = floorDiv(maxZ, 16);
        for (i32 x = fromX; x <= toX; x++) {
            for (i32 z = fromZ; z <= toZ; z++) {
                for (i32 y = fromY; y <= toY; y++) {
                    Section *section = at(x, y, z);
                    if (!section) continue;
                    section->version++;
                    section->dirty = true;
                    section->important |= important;
                    section->dirtySequence = std::max(section->dirtySequence, sequence);
                    refresh(*section);
                }
            }
        }
    }

    void Terrain::computeFrustum() {
        RenderState &state = renderer.state;
        float clip[16];
        std::memcpy(clip, state.projection.top(), 64);
        mat4::multiply(clip, state.modelView.top());
        auto row = [&](int index, int column) { return clip[column * 4 + index]; };
        for (int plane = 0; plane < 6; plane++) {
            int axis = plane / 2;
            float sign = (plane & 1) ? -1.0f : 1.0f;
            float length = 0.0f;
            for (int column = 0; column < 4; column++) {
                planes[plane][column] = row(3, column) + sign * row(axis, column);
            }
            length = std::sqrt(
                planes[plane][0] * planes[plane][0] + planes[plane][1] * planes[plane][1] + planes[plane][2] * planes[
                    plane][2]);
            if (length > 0.0f) {
                for (float &value: planes[plane]) value /= length;
            }
        }
    }

    bool Terrain::frustumVisible(const Section &section) const {
        return frustumVisible(section, cameraX, cameraY, cameraZ);
    }

    bool Terrain::frustumVisible(const Section &section, double originX, double originY, double originZ) const {
        constexpr float OUTSIDE_WORLD = 1.0e7f;
        auto minX = static_cast<float>(section.x * 16 - originX);
        auto minY = static_cast<float>(section.y * 16 - originY);
        auto minZ = static_cast<float>(section.z * 16 - originZ);
        float maxX = minX + 16.0f, maxY = minY + 16.0f, maxZ = minZ + 16.0f;
        if (section.y == 0) minY = -OUTSIDE_WORLD;
        if (section.y == 15) maxY = OUTSIDE_WORLD;
        for (const auto &plane: planes) {
            float x = plane[0] > 0 ? maxX : minX;
            float y = plane[1] > 0 ? maxY : minY;
            if (float z = plane[2] > 0 ? maxZ : minZ; plane[0] * x + plane[1] * y + plane[2] * z + plane[3] < 0.0f) return false;
        }
        return true;
    }

    std::size_t Terrain::slotIndex(const Section &section) const {
        return static_cast<std::size_t>(&section - sections.data());
    }

    void Terrain::submitCull(i32 originX, i32 originY, i32 originZ, bool smart) {
        auto input = std::make_unique<CullInput>();
        input->nodes = graphNodes;
        input->size = size;
        input->viewDistance = viewDistance;
        input->originX = originX;
        input->originY = originY;
        input->originZ = originZ;
        input->smartCull = smart;
        input->graphVersion = graphVersion;
        culler.submit(std::move(input));
        submittedX = originX;
        submittedY = originY;
        submittedZ = originZ;
        submittedSmart = smart;
        submittedVersion = graphVersion;
    }

    bool Terrain::acceptCull(std::unique_ptr<CullResult> result) {
        if (!result) return false;
        if (cullResult) culler.recycle(std::move(cullResult));
        cullResult = std::move(result);
        activeList = nullptr;
        listDirty = true;
        return true;
    }

    const std::vector<u32> *Terrain::candidates(i32 originX, i32 originY, i32 originZ) const {
        if (!cullResult) return nullptr;
        i32 dx = std::abs(originX - cullResult->originX);
        i32 dy = std::abs(originY - cullResult->originY);
        i32 dz = std::abs(originZ - cullResult->originZ);
        if (dx == 0 && dy == 0 && dz == 0) return &cullResult->regular;
        if (dx <= 1 && dy <= 1 && dz <= 1) return &cullResult->wide;
        return nullptr;
    }

    void Terrain::setup(const SetupParameters &parameters) {
        cameraX = parameters.x;
        cameraY = parameters.y;
        cameraZ = parameters.z;
        if (size == 0) return;
        prof::Lap lap;
        lap.part(prof::PART + prof::SETUP_RECENTER);
        recenter(cameraX, cameraZ);
        lap.part(prof::PART + prof::SETUP_CULL);
        computeFrustum();

        float inverse[16];
        eyeX = cameraX;
        eyeY = cameraY + parameters.eyeHeight;
        eyeZ = cameraZ;
        if (mat4::invert(renderer.state.modelView.top(), inverse)) {
            eyeX = cameraX + inverse[12];
            eyeY = cameraY + inverse[13];
            eyeZ = cameraZ + inverse[14];
        }
        {
            std::lock_guard guard(cameraLock);
            buildCamera[0] = static_cast<float>(eyeX);
            buildCamera[1] = static_cast<float>(eyeY);
            buildCamera[2] = static_cast<float>(eyeZ);
        }

        i32 originX = floorDiv(floorToInt(cameraX), 16);
        i32 originY = floorDiv(floorToInt(cameraY + parameters.eyeHeight), 16);
        i32 originZ = floorDiv(floorToInt(cameraZ), 16);
        bool smart = parameters.smartCull && !(parameters.spectator && parameters.cameraInOpaque);

        acceptCull(culler.take(false));
        bool stale = originX != submittedX || originY != submittedY || originZ != submittedZ ||
                     smart != submittedSmart || graphVersion != submittedVersion;
        if (stale && !culler.busy()) submitCull(originX, originY, originZ, smart);

        const std::vector<u32> *list = candidates(originX, originY, originZ);
        if (!list) {
            if (!culler.busy()) submitCull(originX, originY, originZ, smart);
            while (!list && acceptCull(culler.take(true))) {
                list = candidates(originX, originY, originZ);
                if (!list && !culler.busy()) submitCull(originX, originY, originZ, smart);
            }
            if (!list) {
                scheduleStale = true;
                return;
            }
        }

        float clip[16];
        std::memcpy(clip, renderer.state.projection.top(), 64);
        mat4::multiply(clip, renderer.state.modelView.top());
        bool same = !listDirty && list == activeList && std::memcmp(clip, lastClip, sizeof(clip)) == 0 &&
                    parameters.x == lastParameters.x && parameters.y == lastParameters.y &&
                    parameters.z == lastParameters.z && parameters.eyeHeight == lastParameters.eyeHeight &&
                    parameters.frustumCull == lastParameters.frustumCull;
        if (same) return;
        std::memcpy(lastClip, clip, sizeof(clip));
        lastParameters = parameters;
        listDirty = false;
        activeList = list;

        lap.part(prof::PART + prof::SETUP_VISIBLE);
        frame++;
        if (frame == 0) frame = 1;
        scheduleStale = true;
        visible.clear();
        visibleSlots.clear();
        visibleCoordinates.clear();
        for (auto &layerList: layerVisible) layerList.clear();

        // The same test as frustumVisible, for all planes at once and reading only the Hot entries. Per plane the
        // box corner furthest along the normal is tested; the +16 of that corner on x and z is folded into `bias`.
        constexpr float OUTSIDE_WORLD = 1.0e7f;
        constexpr int WIDE = 8;
        float planeX[WIDE] = {}, planeUp[WIDE] = {}, planeDown[WIDE] = {}, planeZ[WIDE] = {}, bias[WIDE];
        for (int plane = 0; plane < WIDE; plane++) bias[plane] = 1.0f;
        for (int plane = 0; plane < 6; plane++) {
            planeX[plane] = planes[plane][0];
            planeUp[plane] = std::max(planes[plane][1], 0.0f);
            planeDown[plane] = std::min(planes[plane][1], 0.0f);
            planeZ[plane] = planes[plane][2];
            bias[plane] = planes[plane][3] + 16.0f * (std::max(planes[plane][0], 0.0f) + std::max(planes[plane][2], 0.0f));
        }
        const bool cull = parameters.frustumCull;
        Section *base = sections.data();
        prof::count(prof::PART + prof::N_LIST, list->size());
#ifdef AT_TERRAIN_SSE
        const __m128 planeX0 = _mm_loadu_ps(planeX), planeX1 = _mm_loadu_ps(planeX + 4);
        const __m128 planeUp0 = _mm_loadu_ps(planeUp), planeUp1 = _mm_loadu_ps(planeUp + 4);
        const __m128 planeDown0 = _mm_loadu_ps(planeDown), planeDown1 = _mm_loadu_ps(planeDown + 4);
        const __m128 planeZ0 = _mm_loadu_ps(planeZ), planeZ1 = _mm_loadu_ps(planeZ + 4);
        const __m128 bias0 = _mm_loadu_ps(bias), bias1 = _mm_loadu_ps(bias + 4);
        const __m128 zero = _mm_setzero_ps();
#endif
        for (u32 slot: *list) {
            Hot &entry = hot[slot];
            if (!entry.valid) continue;
            if (cull) {
                auto minX = static_cast<float>(entry.x * 16 - cameraX);
                auto minY = static_cast<float>(entry.y * 16 - cameraY);
                auto minZ = static_cast<float>(entry.z * 16 - cameraZ);
                float low = entry.y == 0 ? -OUTSIDE_WORLD : minY;
                float high = entry.y == 15 ? OUTSIDE_WORLD : minY + 16.0f;
#ifdef AT_TERRAIN_SSE
                // Same sums in the same order as the plain loop below, four planes at a time.
                const __m128 x = _mm_set1_ps(minX), up = _mm_set1_ps(high), down = _mm_set1_ps(low), z = _mm_set1_ps(minZ);
                __m128 d0 = _mm_add_ps(_mm_mul_ps(planeX0, x), _mm_mul_ps(planeUp0, up));
                __m128 d1 = _mm_add_ps(_mm_mul_ps(planeX1, x), _mm_mul_ps(planeUp1, up));
                d0 = _mm_add_ps(d0, _mm_mul_ps(planeDown0, down));
                d1 = _mm_add_ps(d1, _mm_mul_ps(planeDown1, down));
                d0 = _mm_add_ps(_mm_add_ps(d0, _mm_mul_ps(planeZ0, z)), bias0);
                d1 = _mm_add_ps(_mm_add_ps(d1, _mm_mul_ps(planeZ1, z)), bias1);
                if (_mm_movemask_ps(_mm_or_ps(_mm_cmplt_ps(d0, zero), _mm_cmplt_ps(d1, zero)))) continue;
#else
                float distance[WIDE];
                for (int plane = 0; plane < WIDE; plane++) {
                    distance[plane] = planeX[plane] * minX + planeUp[plane] * high + planeDown[plane] * low + planeZ[plane] * minZ +
                                      bias[plane];
                }
                int outside = 0;
                for (int plane = 0; plane < WIDE; plane++) outside |= distance[plane] < 0.0f ? 1 : 0;
                if (outside) continue;
#endif
            }
            entry.drawnFrame = frame;
            Section *section = base + slot;
            visibleSlots.push_back(slot);
            visibleCoordinates.push_back(entry.x);
            visibleCoordinates.push_back(entry.y);
            visibleCoordinates.push_back(entry.z);
            for (u32 layer = 0; layer < LAYERS; layer++) {
                if (entry.layers >> layer & 1) layerVisible[layer].push_back(section);
            }
        }
        prof::count(prof::PART + prof::N_VISIBLE, visibleSlots.size());
    }

    u32 Terrain::schedule(i32 *out, u32 max) {
        u32 count = 0;
        i32 cameraSectionX = floorDiv(floorToInt(cameraX), 16);
        i32 cameraSectionY = floorDiv(floorToInt(cameraY), 16);
        i32 cameraSectionZ = floorDiv(floorToInt(cameraZ), 16);
        auto take = [&](std::size_t index) {
            Hot &state = hot[index];
            if (count >= max || !state.pending) return;
            Section &section = sections[index];
            bool near = std::abs(section.x - cameraSectionX) <= 1 && std::abs(section.y - cameraSectionY) <= 1 &&
                        std::abs(section.z - cameraSectionZ) <= 1;
            bool urgent = near && section.important && section.compiled;
            i32 *entry = out + count * 5;
            entry[0] = section.x;
            entry[1] = section.y;
            entry[2] = section.z;
            entry[3] = static_cast<i32>(section.version);
            entry[4] = urgent ? 1 : 0;
            section.building = true;
            section.buildingVersion = section.version;
            section.important = false;
            state.pending = 0;
            count++;
        };
        for (u32 slot: visibleSlots) {
            if (count >= max) break;
            take(slot);
        }
        if (activeList) {
            for (u32 slot: *activeList) {
                if (count >= max) break;
                take(slot);
            }
        }
        for (i32 radius = 1; radius <= 2 && count < max; radius++) {
            for (i32 dy = -radius; dy <= radius; dy++) {
                for (i32 dz = -radius; dz <= radius; dz++) {
                    for (i32 dx = -radius; dx <= radius; dx++) {
                        i32 x = cameraSectionX + dx, y = cameraSectionY + dy, z = cameraSectionZ + dz;
                        if (y < 0 || y > 15) continue;
                        std::size_t index = (static_cast<std::size_t>(floorMod(z, size)) * size + floorMod(x, size)) * 16 + y;
                        if (const Hot &near = hot[index]; near.valid && near.x == x && near.z == z) take(index);
                    }
                }
            }
        }
        return count;
    }

    void Terrain::abort(i32 x, i32 y, i32 z, u32 version) {
        if (Section *section = at(x, y, z); section && section->building && section->buildingVersion == version) {
            section->building = false;
            refresh(*section);
        }
    }

    Snapshot *Terrain::acquireSnapshot() {
        {
            std::lock_guard guard(snapshotsLock);
            if (!snapshots.empty()) {
                Snapshot *snapshot = snapshots.back();
                snapshots.pop_back();
                return snapshot;
            }
        }
        void *memory = ::operator new(sizeof(Snapshot), static_cast<std::align_val_t>(alignof(Snapshot)));
        return new(memory) Snapshot();
    }

    u32 Terrain::attachClones(Snapshot *snapshot, i32 x, i32 y, i32 z, u32 present) {
        u32 attached = 0;
        for (i32 dy = -1; dy <= 1; dy++) {
            for (i32 dz = -1; dz <= 1; dz++) {
                for (i32 dx = -1; dx <= 1; dx++) {
                    u32 slot = static_cast<u32>((dy + 1) * 9 + (dz + 1) * 3 + dx + 1);
                    if (!((present >> slot) & 1)) continue;
                    auto found = clones.find(cloneKey(x + dx, y + dy, z + dz));
                    if (found == clones.end()) continue;
                    snapshot->clones[slot] = found->second.data;
                    attached |= 1u << slot;
                }
            }
        }
        return attached;
    }

    void Terrain::commitClones(Snapshot *snapshot, u32 copied, u32 sky) {
        for (u32 slot = 0; slot < Snapshot::SECTIONS; slot++) {
            if (!((copied >> slot) & 1)) continue;
            i32 dx = static_cast<i32>(slot % 3) - 1, dz = static_cast<i32>((slot / 3) % 3) - 1,
                    dy = static_cast<i32>(slot / 9) - 1;
            i32 sectionY = snapshot->sectionY + dy;
            if (sectionY < 0 || sectionY > 15) continue;
            auto clone = std::make_shared<SectionClone>();
            std::copy_n(snapshot->states[slot], 4096, clone->states);
            std::copy_n(snapshot->blockLight[slot], 2048, clone->blockLight);
            clone->sky = (sky >> slot) & 1;
            if (clone->sky) std::copy_n(snapshot->skyLight[slot], 2048, clone->skyLight);
            u64 key = cloneKey(snapshot->sectionX + dx, sectionY, snapshot->sectionZ + dz);
            u64 serial = ++cloneSerial;
            clones[key] = CloneEntry{std::move(clone), serial};
            cloneOrder.emplace_back(key, serial);
        }
        while (clones.size() > MAX_CLONES && !cloneOrder.empty()) {
            auto [key, serial] = cloneOrder.front();
            cloneOrder.pop_front();
            auto found = clones.find(key);
            if (found != clones.end() && found->second.serial == serial) clones.erase(found);
        }
        if (cloneOrder.size() > MAX_CLONES * 4) {
            std::deque<std::pair<u64, u64> > live;
            for (const auto &entry: cloneOrder) {
                auto found = clones.find(entry.first);
                if (found != clones.end() && found->second.serial == entry.second) live.push_back(entry);
            }
            cloneOrder.swap(live);
        }
    }

    void Terrain::releaseSnapshot(Snapshot *snapshot) {
        snapshot->dropClones();
        std::lock_guard guard(snapshotsLock);
        snapshots.push_back(snapshot);
    }

    void Terrain::build(Snapshot *snapshot) {
        std::shared_ptr<const Tables> current;
        {
            std::lock_guard guard(tablesLock);
            current = tables;
        }
        float camera[3];
        {
            std::lock_guard guard(cameraLock);
            std::memcpy(camera, buildCamera, sizeof(camera));
        }
        std::unique_ptr<MeshResult> result;
        if (current) {
            result = mesh(*current, *snapshot, camera[0], camera[1], camera[2]);
            u32 units = current->vertexUnits;
            result->units = units;
            i32 slotSize = buildSize.load(std::memory_order_relaxed);
            if (slotSize > 0 && snapshot->sectionY >= 0 && snapshot->sectionY < 16) {
                u32 slot = static_cast<u32>((floorMod(snapshot->sectionZ, slotSize) * slotSize +
                                             floorMod(snapshot->sectionX, slotSize)) * 16 + snapshot->sectionY);
                for (LayerMesh &layer: result->layers) {
                    for (std::size_t unit = 0; unit < layer.vertices.size(); unit += units) setSlot(layer.vertices[unit], slot);
                }
            }
            for (u32 index = 0; index < LAYERS; index++) {
                LayerMesh &layer = result->layers[index];
                layer.count = static_cast<u32>(layer.vertices.size() / units);
                if (layer.count == 0) continue;
                u64 bytes = layer.vertices.size() * sizeof(Vertex);
                StagingSpan span = staging->allocate(bytes);
                if (!span.valid()) continue;
                std::memcpy(span.address, layer.vertices.data(), bytes);
                layer.staged = StagedData(staging.get(), span);
                layer.vertices.clear();
                layer.vertices.shrink_to_fit();
                if (index == TRANSLUCENT) {
                    u32 quads = layer.count / 4;
                    layer.indexCount = quads * 6;
                    StagingSpan indexSpan = staging->allocate(u64(layer.indexCount) * sizeof(u32));
                    if (indexSpan.valid()) {
                        auto *indices = reinterpret_cast<u32 *>(indexSpan.address);
                        for (u32 quad = 0; quad < quads; quad++) {
                            u32 base = quad * 4;
                            u32 *out = indices + quad * 6;
                            out[0] = base;
                            out[1] = base + 1;
                            out[2] = base + 2;
                            out[3] = base;
                            out[4] = base + 2;
                            out[5] = base + 3;
                        }
                        layer.indices = StagedData(staging.get(), indexSpan);
                    }
                }
            }
        } else {
            result = std::make_unique<MeshResult>();
            result->x = snapshot->sectionX;
            result->y = snapshot->sectionY;
            result->z = snapshot->sectionZ;
            result->version = snapshot->version;
        }
        result->urgent = (snapshot->flags & Snapshot::URGENT) != 0;
        result->sequence = snapshot->sequence;
        releaseSnapshot(snapshot);
        std::lock_guard guard(resultsLock);
        results.push_back(std::move(result));
    }

    void Terrain::apply(MeshResult &result) {
        Section *section = at(result.x, result.y, result.z);
        if (!section) return;
        std::size_t slot = slotIndex(*section);
        if (!result.urgent && section->building && section->buildingVersion == result.version) section->building = false;
        refresh(*section);
        if (!result.urgent && result.version < section->builtVersion) return;
        if (result.units != arenaUnits) return;
        bool hadLayer[LAYERS];
        for (u32 index = 0; index < LAYERS; index++) hadLayer[index] = section->layers[index].vertices > 0;
        releaseGeometry(*section);
        VkCommandBuffer transfer = renderer.transferCommands();
        for (u32 index = 0; index < LAYERS; index++) {
            LayerMesh &mesh = result.layers[index];
            u32 count = mesh.count ? mesh.count : static_cast<u32>(mesh.vertices.size() / arenaUnits);
            if (count == 0) continue;
            SectionLayer &layer = section->layers[index];
            u32 buffer, first;
            if (!arena->allocate(count, buffer, first)) {
                log::warn("Section %d %d %d does not fit in a terrain buffer", result.x, result.y, result.z);
                continue;
            }
            u64 bytes = static_cast<u64>(count) * arenaUnits * sizeof(Vertex);
            VkBuffer source;
            u64 offset;
            if (mesh.staged.valid()) {
                StagingSpan span = mesh.staged.release();
                source = staging->handle(span.block);
                offset = span.offset;
                StagingPool *pool = staging.get();
                renderer.defer([pool, span] { pool->release(span); });
            } else {
                u8 *address = renderer.stage(bytes, source, offset);
                std::memcpy(address, mesh.vertices.data(), bytes);
                transfer = renderer.transferCommands();
            }
            VkBufferCopy region{.srcOffset = offset, .dstOffset = static_cast<u64>(first) * arenaUnits * sizeof(Vertex), .size = bytes};
            vkCmdCopyBuffer(transfer, source, arena->handle(buffer), 1, &region);
            uploadedBytes += bytes;
            layer.buffer = buffer;
            layer.first = first;
            layer.vertices = count;
            std::memcpy(layer.bucketQuads, mesh.bucketQuads, sizeof(layer.bucketQuads));
            hot[slot].layers |= static_cast<u8>(1u << index);
            if (index != TRANSLUCENT) {
                Ordered item{.buffer = buffer, .first = first, .slot = static_cast<u32>(slot), .quads = {}};
                std::memcpy(item.quads, layer.bucketQuads, sizeof(item.quads));
                ordered[index].push_back(item);
            }
            if (index == TRANSLUCENT) {
                u32 indexCount = (count / 4) * 6;
                u32 indexBuffer, indexFirst;
                if (indexArena->allocate(indexCount, indexBuffer, indexFirst)) {
                    u64 indexBytes = u64(indexCount) * sizeof(u32);
                    VkBuffer indexSource;
                    u64 indexOffset;
                    if (mesh.indices.valid()) {
                        StagingSpan span = mesh.indices.release();
                        indexSource = staging->handle(span.block);
                        indexOffset = span.offset;
                        StagingPool *pool = staging.get();
                        renderer.defer([pool, span] { pool->release(span); });
                    } else {
                        auto *indices = reinterpret_cast<u32 *>(renderer.stage(indexBytes, indexSource, indexOffset));
                        for (u32 quad = 0; quad < count / 4; quad++) {
                            u32 base = quad * 4;
                            u32 *out = indices + quad * 6;
                            out[0] = base;
                            out[1] = base + 1;
                            out[2] = base + 2;
                            out[3] = base;
                            out[4] = base + 2;
                            out[5] = base + 3;
                        }
                        transfer = renderer.transferCommands();
                    }
                    VkBufferCopy indexRegion{.srcOffset = indexOffset, .dstOffset = u64(indexFirst) * sizeof(u32),
                                             .size = indexBytes};
                    vkCmdCopyBuffer(transfer, indexSource, indexArena->handle(indexBuffer), 1, &indexRegion);
                    layer.indexBuffer = indexBuffer;
                    layer.indexFirst = indexFirst;
                    layer.indexCount = indexCount;
                }
                layer.centroids = std::make_shared<const std::vector<float> >(std::move(mesh.centroids));
                layer.sortMode = mesh.sortMode;
                for (int axis = 0; axis < 3; axis++) layer.planes[axis] = std::move(mesh.planes[axis]);
                layer.sortToken = ++sortGeneration;
                std::lock_guard guard(cameraLock);
                layer.sortedX = buildCamera[0];
                layer.sortedY = buildCamera[1];
                layer.sortedZ = buildCamera[2];
                layer.signature = planeSignature(layer.planes, buildCamera[0] - static_cast<float>(section->x * 16),
                                                 buildCamera[1] - static_cast<float>(section->y * 16),
                                                 buildCamera[2] - static_cast<float>(section->z * 16));
            }
        }
        if (hot[slot].drawnFrame == frame) {
            for (u32 index = 0; index < LAYERS; index++) {
                if (!hadLayer[index] && section->layers[index].vertices > 0) layerVisible[index].push_back(section);
            }
        }
        GraphNode &node = graphNodes[slot];
        if (!section->compiled || section->visibility != result.visibility) {
            node.visibility = result.visibility;
            node.state = graph::VALID | graph::COMPILED;
            graphVersion++;
        }
        bool hasGeometry = false;
        for (const SectionLayer &layer: section->layers) hasGeometry |= layer.vertices > 0;
        if (hasGeometry && cullResult && slot < cullResult->member.size() && !cullResult->member[slot]) {
            for (const auto &offset: OFFSETS) {
                Section *neighbor = at(section->x + offset[0], section->y + offset[1], section->z + offset[2]);
                if (!neighbor) continue;
                u8 membership = cullResult->member[slotIndex(*neighbor)];
                if (!membership) continue;
                cullResult->member[slot] = membership;
                if (membership & 1) cullResult->regular.push_back(static_cast<u32>(slot));
                cullResult->wide.push_back(static_cast<u32>(slot));
                listDirty = true;
                break;
            }
        }
        section->builtVersion = result.urgent
                                    ? (section->dirtySequence <= result.sequence ? section->version : section->builtVersion)
                                    : result.version;
        section->compiled = true;
        section->visibility = result.visibility;
        if (!section->opaque) section->opaque = std::make_unique<OpaqueMask>();
        *section->opaque = result.opaque;
        section->dirty = section->version != section->builtVersion;
        refresh(*section);
    }

    u32 Terrain::upload(u64 budget, u64 budgetNanos) {
        auto started = std::chrono::steady_clock::now();
        std::vector<std::unique_ptr<MeshResult> > pending;
        {
            std::lock_guard guard(resultsLock);
            pending.swap(results);
        }
        u64 spent = 0;
        u32 applied = 0;
        std::stable_partition(pending.begin(), pending.end(),
                              [](const std::unique_ptr<MeshResult> &result) { return result->urgent; });
        std::size_t index = 0;
        for (; index < pending.size(); index++) {
            if (pending[index]->urgent) {
                apply(*pending[index]);
                applied++;
                continue;
            }
            if (index > 0 && (spent >= budget || u64(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                  std::chrono::steady_clock::now() - started).count()) >= budgetNanos))
                break;
            MeshResult &result = *pending[index];
            for (const LayerMesh &layer: result.layers) spent += u64(layer.count) * sizeof(Vertex);
            apply(result);
            applied++;
        }
        if (index < pending.size()) {
            std::lock_guard guard(resultsLock);
            results.insert(results.begin(),
                           std::make_move_iterator(pending.begin() + static_cast<std::ptrdiff_t>(index)),
                           std::make_move_iterator(pending.end()));
        }
        return applied;
    }

    void Terrain::applySorts() {
        std::vector<SortResult> finished;
        {
            std::lock_guard guard(sortLock);
            finished.swap(sortResults);
        }
        for (SortResult &result: finished) {
            Section *section = at(result.x, result.y, result.z);
            if (!section) continue;
            SectionLayer &layer = section->layers[TRANSLUCENT];
            if (layer.sortToken != result.token) continue;
            layer.sorting = false;
            if (!result.indices.valid() || layer.indexCount != result.indexCount) continue;
            u32 buffer, first;
            if (!indexArena->allocate(result.indexCount, buffer, first)) continue;
            StagingSpan span = result.indices.release();
            VkCommandBuffer transfer = renderer.transferCommands();
            VkBufferCopy region{.srcOffset = span.offset, .dstOffset = u64(first) * sizeof(u32),
                                .size = u64(result.indexCount) * sizeof(u32)};
            vkCmdCopyBuffer(transfer, staging->handle(span.block), indexArena->handle(buffer), 1, &region);
            StagingPool *pool = staging.get();
            VertexArena *target = indexArena.get();
            u32 oldBuffer = layer.indexBuffer, oldFirst = layer.indexFirst, count = layer.indexCount;
            renderer.defer([pool, span, target, oldBuffer, oldFirst, count] {
                pool->release(span);
                target->release(oldBuffer, oldFirst, count);
            });
            layer.indexBuffer = buffer;
            layer.indexFirst = first;
        }
    }

    u32 Terrain::sortTranslucent(u32 limit) {
        applySorts();
        u32 queued = 0;
        auto x = static_cast<float>(eyeX), y = static_cast<float>(eyeY), z = static_cast<float>(eyeZ);
        std::vector<SortJob> jobs;
        for (Section *section: layerVisible[TRANSLUCENT]) {
            if (queued >= limit) break;
            SectionLayer &layer = section->layers[TRANSLUCENT];
            if (layer.indexCount == 0 || layer.sorting || !layer.centroids || layer.sortMode == SortMode::NONE) continue;
            float localX = x - static_cast<float>(section->x * 16);
            float localY = y - static_cast<float>(section->y * 16);
            float localZ = z - static_cast<float>(section->z * 16);
            if (layer.sortMode == SortMode::PLANES) {
                u32 signature = planeSignature(layer.planes, localX, localY, localZ);
                if (signature == layer.signature) continue;
                layer.signature = signature;
            } else {
                float dx = x - layer.sortedX, dy = y - layer.sortedY, dz = z - layer.sortedZ;
                if (dx * dx + dy * dy + dz * dz <= 1.0f) continue;
            }
            layer.sortedX = x;
            layer.sortedY = y;
            layer.sortedZ = z;
            layer.sorting = true;
            jobs.push_back({section->x, section->y, section->z, layer.sortToken, layer.centroids, localX, localY, localZ});
            queued++;
        }
        if (!jobs.empty()) {
            {
                std::lock_guard guard(sortLock);
                for (SortJob &job: jobs) sortJobs.push_back(std::move(job));
            }
            sortWake.notify_one();
        }
        return queued;
    }

    void Terrain::beginShadowView(double x, double y, double z, const op::ShadowCulling &culling) {
        constexpr float SECTION_HALF_SIZE = 8.0f + 1.0f + 0.125f;
        shadowX = x;
        shadowY = y;
        shadowZ = z;
        for (auto &list: shadowLayers) list.clear();
        auto outside = [](float limit, const float min[3], const float max[3]) {
            for (int axis = 0; axis < 3; axis++) {
                if (max[axis] < -limit || min[axis] > limit) return true;
            }
            return false;
        };
        auto visibleSection = [&](const Section &section) {
            if (culling.cullEverything) return false;
            float min[3], max[3];
            double origin[3] = {x, y, z};
            i32 coordinates[3] = {section.x, section.y, section.z};
            for (int axis = 0; axis < 3; axis++) {
                float center = static_cast<float>(static_cast<double>(coordinates[axis]) * 16.0 + 8.0 - origin[axis]);
                min[axis] = center - SECTION_HALF_SIZE;
                max[axis] = center + SECTION_HALF_SIZE;
            }
            if (culling.distance >= 0.0f && outside(culling.distance, min, max)) return false;
            if (culling.safeDistance >= 0.0f && !outside(culling.safeDistance, min, max)) return true;
            for (u32 i = 0; i < culling.planeCount; i++) {
                const float *plane = culling.planes[i];
                float testX = plane[0] < 0.0f ? min[0] : max[0];
                float testY = plane[1] < 0.0f ? min[1] : max[1];
                float testZ = plane[2] < 0.0f ? min[2] : max[2];
                if (std::fma(plane[0], testX, std::fma(plane[1], testY, plane[2] * testZ)) < -plane[3]) return false;
            }
            return true;
        };
        for (Section &section: sections) {
            if (!section.valid || !visibleSection(section)) continue;
            for (u32 layer = 0; layer < LAYERS; layer++) {
                if (section.layers[layer].vertices > 0) shadowLayers[layer].push_back(&section);
            }
        }
        shadowView = true;
    }

    void Terrain::endShadowView() {
        shadowView = false;
    }

    u32 Terrain::draw(u32 layerIndex) {
        const std::vector<Section *> &list = shadowView ? shadowLayers[layerIndex] : layerVisible[layerIndex];
        const double viewX = shadowView ? shadowX : cameraX;
        const double viewY = shadowView ? shadowY : cameraY;
        const double viewZ = shadowView ? shadowZ : cameraZ;
        const double originX = std::floor(viewX), originY = std::floor(viewY), originZ = std::floor(viewZ);
        if (list.empty()) return 0;
        prof::Lap lap;
        lap.part(prof::PART + prof::DRAW_PREPARE);
        bool translucent = layerIndex == TRANSLUCENT;
        u64 tableBytes = sections.size() * sizeof(SectionDraw);
        u64 maxCommands = list.size() * BUCKETS + 64;
        u64 commandBytes = maxCommands * sizeof(VkDrawIndexedIndirectCommand);
        if (!renderer.prepare(commandBytes + 64, tableBytes)) return 0;
        lap.part(prof::PART + prof::DRAW_BIND);
        renderer.terrainLayer = static_cast<i32>(layerIndex);
        bool bound = renderer.bindState(arenaUnits == EXTENDED_UNITS ? renderer.layouts.extendedTerrain() : renderer.layouts.terrain(),
                                        VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        renderer.terrainLayer = -1;
        if (!bound) return 0;
        renderer.state.dirty |= RenderState::TRANSFORM;
        lap.part(prof::PART + prof::DRAW_RECORD);
        u32 record = renderer.record();
        renderer.state.dirty |= RenderState::TRANSFORM;
        {
            // The record sits in memory mapped for writing to the GPU, where reading back is very slow. The matrix in it
            // was copied from the model view state just now, so the adjusted column is computed from that and written.
            auto *drawRecord = reinterpret_cast<DrawRecord *>(renderer.recordStream().address(static_cast<u64>(record) * sizeof(DrawRecord)));
            const float *modelView = renderer.state.modelView.top();
            float fraction[3] = {
                static_cast<float>(viewX - originX), static_cast<float>(viewY - originY), static_cast<float>(viewZ - originZ)
            };
            float adjusted[4];
            for (int row = 0; row < 4; row++) {
                adjusted[row] = modelView[12 + row] -
                                (modelView[row] * fraction[0] + modelView[4 + row] * fraction[1] + modelView[8 + row] * fraction[2]);
            }
            std::memcpy(&drawRecord->modelView[12], adjusted, sizeof(adjusted));
        }
        bool facing = !translucent && !shadowView && renderer.state.cullsBack();

        lap.part(prof::PART + prof::DRAW_ALLOC);
        FrameStream &data = renderer.dataStream();
        FrameStream &sectionStream = renderer.sectionStream();
        u64 tableOffset = sectionStream.allocate(tableBytes, sizeof(SectionDraw));
        u64 commandOffset = data.allocate(commandBytes, 4);
        if (tableOffset == FrameStream::FAILED || commandOffset == FrameStream::FAILED) return 0;
        auto *table = reinterpret_cast<SectionDraw *>(sectionStream.address(tableOffset));
        auto *commands = reinterpret_cast<VkDrawIndexedIndirectCommand *>(data.address(commandOffset));
        auto tableBase = static_cast<u32>(tableOffset / sizeof(SectionDraw));

        lap.part(prof::PART + prof::DRAW_WALK);
        drawRanges.clear();
        u32 drawn = 0;
        auto addRange = [&](u32 buffer, u32 first, u32 count) {
            if (!drawRanges.empty()) {
                if (auto &[rBuffer, rFirst, rCount] = drawRanges.back(); rBuffer == buffer && rFirst + rCount == first) {
                    rCount += count;
                    return;
                }
            }
            drawRanges.push_back({.buffer = buffer, .first = first, .count = count});
        };
        // luka: ASTATINE_BRIDGE overrides the bridge length, to measure draws against vertices.
        static const u32 bridge = [] {
            const char *value = std::getenv("ASTATINE_BRIDGE");
            return value ? static_cast<u32>(std::strtoul(value, nullptr, 10)) : BRIDGE_VERTICES;
        }();
        // luka: the solid layers are drawn nearest section first. ASTATINE_ORDER=buffer goes back to vertex buffer
        // order, to measure what the hidden pixels cost.
        static const bool nearFirst = [] {
            const char *value = std::getenv("ASTATINE_ORDER");
            return !value || std::strcmp(value, "buffer") != 0;
        }();
        auto addBuckets = [&](std::size_t slot, i32 x, i32 y, i32 z, u32 buffer, u32 firstVertex, const u32 *quads) {
            SectionDraw &entry = table[slot];
            entry.offset[0] = static_cast<float>(x * 16 - originX);
            entry.offset[1] = static_cast<float>(y * 16 - originY);
            entry.offset[2] = static_cast<float>(z * 16 - originZ);
            entry.record = record;
            drawn++;
            bool show[BUCKETS];
            double minX = x * 16.0, minY = y * 16.0, minZ = z * 16.0;
            show[0] = !facing || eyeY < minY + 18.0;
            show[1] = !facing || eyeY > minY - 2.0;
            show[2] = !facing || eyeZ < minZ + 18.0;
            show[3] = !facing || eyeZ > minZ - 2.0;
            show[4] = !facing || eyeX < minX + 18.0;
            show[5] = !facing || eyeX > minX - 2.0;
            show[6] = true;
            u32 vertex = firstVertex;
            u32 start = 0, end = 0, gap = 0;
            bool open = false;
            for (u32 bucket = 0; bucket < BUCKETS; bucket++) {
                u32 count = quads[bucket] * 4;
                if (count == 0) continue;
                if (show[bucket]) {
                    if (!open) {
                        start = vertex;
                        open = true;
                    } else if (gap > bridge) {
                        addRange(buffer, start, end - start);
                        start = vertex;
                    }
                    end = vertex + count;
                    gap = 0;
                } else if (open) {
                    gap += count;
                }
                vertex += count;
            }
            if (open) addRange(buffer, start, end - start);
        };
        auto addSection = [&](const Section &section) {
            const SectionLayer &layer = section.layers[layerIndex];
            if (layer.vertices == 0) return;
            addBuckets(slotIndex(section), section.x, section.y, section.z, layer.buffer, layer.first, layer.bucketQuads);
        };

        if (translucent) {
            const Device &translucentDevice = *renderer.context().device;
            VkCommandBuffer translucentCommands = renderer.commands();
            u32 count = 0, start = 0;
            u32 vertexBuffer = ~0u, indexBuffer = ~0u;
            auto flush = [&] {
                u32 run = count - start;
                if (run == 0) return;
                renderer.setVertexBuffers(arena->handle(vertexBuffer), 0, VK_NULL_HANDLE, 0);
                renderer.bindIndexBuffer(indexArena->handle(indexBuffer));
                if (translucentDevice.multiDrawIndirect) {
                    for (u32 first = 0; first < run;) {
                        u32 batch = std::min(run - first, translucentDevice.maxDrawIndirectCount);
                        vkCmdDrawIndexedIndirect(translucentCommands, data.handle(),
                                                 commandOffset + u64(start + first) * sizeof(VkDrawIndexedIndirectCommand),
                                                 batch, sizeof(VkDrawIndexedIndirectCommand));
                        first += batch;
                    }
                } else {
                    for (u32 i = start; i < count; i++) {
                        const VkDrawIndexedIndirectCommand &command = commands[i];
                        vkCmdDrawIndexed(translucentCommands, command.indexCount, 1, command.firstIndex,
                                         command.vertexOffset, command.firstInstance);
                    }
                }
                start = count;
            };
            for (std::size_t i = list.size(); i-- > 0;) {
                Section &section = *list[i];
                const SectionLayer &layer = section.layers[layerIndex];
                if (layer.vertices == 0 || layer.indexCount == 0 || count >= maxCommands) continue;
                SectionDraw &entry = table[slotIndex(section)];
                entry.offset[0] = static_cast<float>(section.x * 16 - originX);
                entry.offset[1] = static_cast<float>(section.y * 16 - originY);
                entry.offset[2] = static_cast<float>(section.z * 16 - originZ);
                entry.record = record;
                if (layer.buffer != vertexBuffer || layer.indexBuffer != indexBuffer) {
                    flush();
                    vertexBuffer = layer.buffer;
                    indexBuffer = layer.indexBuffer;
                }
                commands[count++] = {layer.indexCount, 1, layer.indexFirst, static_cast<i32>(layer.first), tableBase};
                drawn++;
            }
            flush();
            drawnLastFrame = drawn;
            return drawn;
        } else if (!shadowView && nearFirst) {
            // The visible list runs outward from the camera, so what is drawn first hides what comes later and the
            // GPU skips those pixels. Costs more ranges than vertex buffer order.
            for (Section *section: list) addSection(*section);
        } else if (!shadowView) {
            // Already in vertex buffer order, so neighbouring sections join into one range without a sort. Uploads
            // since the last settle sit at the end and only cost a few extra ranges until they are merged in.
            std::vector<Ordered> &entries = ordered[layerIndex];
            if (entries.size() - orderedSorted[layerIndex] > SETTLE_TAIL || orderedDead[layerIndex] > SETTLE_DEAD) {
                lap.part(prof::PART + prof::DRAW_ORDER);
                settle(layerIndex);
                lap.part(prof::PART + prof::DRAW_WALK);
            }
            const Hot *states = hot.data();
            prof::count(prof::PART + prof::N_ORDERED, entries.size());
            for (const Ordered &item: entries) {
                if (item.slot == DEAD) continue;
                const Hot &state = states[item.slot];
                if (state.drawnFrame != frame) continue;
                addBuckets(item.slot, state.x, state.y, state.z, item.buffer, item.first, item.quads);
            }
        } else {
            for (Section *section: list) addSection(*section);
            lap.part(prof::PART + prof::DRAW_ORDER);
            std::ranges::sort(drawRanges, [](const DrawRange &a, const DrawRange &b) {
                return a.buffer != b.buffer ? a.buffer < b.buffer : a.first < b.first;
            });
            std::size_t merged = 0;
            for (auto range : drawRanges) {
                if (merged > 0) {
                    if (auto &[buffer, first, count] = drawRanges[merged - 1]; buffer == range.buffer && first + count >= range.first) {
                        count = std::max(first + count, range.first + range.count) - first;
                        continue;
                    }
                }
                drawRanges[merged++] = range;
            }
            drawRanges.resize(merged);
        }
        prof::count(prof::PART + prof::N_DRAWN, drawn);
        if (prof::enabled) {
            u64 vertices = 0;
            for (const DrawRange &range : drawRanges) vertices += range.count;
            prof::count(prof::PART + prof::N_RANGES, drawRanges.size());
            prof::count(prof::PART + prof::N_VERTS, vertices);
        }
        if (drawRanges.empty()) {
            drawnLastFrame = drawn;
            return drawn;
        }

        lap.part(prof::PART + prof::DRAW_EMIT);
        const Device &device = *renderer.context().device;
        VkCommandBuffer main = renderer.commands();
        renderer.bindQuadIndices(false);
        u32 commandCount = 0;
        u32 runStart = 0;
        u32 runBuffer = drawRanges.front().buffer;
        auto flushRun = [&] {
            u32 runCount = commandCount - runStart;
            if (runCount == 0) return;
            renderer.setVertexBuffers(arena->handle(runBuffer), 0, VK_NULL_HANDLE, 0);
            if (device.multiDrawIndirect) {
                for (u32 first = 0; first < runCount;) {
                    u32 batch = std::min(runCount - first, device.maxDrawIndirectCount);
                    vkCmdDrawIndexedIndirect(main, data.handle(),
                                             commandOffset + static_cast<u64>(runStart + first) * sizeof(VkDrawIndexedIndirectCommand),
                                             batch, sizeof(VkDrawIndexedIndirectCommand));
                    first += batch;
                }
            } else {
                for (u32 i = runStart; i < commandCount; i++) {
                    const VkDrawIndexedIndirectCommand &command = commands[i];
                    vkCmdDrawIndexed(main, command.indexCount, 1, 0, command.vertexOffset, command.firstInstance);
                }
            }
            runStart = commandCount;
        };
        constexpr u32 MAX_VERTICES = Renderer::MAX_QUADS * 4;
        for (const auto &[buffer, first, count]: drawRanges) {
            if (buffer != runBuffer) {
                flushRun();
                runBuffer = buffer;
            }
            for (u32 offset = 0; offset < count;) {
                u32 vertices = std::min(count - offset, MAX_VERTICES);
                if (commandCount >= maxCommands) {
                    flushRun();
                    break;
                }
                commands[commandCount++] = {
                    (vertices / 4) * 6, 1, 0, static_cast<i32>(first + offset), tableBase
                };
                offset += vertices;
            }
        }
        flushRun();
        drawnLastFrame = drawn;
        return drawn;
    }

    std::string Terrain::debug() const {
        u32 total = 0, compiled = 0, dirty = 0, building = 0;
        for (const Section &section: sections) {
            if (!section.valid) continue;
            total++;
            if (section.compiled) compiled++;
            if (section.dirty) dirty++;
            if (section.building) building++;
        }
        u32 rendered = 0;
        for (u32 slot: visibleSlots) {
            const Section *section = &sections[slot];
            bool any = false;
            for (const SectionLayer &layer: section->layers) any |= layer.vertices > 0;
            if (any) rendered++;
        }
        return log::format("C: %u/%u,%u D: %d, B: %u, Q: %u, M: %lluMB", rendered, total, compiled, viewDistance, building, dirty,
                           arena->usedVertices() * arenaUnits * sizeof(Vertex) >> 20);
    }
} // namespace at::terrain
