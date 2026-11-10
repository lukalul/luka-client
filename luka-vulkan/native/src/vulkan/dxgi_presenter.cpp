#ifdef _WIN32

#include "vulkan/dxgi_presenter.h"

#include "core/error.h"
#include "core/log.h"
#include "vulkan/device.h"

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>
#include <vulkan/vulkan_win32.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

using Microsoft::WRL::ComPtr;

namespace at {
    // Back buffers of the swapchain. A frame drawn straight into a back buffer has to wait until that buffer has
    // left the screen, which is BUFFER_COUNT - 1 presents back. With three frames in flight, six buffers mean that
    // present is always long done (measured: 3 buffers 2,300 FPS, 5 or more 2,750 to 2,900).
    // ASTATINE_DXGI_BUFFERS (2 to 8) changes it, to measure.
    static u32 BUFFER_COUNT = 6;
    static constexpr u32 MAX_LATENCY = 2;

    static void hr(HRESULT result, const char *action) {
        if (FAILED(result)) {
            char code[16];
            std::snprintf(code, sizeof(code), "0x%08lX", static_cast<unsigned long>(result));
            fail(std::string(action) + " failed with HRESULT " + code);
        }
    }

    struct DxgiPresenter::State {
        HWND window = nullptr;
        ComPtr<IDXGIFactory4> factory;
        ComPtr<ID3D12Device> d3d;
        ComPtr<ID3D12CommandQueue> queue;
        ComPtr<IDXGISwapChain3> swapchain;
        HANDLE idleEvent = nullptr;
        HANDLE waitable = nullptr;
        bool tearing = false;
        std::atomic<bool> vsync{true};
        UINT flags = 0;

        ComPtr<ID3D12Fence> renderedFence;
        ComPtr<ID3D12Fence> copiedFence;
        VkSemaphore rendered = VK_NULL_HANDLE;
        VkSemaphore copied = VK_NULL_HANDLE;
        u64 renderedValue = 0;
        u64 copiedValue = 0;

        struct Buffer {
            ComPtr<ID3D12Resource> back;
            ComPtr<ID3D12Resource> shared;
            ComPtr<ID3D12CommandAllocator> allocator;
            ComPtr<ID3D12GraphicsCommandList> copy;
            VkDeviceMemory memory = VK_NULL_HANDLE;
            u64 copiedAt = 0;
        };

        std::vector<Buffer> buffers;

        // luka: the copy and Present of a frame run on their own thread, so the render thread never waits for DXGI.
        // One frame may be waiting (job) while one is being presented (busy); a third makes the render thread wait.
        struct Job {
            u32 index = 0;
            u64 rendered = 0;
            u64 copied = 0;
            bool vsync = false;
        };

        bool threaded = true;
        bool alwaysWait = false;
        // Measuring only (ASTATINE_DXGI_TEST): 1 = no Present, 2 = no copy, 3 = copy without waiting for Vulkan
        int test = 0;
        // luka: without vsync Vulkan draws straight into the swapchain's own back buffers, so a frame needs no copy
        // and no D3D12 work at all, only the Present.
        bool wantDirect = true;
        bool direct = false;
        std::string dumpPath;
        u32 presents = 0;
        bool timing = false;
        u64 spent[4] = {};
        u32 timed = 0;
        std::thread thread;
        std::mutex mutex;
        std::condition_variable wake; // to the present thread: a job or stop
        std::condition_variable done; // to the render thread: the job was taken, or the thread is idle
        Job job;
        bool pending = false;
        bool busy = false;
        bool stop = false;
        std::string error;
        std::atomic<bool> failed{false};
        // The back buffer the next frame goes to. A flip swapchain hands them out in order, so the render thread
        // does not have to wait for the Present before it to know.
        u32 nextIndex = 0;

        PFN_vkGetMemoryWin32HandlePropertiesKHR getMemoryWin32HandleProperties = nullptr;
        PFN_vkImportSemaphoreWin32HandleKHR importSemaphoreWin32Handle = nullptr;
    };

