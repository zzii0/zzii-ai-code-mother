package com.nylg.zziiaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器（HTML / MULTI_FILE）。
 * 透传 AI 文本 chunk 给前端，并在流结束时写入 chat_history。
 * 与「停止生成」配合：
 *   正常完成 → doOnComplete 保存完整 AI 回复
 *   用户停止 → doOnCancel 保存已收到的部分内容
 *   代码文件是否保存由 {@code AiCodeGeneratorFacade} 根据 cancelled 决定
 */
@Component
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理 HTML / MULTI_FILE 的 SSE 文本流。
     *
     * @param generationTask 生成任务上下文；停止时用于判断是否跳过完整历史写入、以及防重复落库
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               User loginUser,
                               AiGenerationTask generationTask) {
        // 边推流边拼接，供结束或停止时写入对话历史
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 用户已停止：部分历史由 doOnCancel 写入，这里直接返回
                    if (generationTask != null && generationTask.isCancelled()) {
                        return;
                    }
                    // 防止与 doOnCancel 竞态导致重复写入同一条 AI 消息
                    if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
                        return;
                    }
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(
                            appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                // subscription.cancel() 时触发（含用户点击「停止」）
                .doOnCancel(() -> savePartialAiMessage(
                        chatHistoryService, appId, loginUser, aiResponseBuilder, generationTask))
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(
                            appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 停止生成时保存部分 AI 回复到对话历史。
     * 这样用户下次进入聊天页仍能看到停止前已生成的内容。
     */
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
