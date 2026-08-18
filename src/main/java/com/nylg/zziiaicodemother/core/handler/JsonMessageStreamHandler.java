package com.nylg.zziiaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.core.builder.VueBuildFixOrchestrator;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.exception.AiStreamErrors;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器。
 * 处理 VUE_PROJECT 类型的复杂流式响应（含工具调用信息）。
 * 「停止生成」相关：
 *   正常完成：写入完整对话历史，并进入构建/自动修复两阶段 SSE
 *   用户停止：只保存部分对话，不触发 npm build
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueBuildFixOrchestrator vueBuildFixOrchestrator;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式。
     * 停止时：只保存部分对话历史，不触发 vueProjectBuilder 构建。
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser,
                               AiGenerationTask generationTask) {
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return vueBuildFixOrchestrator.transformVueJsonChunks(originFlux, chatHistoryStringBuilder, seenToolIds)
                .concatWith(Flux.defer(() -> afterAiGeneration(
                        chatHistoryService, appId, loginUser, chatHistoryStringBuilder, generationTask)))
                .doOnCancel(() -> savePartialAiMessage(
                        chatHistoryService, appId, loginUser, chatHistoryStringBuilder, generationTask))
                .doOnError(error -> {
                    String errorMessage = AiStreamErrors.userMessage(error);
                    chatHistoryService.addChatMessage(appId, "AI回复失败: " + errorMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    private Flux<String> afterAiGeneration(ChatHistoryService chatHistoryService,
                                           long appId,
                                           User loginUser,
                                           StringBuilder chatHistoryStringBuilder,
                                           AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
            return Flux.empty();
        }
        String aiResponse = chatHistoryStringBuilder.toString();
        chatHistoryService.addChatMessage(appId, aiResponse,
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        return vueBuildFixOrchestrator.runAfterGeneration(appId, loginUser, generationTask);
    }

    /** Vue 模式：停止时保存部分 AI 回复（含工具调用文本），不触发 npm build */
    private void savePartialAiMessage(ChatHistoryService chatHistoryService,
                                     long appId,
                                     User loginUser,
                                     StringBuilder chatHistoryStringBuilder,
                                     AiGenerationTask generationTask) {
        if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
            return;
        }
        String aiResponse = chatHistoryStringBuilder.toString();
        if (StrUtil.isBlank(aiResponse)) {
            aiResponse = "（已停止生成）";
        }
        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        log.info("Vue 项目 AI 生成已停止，已保存部分对话内容，appId={}", appId);
    }
}
