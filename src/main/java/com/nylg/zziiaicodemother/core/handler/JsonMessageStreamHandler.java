package com.nylg.zziiaicodemother.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nylg.zziiaicodemother.ai.model.message.*;
import com.nylg.zziiaicodemother.ai.tools.BaseTool;
import com.nylg.zziiaicodemother.ai.tools.ToolManager;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.core.builder.VueProjectBuilder;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.model.entity.User;
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
 *   正常完成：写入完整对话历史，并异步 build Vue 项目
 *   用户停止：只保存部分对话，不触发 npm build
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式。
     * 停止时：只保存部分对话历史，不触发 vueProjectBuilder 构建。
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser,
                               AiGenerationTask generationTask) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 已停止：部分历史由 doOnCancel 写入，此处不写完整历史、也不 build
                    if (generationTask != null && generationTask.isCancelled()) {
                        return;
                    }
                    // 与 doOnCancel 互斥，避免重复落库
                    if (generationTask != null && !generationTask.tryMarkHistoryPersisted()) {
                        return;
                    }
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    // 仅正常完成时异步构建 Vue 项目
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ "/vue_project_" + appId;
                    vueProjectBuilder.buildProjectAsync(projectPath);
                })
                // 用户停止或 SSE 断开：保存部分对话，跳过 build
                .doOnCancel(() -> savePartialAiMessage(
                        chatHistoryService, appId, loginUser, chatHistoryStringBuilder, generationTask))
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
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

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    //根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    //返回格式化后的工具信息
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String toolName = toolExecutedMessage.getName();
                // 根据工具名称获取工具实例
                BaseTool tool = toolManager.getTool(toolName);
                // 执行工具并获取结果
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