    static VkSemaphore importFence(const Device &device, DxgiPresenter::State &state, ID3D12Fence *fence) {
        HANDLE handle = nullptr;
        hr(state.d3d->CreateSharedHandle(fence, nullptr, GENERIC_ALL, nullptr, &handle), "CreateSharedHandle");
        VkSemaphoreTypeCreateInfoKHR type{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO_KHR};
        type.semaphoreType = VK_SEMAPHORE_TYPE_TIMELINE_KHR;
        VkSemaphoreCreateInfo info{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, .pNext = &type};
        VkSemaphore semaphore = VK_NULL_HANDLE;
        check(vkCreateSemaphore(device.handle, &info, nullptr, &semaphore), "vkCreateSemaphore");
        VkImportSemaphoreWin32HandleInfoKHR import{.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_WIN32_HANDLE_INFO_KHR};
        import.semaphore = semaphore;
        import.handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_D3D12_FENCE_BIT;
        import.handle = handle;
        VkResult result = state.importSemaphoreWin32Handle(device.handle, &import);
        CloseHandle(handle);
        if (result != VK_SUCCESS) {
            vkDestroySemaphore(device.handle, semaphore, nullptr);
            check(result, "vkImportSemaphoreWin32HandleKHR");
        }
        return semaphore;
    }

