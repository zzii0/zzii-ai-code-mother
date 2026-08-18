package com.nylg.zziiaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.validator.NativeArtifactFixOrchestrator;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.exception.AiStreamErrors;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器（HTML / MULTI_FILE）。
 * 透传 AI 文本 chunk 给前端，流结束后：
 *   1) 写入 chat_history
 *   2) 校验产物 → 定向补生成 → 保存（{@link NativeArtifactFixOrchestrator}）
 * 用户停止：只保存部分对话，不触发校验与保存。
 */
@Component
@Slf4j
public class SimpleTextStreamHandler {

    @Resource
    private NativeArtifactFixOrchestrator nativeArtifactFixOrchestrator;

    /**
     * 处理 HTML / MULTI_FILE 的 SSE 文本流。
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               User loginUser,
                               CodeGenTypeEnum codeGenType,
                               String userMessage,
                               AiGenerationTask generationTask) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .concatWith(Flux.defer(() -> afterAiGeneration(
                        chatHistoryService, appId, loginUser, codeGenType, userMessage,
                        aiResponseBuilder, generationTask)))
                .doOnCancel(() -> savePartialAiMessage(
                        chatHistoryService, appId, loginUser, aiResponseBuilder, generationTask))
                .doOnError(error -> {
                    String errorMessage = AiStreamErrors.userMessage(error);
                    chatHistoryService.addChatMessage(
                            appId, "AI回复失败: " + errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    private Flux<String> afterAiGeneration(ChatHistoryService chatHistoryService,
                                           long appId,
                                           User loginUser,
                                           CodeGenTypeEnum codeGenType,
                                           String userMessage,
                                           StringBuilder aiResponseBuilder,
                                           AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
            return Flux.empty();
        }
        String aiResponse = aiResponseBuilder.toString();
        chatHistoryService.addChatMessage(
                appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        return nativeArtifactFixOrchestrator.runAfterGeneration(
                aiResponse, codeGenType, appId, loginUser, userMessage, generationTask);
    }

    private void savePartialAiMessage(ChatHistoryService chatHistoryService,
                                      long appId,
                                      User loginUser,
                                      StringBuilder aiResponseBuilder,
                                      AiGenerationTask generationTask) {
        if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
            return;
        }
        String aiResponse = aiResponseBuilder.toString();
        if (StrUtil.isBlank(aiResponse)) {
            aiResponse = "（已停止生成）";
        }
        chatHistoryService.addChatMessage(
                appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        log.info("AI 生成已停止，已保存部分对话内容，appId={}", appId);
    }
}
