package com.nylg.zziiaicodemother.core.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进行中的 AI 生成任务注册表（内存级，单机有效）。
 * <p>
 * 职责：把「HTTP 停止请求」和「正在运行的 SSE Flux」关联起来。
 * Key 为 appId:userId，同一用户对同一应用同时只保留一个生成任务。
 */
@Slf4j
@Component
public class AiGenerationTaskRegistry {

    private final ConcurrentHashMap<String, AiGenerationTask> tasks = new ConcurrentHashMap<>();

    /**
     * 注册新的生成任务。若该用户在该应用下已有进行中的任务，会先停止旧任务。
     * 在 AppServiceImpl.chatToGenCode 调用 AI 之前执行。
     */
    public AiGenerationTask register(Long appId, Long userId) {
        String key = buildKey(appId, userId);
        AiGenerationTask existingTask = tasks.get(key);
        if (existingTask != null) {
            existingTask.cancel();
        }
        AiGenerationTask task = new AiGenerationTask(appId, userId);
        tasks.put(key, task);
        return task;
    }

    public AiGenerationTask get(Long appId, Long userId) {
        return tasks.get(buildKey(appId, userId));
    }

    /**
     * 停止指定用户在某应用下的生成任务。
     * 由 POST /app/chat/gen/stop 触发。
     *
     * @return true 表示找到任务并已触发 cancel；false 表示没有进行中的任务
     */
    public boolean cancel(Long appId, Long userId) {
        AiGenerationTask task = tasks.get(buildKey(appId, userId));
        if (task == null) {
            return false;
        }
        task.cancel();
        log.info("已请求停止 AI 生成，appId={}, userId={}", appId, userId);
        return true;
    }

    /**
     * 生成结束（正常完成或被取消）后清理任务，避免 Map 泄漏。
     */
    public void remove(Long appId, Long userId) {
        tasks.remove(buildKey(appId, userId));
    }

    private String buildKey(Long appId, Long userId) {
        return appId + ":" + userId;
    }
}
