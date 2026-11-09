#include "core/prof.h"
#include "vulkan/frame.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    FrameResources::FrameResources(const Device &device) : device(device) {
        VkCommandPoolCreateInfo poolInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        poolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
        poolInfo.queueFamilyIndex = device.queueFamily;
        check(vkCreateCommandPool(device.handle, &poolInfo, nullptr, &pool), "vkCreateCommandPool");

        VkCommandBufferAllocateInfo bufferInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        bufferInfo.commandPool = pool;
        bufferInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        bufferInfo.commandBufferCount = 2;
        VkCommandBuffer buffers[2];
        check(vkAllocateCommandBuffers(device.handle, &bufferInfo, buffers), "vkAllocateCommandBuffers");
        setup = buffers[0];
        main = buffers[1];

        VkFenceCreateInfo fenceInfo{.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        check(vkCreateFence(device.handle, &fenceInfo, nullptr, &fence), "vkCreateFence");

        VkSemaphoreCreateInfo semaphoreInfo{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        check(vkCreateSemaphore(device.handle, &semaphoreInfo, nullptr, &imageAvailable), "vkCreateSemaphore");

        if (device.transferQueue) {
            VkCommandPoolCreateInfo transferPoolInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
            transferPoolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
            transferPoolInfo.queueFamilyIndex = device.transferFamily;
            check(vkCreateCommandPool(device.handle, &transferPoolInfo, nullptr, &transferPool), "vkCreateCommandPool");
            VkCommandBufferAllocateInfo transferInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
            transferInfo.commandPool = transferPool;
            transferInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
            transferInfo.commandBufferCount = 1;
            check(vkAllocateCommandBuffers(device.handle, &transferInfo, &transfer), "vkAllocateCommandBuffers");
            VkSemaphoreCreateInfo doneInfo{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
            check(vkCreateSemaphore(device.handle, &doneInfo, nullptr, &transferDone), "vkCreateSemaphore");
        }

        if (device.timestampMask) {
            VkQueryPoolCreateInfo queryInfo{.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO};
            queryInfo.queryType = VK_QUERY_TYPE_TIMESTAMP;
            queryInfo.queryCount = 2;
            check(vkCreateQueryPool(device.handle, &queryInfo, nullptr, &timestamps), "vkCreateQueryPool");
            if (prof::enabled) {
                queryInfo.queryCount = ZONE_CAPACITY;
                check(vkCreateQueryPool(device.handle, &queryInfo, nullptr, &zones), "vkCreateQueryPool");
            }
        }
    }

    FrameResources::~FrameResources() {
        runReleases();
        if (timestamps) vkDestroyQueryPool(device.handle, timestamps, nullptr);
        if (zones) vkDestroyQueryPool(device.handle, zones, nullptr);
        if (transferDone) vkDestroySemaphore(device.handle, transferDone, nullptr);
        if (transferPool) vkDestroyCommandPool(device.handle, transferPool, nullptr);
        vkDestroySemaphore(device.handle, imageAvailable, nullptr);
        vkDestroyFence(device.handle, fence, nullptr);
        vkDestroyCommandPool(device.handle, pool, nullptr);
    }

    void FrameResources::await() {
        check(vkWaitForFences(device.handle, 1, &fence, VK_TRUE, ~0ull), "vkWaitForFences");
        if (prof::enabled && submitNanos) {
            prof::total[prof::SUBMIT_TO_DONE] += prof::now() - submitNanos;
            prof::calls[prof::SUBMIT_TO_DONE]++;
            submitNanos = 0;
        }
        if (timed) {
            timed = false;
            u64 values[2];
            if (vkGetQueryPoolResults(device.handle, timestamps, 0, 2, sizeof(values), values, sizeof(u64),
                                      VK_QUERY_RESULT_64_BIT) == VK_SUCCESS) {
                u64 ticks = (values[1] - values[0]) & device.timestampMask;
                static u64 previousEnd = 0;
                if (prof::enabled && previousEnd && values[0] > previousEnd) {
                    prof::total[prof::GPU_GAP] += static_cast<u64>(static_cast<double>(values[0] - previousEnd) * device.timestampPeriod);
                    prof::calls[prof::GPU_GAP]++;
                }
                previousEnd = values[1];
                u64 nanos = static_cast<u64>(static_cast<double>(ticks) * device.timestampPeriod);
                device.gpuNanos += nanos;
                if (prof::enabled) {
                    prof::total[prof::GPU] += nanos;
                    prof::calls[prof::GPU]++;
                }
            }
        }
        if (zoned) {
            zoned = false;
            u64 values[ZONE_CAPACITY];
            if (zoneCount > 1 && vkGetQueryPoolResults(device.handle, zones, 0, zoneCount, sizeof(u64) * zoneCount, values,
                                                       sizeof(u64), VK_QUERY_RESULT_64_BIT) == VK_SUCCESS) {
                for (u32 i = 1; i < zoneCount; i++) {
                    u64 ticks = (values[i] - values[i - 1]) & device.timestampMask;
                    int bucket = prof::ZONE + zoneKinds[i];
                    prof::total[bucket] += static_cast<u64>(static_cast<double>(ticks) * device.timestampPeriod);
                    prof::calls[bucket]++;
                }
            }
        }
        runReleases();
    }

    void FrameResources::reset() const {
        check(vkResetFences(device.handle, 1, &fence), "vkResetFences");
        check(vkResetCommandPool(device.handle, pool, 0), "vkResetCommandPool");
        if (transferPool) check(vkResetCommandPool(device.handle, transferPool, 0), "vkResetCommandPool");
    }

    void FrameResources::runReleases() {
        std::vector<std::function<void()> > pending;
        pending.swap(releases);
        for (auto &release: pending) release();
    }

    FrameStream::FrameStream(const Device &device, MemoryAllocator &allocator, u64 frameSize, u32 frames,
                             VkBufferUsageFlags usage, u64 slack)
        : frameSize(frameSize) {
        buffer = std::make_unique<GpuBuffer>(device, allocator, frameSize * frames + slack, usage,
                                             VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                                             VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        base = buffer->mapped();
    }
} // namespace at