    // Measuring only: writes what is in the back buffer about to be shown to a .bmp, read back through D3D12.
    static void dumpBackBuffer(DxgiPresenter::State &s, const DxgiPresenter::State::Job &job) {
        ID3D12Resource *back = s.buffers[job.index].back.Get();
        HANDLE event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (s.renderedFence->GetCompletedValue() < job.rendered) {
            s.renderedFence->SetEventOnCompletion(job.rendered, event);
            WaitForSingleObject(event, 5000);
        }
        D3D12_RESOURCE_DESC desc = back->GetDesc();
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
        UINT64 total = 0;
        s.d3d->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
        D3D12_HEAP_PROPERTIES heap{.Type = D3D12_HEAP_TYPE_READBACK};
        D3D12_RESOURCE_DESC bufferDesc{};
        bufferDesc.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
        bufferDesc.Width = total;
        bufferDesc.Height = 1;
        bufferDesc.DepthOrArraySize = 1;
        bufferDesc.MipLevels = 1;
        bufferDesc.SampleDesc.Count = 1;
        bufferDesc.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
        ComPtr<ID3D12Resource> readback;
        hr(s.d3d->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_NONE, &bufferDesc, D3D12_RESOURCE_STATE_COPY_DEST,
                                          nullptr, IID_PPV_ARGS(&readback)), "readback buffer");
        ComPtr<ID3D12CommandAllocator> allocator;
        ComPtr<ID3D12GraphicsCommandList> list;
        hr(s.d3d->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&allocator)), "allocator");
        hr(s.d3d->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, allocator.Get(), nullptr, IID_PPV_ARGS(&list)),
           "list");
        D3D12_RESOURCE_BARRIER barrier{};
        barrier.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barrier.Transition.pResource = back;
        barrier.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
        barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
        list->ResourceBarrier(1, &barrier);
        D3D12_TEXTURE_COPY_LOCATION from{};
        from.pResource = back;
        from.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        D3D12_TEXTURE_COPY_LOCATION to{};
        to.pResource = readback.Get();
        to.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
        to.PlacedFootprint = footprint;
        list->CopyTextureRegion(&to, 0, 0, 0, &from, nullptr);
        std::swap(barrier.Transition.StateBefore, barrier.Transition.StateAfter);
        list->ResourceBarrier(1, &barrier);
        hr(list->Close(), "Close");
        ID3D12CommandList *lists[] = {list.Get()};
        s.queue->ExecuteCommandLists(1, lists);
        ComPtr<ID3D12Fence> fence;
        hr(s.d3d->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&fence)), "CreateFence");
        hr(s.queue->Signal(fence.Get(), 1), "Signal");
        fence->SetEventOnCompletion(1, event);
        WaitForSingleObject(event, 5000);
        CloseHandle(event);
        void *data = nullptr;
        hr(readback->Map(0, nullptr, &data), "Map");
        FILE *file = std::fopen(s.dumpPath.c_str(), "wb");
        if (file) {
            u32 w = footprint.Footprint.Width, h = footprint.Footprint.Height;
            BITMAPFILEHEADER head{};
            head.bfType = 0x4D42;
            head.bfOffBits = sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER);
            head.bfSize = head.bfOffBits + w * h * 4;
            BITMAPINFOHEADER info{};
            info.biSize = sizeof(info);
            info.biWidth = static_cast<LONG>(w);
            info.biHeight = -static_cast<LONG>(h);
            info.biPlanes = 1;
            info.biBitCount = 32;
            std::fwrite(&head, sizeof(head), 1, file);
            std::fwrite(&info, sizeof(info), 1, file);
            for (u32 y = 0; y < h; y++) {
                std::fwrite(static_cast<const char *>(data) + footprint.Offset + y * footprint.Footprint.RowPitch, 4, w,
                            file);
            }
            std::fclose(file);
        }
        readback->Unmap(0, nullptr);
        log::info("[dxgi] back buffer %u written to %s (direct=%d)", job.index, s.dumpPath.c_str(), s.direct ? 1 : 0);
    }

    // The copy into the back buffer and its Present. Runs on the present thread.
    static bool presentNow(DxgiPresenter::State &s, const DxgiPresenter::State::Job &job) {
        DxgiPresenter::State::Buffer &buffer = s.buffers[job.index];
        if (s.swapchain->GetCurrentBackBufferIndex() != job.index) {
            // Out of step (a Present before this one failed). Nothing is shown; the surface is made again.
            hr(s.queue->Signal(s.copiedFence.Get(), job.copied), "ID3D12CommandQueue::Signal");
            return false;
        }
        auto stamp = [] { return std::chrono::duration_cast<std::chrono::nanoseconds>(
                                  std::chrono::steady_clock::now().time_since_epoch()).count(); };
        auto t0 = stamp();
        if (s.test != 2 && s.test != 3) hr(s.queue->Wait(s.renderedFence.Get(), job.rendered), "ID3D12CommandQueue::Wait");
        ID3D12CommandList *lists[] = {buffer.copy.Get()};
        if (!s.direct && s.test != 2) s.queue->ExecuteCommandLists(1, lists);
        if (!s.direct) hr(s.queue->Signal(s.copiedFence.Get(), job.copied), "ID3D12CommandQueue::Signal");
        if (!s.dumpPath.empty() && ++s.presents == 3000) dumpBackBuffer(s, job);
        auto t1 = stamp();
        UINT flags = !job.vsync && s.tearing ? DXGI_PRESENT_ALLOW_TEARING : 0;
        HRESULT result = s.test == 1 ? S_OK : s.swapchain->Present(job.vsync ? 1 : 0, flags);
        if (result == DXGI_ERROR_DEVICE_REMOVED || result == DXGI_ERROR_DEVICE_RESET) hr(result, "Present");
        if (FAILED(result) && flags) {
            log::warn("Present with tearing failed with HRESULT 0x%08lX, retrying without it",
                      static_cast<unsigned long>(result));
            s.tearing = false;
            result = s.swapchain->Present(0, 0);
        }
        if (FAILED(result)) log::warn("Present failed with HRESULT 0x%08lX", static_cast<unsigned long>(result));
        // Drawing straight into the back buffers: this Present takes the buffer shown before it off the screen, and
        // the signal behind it is what the next frame drawn into that buffer waits for.
        if (s.direct) hr(s.queue->Signal(s.copiedFence.Get(), job.copied), "ID3D12CommandQueue::Signal");
        auto t2 = stamp();
        // The latency wait paces a vsynced game to the screen. Without vsync there is nothing to wait for.
        if (s.waitable && (job.vsync || s.alwaysWait)) WaitForSingleObjectEx(s.waitable, 1000, TRUE);
        if (s.timing) {
            auto t3 = stamp();
            s.spent[0] += t1 - t0;
            s.spent[1] += t2 - t1;
            s.spent[2] += t3 - t2;
            if (++s.timed == 2000) {
                log::info("[dxgi] us/present: copy=%.1f present=%.1f wait=%.1f tearing=%d flags=%u", s.spent[0] / 2e6,
                          s.spent[1] / 2e6, s.spent[2] / 2e6, s.tearing ? 1 : 0, static_cast<unsigned>(flags));
                s.spent[0] = s.spent[1] = s.spent[2] = 0;
                s.timed = 0;
            }
        }
        return SUCCEEDED(result);
    }

    static void presentLoop(DxgiPresenter::State &s) {
        SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_ABOVE_NORMAL);
        std::unique_lock lock(s.mutex);
        for (;;) {
            s.wake.wait(lock, [&s] { return s.pending || s.stop; });
            if (!s.pending) return;
            DxgiPresenter::State::Job job = s.job;
            s.pending = false;
            s.busy = true;
            s.done.notify_all();
            lock.unlock();
            bool presented = false;
            std::string error;
            try {
                presented = presentNow(s, job);
            } catch (const std::exception &thrown) {
                error = thrown.what();
            }
            if (!presented) s.failed.store(true, std::memory_order_relaxed);
            lock.lock();
            if (!error.empty() && s.error.empty()) s.error = error;
            s.busy = false;
            s.done.notify_all();
        }
    }

    // Returns once the present thread has nothing left to do. Its error, if it had one, is raised here.
    static void drain(DxgiPresenter::State &s) {
        if (!s.thread.joinable()) return;
        std::string error;
        {
            std::unique_lock lock(s.mutex);
            s.done.wait(lock, [&s] { return !s.pending && !s.busy; });
            error.swap(s.error);
        }
        if (!error.empty()) fail(error);
    }

    DxgiPresenter::DxgiPresenter(const Device &device, void *window, u32 initialWidth, u32 initialHeight)
        : device(device), state(std::make_unique<State>()) {
        format = VK_FORMAT_B8G8R8A8_UNORM;
        layout = VK_IMAGE_LAYOUT_GENERAL;
        blitTarget = true;
        State &s = *state;
        if (const char *buffers = std::getenv("ASTATINE_DXGI_BUFFERS")) {
            int wanted = std::atoi(buffers);
            if (wanted >= 2 && wanted <= 8) BUFFER_COUNT = static_cast<u32>(wanted);
        }
        s.window = static_cast<HWND>(window);
        s.getMemoryWin32HandleProperties = reinterpret_cast<PFN_vkGetMemoryWin32HandlePropertiesKHR>(
            vkGetDeviceProcAddr(device.handle, "vkGetMemoryWin32HandlePropertiesKHR"));
        s.importSemaphoreWin32Handle = reinterpret_cast<PFN_vkImportSemaphoreWin32HandleKHR>(
            vkGetDeviceProcAddr(device.handle, "vkImportSemaphoreWin32HandleKHR"));
        if (!s.getMemoryWin32HandleProperties || !s.importSemaphoreWin32Handle) fail("Win32 interop entry points are missing");

        hr(CreateDXGIFactory2(0, IID_PPV_ARGS(&s.factory)), "CreateDXGIFactory2");
        LUID luid;
        std::memcpy(&luid, device.luid, sizeof(luid));
        ComPtr<IDXGIAdapter1> adapter;
        hr(s.factory->EnumAdapterByLuid(luid, IID_PPV_ARGS(&adapter)), "EnumAdapterByLuid");
        hr(D3D12CreateDevice(adapter.Get(), D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&s.d3d)), "D3D12CreateDevice");

        D3D12_COMMAND_QUEUE_DESC queueDesc{};
        queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
        queueDesc.Priority = D3D12_COMMAND_QUEUE_PRIORITY_HIGH;
        hr(s.d3d->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&s.queue)), "CreateCommandQueue");

        ComPtr<IDXGIFactory5> factory5;
        if (SUCCEEDED(s.factory.As(&factory5))) {
            BOOL allow = FALSE;
            if (SUCCEEDED(factory5->CheckFeatureSupport(DXGI_FEATURE_PRESENT_ALLOW_TEARING, &allow, sizeof(allow))))
                s.tearing = allow == TRUE;
        }
        s.flags = DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT | (s.tearing ? DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING : 0);

        hr(s.d3d->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&s.renderedFence)), "CreateFence");
        hr(s.d3d->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&s.copiedFence)), "CreateFence");
        s.idleEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (!s.idleEvent) fail("CreateEvent failed");
        s.rendered = importFence(device, s, s.renderedFence.Get());
        s.copied = importFence(device, s, s.copiedFence.Get());

        DXGI_SWAP_CHAIN_DESC1 desc{};
        desc.Width = std::max(initialWidth, 1u);
        desc.Height = std::max(initialHeight, 1u);
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        desc.BufferCount = BUFFER_COUNT;
        desc.Scaling = DXGI_SCALING_STRETCH;
        desc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        desc.AlphaMode = DXGI_ALPHA_MODE_IGNORE;
        desc.Flags = s.flags;
        ComPtr<IDXGISwapChain1> swapchain1;
        hr(s.factory->CreateSwapChainForHwnd(s.queue.Get(), s.window, &desc, nullptr, nullptr, &swapchain1),
           "CreateSwapChainForHwnd");
        hr(swapchain1.As(&s.swapchain), "IDXGISwapChain3");
        s.factory->MakeWindowAssociation(s.window, DXGI_MWA_NO_ALT_ENTER | DXGI_MWA_NO_WINDOW_CHANGES);
        hr(s.swapchain->SetMaximumFrameLatency(MAX_LATENCY), "SetMaximumFrameLatency");
        s.waitable = s.swapchain->GetFrameLatencyWaitableObject();

        create(desc.Width, desc.Height, true);

        const char *threadChoice = std::getenv("ASTATINE_DXGI_THREAD");
        const char *waitChoice = std::getenv("ASTATINE_DXGI_WAIT");
        s.threaded = !(threadChoice && std::strcmp(threadChoice, "0") == 0);
        s.alwaysWait = waitChoice && std::strcmp(waitChoice, "1") == 0;
        const char *testChoice = std::getenv("ASTATINE_DXGI_TEST");
        s.test = testChoice ? std::atoi(testChoice) : 0;
        s.timing = std::getenv("ASTATINE_PROFILE") != nullptr;
        const char *directChoice = std::getenv("ASTATINE_DXGI_DIRECT");
        s.wantDirect = !(directChoice && std::strcmp(directChoice, "0") == 0);
        if (const char *dump = std::getenv("ASTATINE_DXGI_DUMP")) s.dumpPath = dump;
        {
            HANDLE probe = nullptr;
            HRESULT shared = s.d3d->CreateSharedHandle(s.buffers[0].back.Get(), nullptr, GENERIC_ALL, nullptr, &probe);
            log::info("[dxgi] sharing a back buffer: HRESULT 0x%08lX, test=%d", static_cast<unsigned long>(shared), s.test);
            if (probe) CloseHandle(probe);
        }
        if (s.threaded) s.thread = std::thread(presentLoop, std::ref(s));
        log::info("DXGI presenter: %s, frame latency wait %s", s.threaded ? "own thread" : "render thread",
                  s.alwaysWait ? "every frame" : "with vsync only");
    }

    DxgiPresenter::~DxgiPresenter() {
        State &s = *state;
        if (s.thread.joinable()) {
            try {
                drain(s);
            } catch (const std::exception &) {
            }
            {
                std::lock_guard lock(s.mutex);
                s.stop = true;
            }
            s.wake.notify_all();
            s.thread.join();
        }
        if (s.queue) waitIdle();
        destroyBuffers();
        if (s.rendered) vkDestroySemaphore(device.handle, s.rendered, nullptr);
        if (s.copied) vkDestroySemaphore(device.handle, s.copied, nullptr);
        if (s.idleEvent) CloseHandle(s.idleEvent);
        if (s.waitable) CloseHandle(s.waitable);
    }

    void DxgiPresenter::waitIdle() {
        State &s = *state;
        drain(s);
        u64 value = ++s.copiedValue;
        if (FAILED(s.queue->Signal(s.copiedFence.Get(), value))) return;
        if (s.copiedFence->GetCompletedValue() < value) {
            s.copiedFence->SetEventOnCompletion(value, s.idleEvent);
            WaitForSingleObject(s.idleEvent, 5000);
        }
    }

    void DxgiPresenter::destroyBuffers() {
        State &s = *state;
        for (VkImageView view: views) vkDestroyImageView(device.handle, view, nullptr);
        for (VkImage image: images) vkDestroyImage(device.handle, image, nullptr);
        for (State::Buffer &buffer: s.buffers) {
            if (buffer.memory) vkFreeMemory(device.handle, buffer.memory, nullptr);
        }
        views.clear();
        images.clear();
        s.buffers.clear();
    }

    bool DxgiPresenter::create(u32 requestedWidth, u32 requestedHeight, bool vsync) {
        State &s = *state;
        s.vsync = vsync;
        if (requestedWidth == 0 || requestedHeight == 0) return false;
        waitIdle();
        destroyBuffers();
        hr(s.swapchain->ResizeBuffers(BUFFER_COUNT, requestedWidth, requestedHeight, DXGI_FORMAT_B8G8R8A8_UNORM, s.flags),
           "ResizeBuffers");
        width = requestedWidth;
        height = requestedHeight;
        s.nextIndex = s.swapchain->GetCurrentBackBufferIndex();
        s.direct = s.wantDirect && !vsync && s.tearing;
        s.failed.store(false, std::memory_order_relaxed);
        s.buffers.resize(BUFFER_COUNT);
        images.resize(BUFFER_COUNT, VK_NULL_HANDLE);
        views.resize(BUFFER_COUNT, VK_NULL_HANDLE);

        for (u32 i = 0; i < BUFFER_COUNT; i++) {
            State::Buffer &buffer = s.buffers[i];
            hr(s.swapchain->GetBuffer(i, IID_PPV_ARGS(&buffer.back)), "GetBuffer");

            if (!s.direct) {
            D3D12_HEAP_PROPERTIES heap{.Type = D3D12_HEAP_TYPE_DEFAULT};
            D3D12_RESOURCE_DESC desc{};
            desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
            desc.Width = width;
            desc.Height = height;
            desc.DepthOrArraySize = 1;
            desc.MipLevels = 1;
            desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
            desc.SampleDesc.Count = 1;
            desc.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
            desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET;
            hr(s.d3d->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_SHARED, &desc, D3D12_RESOURCE_STATE_COMMON, nullptr,
                                              IID_PPV_ARGS(&buffer.shared)), "CreateCommittedResource");
            }
            ID3D12Resource *target = s.direct ? buffer.back.Get() : buffer.shared.Get();

            VkExternalMemoryImageCreateInfoKHR external{.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_KHR};
            external.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT;
            VkImageCreateInfo imageInfo{.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external};
            imageInfo.imageType = VK_IMAGE_TYPE_2D;
            imageInfo.format = format;
            imageInfo.extent = {width, height, 1};
            imageInfo.mipLevels = 1;
            imageInfo.arrayLayers = 1;
            imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
            imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
            imageInfo.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                              VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            check(vkCreateImage(device.handle, &imageInfo, nullptr, &images[i]), "vkCreateImage");

            HANDLE handle = nullptr;
            HRESULT sharedResult = s.d3d->CreateSharedHandle(target, nullptr, GENERIC_ALL, nullptr, &handle);
            if (FAILED(sharedResult) && s.direct) {
                log::warn("The back buffers cannot be shared (HRESULT 0x%08lX), copying each frame instead",
                          static_cast<unsigned long>(sharedResult));
                s.wantDirect = false;
                return create(requestedWidth, requestedHeight, vsync);
            }
            hr(sharedResult, "CreateSharedHandle");
            VkMemoryRequirements requirements;
            vkGetImageMemoryRequirements(device.handle, images[i], &requirements);
            u32 typeBits = requirements.memoryTypeBits;
            VkMemoryWin32HandlePropertiesKHR properties{.sType = VK_STRUCTURE_TYPE_MEMORY_WIN32_HANDLE_PROPERTIES_KHR};
            if (s.getMemoryWin32HandleProperties(device.handle, VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT, handle,
                                                 &properties) == VK_SUCCESS && (typeBits & properties.memoryTypeBits)) {
                typeBits &= properties.memoryTypeBits;
            }
            VkMemoryDedicatedAllocateInfoKHR dedicated{.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO_KHR};
            dedicated.image = images[i];
            VkImportMemoryWin32HandleInfoKHR import{.sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR};
            import.pNext = &dedicated;
            import.handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT;
            import.handle = handle;
            VkMemoryAllocateInfo allocate{.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &import};
            allocate.allocationSize = requirements.size;
            allocate.memoryTypeIndex = device.memoryType(typeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VkResult result = vkAllocateMemory(device.handle, &allocate, nullptr, &buffer.memory);
            CloseHandle(handle);
            if (result != VK_SUCCESS && s.direct) {
                log::warn("Vulkan cannot draw into the back buffers (VkResult %d), copying each frame instead",
                          static_cast<int>(result));
                s.wantDirect = false;
                return create(requestedWidth, requestedHeight, vsync);
            }
            check(result, "vkAllocateMemory");
            check(vkBindImageMemory(device.handle, images[i], buffer.memory, 0), "vkBindImageMemory");

            VkImageViewCreateInfo viewInfo{.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            viewInfo.image = images[i];
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = format;
            viewInfo.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            check(vkCreateImageView(device.handle, &viewInfo, nullptr, &views[i]), "vkCreateImageView");

            if (s.direct) continue;
            hr(s.d3d->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&buffer.allocator)),
               "CreateCommandAllocator");
            hr(s.d3d->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, buffer.allocator.Get(), nullptr,
                                        IID_PPV_ARGS(&buffer.copy)), "CreateCommandList");
            D3D12_RESOURCE_BARRIER barriers[2]{};
            barriers[0].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            barriers[0].Transition.pResource = buffer.back.Get();
            barriers[0].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            barriers[0].Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
            barriers[0].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_DEST;
            barriers[1].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            barriers[1].Transition.pResource = buffer.shared.Get();
            barriers[1].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            barriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            barriers[1].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
            buffer.copy->ResourceBarrier(2, barriers);
            buffer.copy->CopyResource(buffer.back.Get(), buffer.shared.Get());
            for (D3D12_RESOURCE_BARRIER &barrier: barriers) {
                std::swap(barrier.Transition.StateBefore, barrier.Transition.StateAfter);
            }
            buffer.copy->ResourceBarrier(2, barriers);
            hr(buffer.copy->Close(), "Close");
        }
        log::info("[dxgi] %ux%u, %u buffers, %s", width, height, BUFFER_COUNT, s.direct ? "drawing into the back buffers" : "copying each frame");
        return true;
    }

    int DxgiPresenter::acquire(VkSemaphore) {
        State &s = *state;
        if (!s.threaded) return static_cast<int>(s.swapchain->GetCurrentBackBufferIndex());
        return static_cast<int>(s.nextIndex);
    }

    void DxgiPresenter::sync(SubmitSync &sync, VkCommandBuffer commands, u32 index, bool first, bool presenting) {
        State &s = *state;
        sync.timeline = true;
        u64 copiedAt = s.buffers[index].copiedAt;
        if (first && s.copiedFence->GetCompletedValue() < copiedAt) {
            sync.wait(s.copied, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                      copiedAt);
        }
        if (presenting) {
            VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT |
                                    VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.srcQueueFamilyIndex = device.queueFamily;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL_KHR;
            barrier.image = images[index];
            barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                                 VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
            sync.signal(s.rendered, ++s.renderedValue);
        }
    }

    bool DxgiPresenter::present(u32 index) {
        State &s = *state;
        State::Job job;
        job.index = index;
        job.rendered = s.renderedValue;
        job.copied = ++s.copiedValue;
        job.vsync = s.vsync.load(std::memory_order_relaxed);
        // Copying: the buffer is free again once its own copy ran. Direct: this Present frees the buffer shown before it.
        s.buffers[s.direct ? (index + BUFFER_COUNT - 1) % BUFFER_COUNT : index].copiedAt = job.copied;
        if (!s.threaded) return presentNow(s, job);
        std::string error;
        {
            std::unique_lock lock(s.mutex);
            s.done.wait(lock, [&s] { return !s.pending; });
            error.swap(s.error);
            s.job = job;
            s.pending = true;
        }
        s.wake.notify_one();
        if (!error.empty()) fail(error);
        s.nextIndex = (index + 1) % BUFFER_COUNT;
        // A frame that could not be shown is reported one frame late; the surface is then made again.
        return !s.failed.exchange(false, std::memory_order_relaxed);
    }

    bool DxgiPresenter::setVsync(bool enabled) {
        State &s = *state;
        s.vsync = enabled;
        // Drawing into the back buffers is only done without vsync, so that choice needs the surface made again.
        return (s.wantDirect && !enabled && s.tearing) != s.direct;
    }
} // namespace at

#endif
