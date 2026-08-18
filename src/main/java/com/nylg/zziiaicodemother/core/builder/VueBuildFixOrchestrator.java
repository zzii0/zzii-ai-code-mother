package com.nylg.zziiaicodemother.core.builder;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nylg.zziiaicodemother.ai.tools.BaseTool;
import com.nylg.zziiaicodemother.ai.tools.ToolManager;
import com.nylg.zziiaicodemother.ai.model.message.*;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.core.AiCodeGeneratorFacade;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.sse.SseEventCodec;
import com.nylg.zziiaicodemother.exception.AiStreamErrors;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Vue 工程两阶段构建编排：生成完成后构建，失败则定位文件并驱动 AI 修复后重试。
 */
@Slf4j
@Component
public class VueBuildFixOrchestrator {

    private static final int MAX_BUILD_ATTEMPTS = 3;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 代码流结束后执行：generation_done → build → (fail → fix → build)* → success / give_up
     */
    public Flux<String> runAfterGeneration(long appId, User loginUser, AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
        return Flux.concat(
                Flux.just(
                        SseEventCodec.encode(SseEventCodec.GENERATION_DONE, Map.of("appId", appId)),
                        "\n\n⚙️ 代码已生成，开始构建预览...\n"
                ),
                buildWithRetry(appId, projectPath, loginUser, generationTask, 1)
        );
    }

    private Flux<String> buildWithRetry(long appId,
                                        String projectPath,
                                        User loginUser,
                                        AiGenerationTask generationTask,
                                        int attempt) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        Map<String, Object> startPayload = new HashMap<>();
        startPayload.put("attempt", attempt);
        startPayload.put("maxAttempts", MAX_BUILD_ATTEMPTS);

        return Flux.concat(
                Flux.just(SseEventCodec.encode(SseEventCodec.BUILD_START, startPayload)),
                Mono.fromCallable(() -> vueProjectBuilder.buildProjectWithResult(projectPath))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(result -> handleBuildResult(
                                appId, projectPath, loginUser, generationTask, attempt, result))
        );
    }

    private Flux<String> handleBuildResult(long appId,
                                           String projectPath,
                                           User loginUser,
                                           AiGenerationTask generationTask,
                                           int attempt,
                                           BuildResult result) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        if (result.isSuccess()) {
            Map<String, Object> payload = Map.of("attempt", attempt);
            String successText = "\n\n✅ 构建成功，预览已就绪。\n";
            chatHistoryService.addChatMessage(appId, successText.trim(),
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            return Flux.just(
                    SseEventCodec.encode(SseEventCodec.BUILD_SUCCESS, payload),
                    successText
            );
        }

        Map<String, Object> failPayload = buildFailPayload(result, attempt);
        String failText = formatBuildFailedText(result, attempt);

        if (attempt >= MAX_BUILD_ATTEMPTS) {
            Map<String, Object> giveUpPayload = new HashMap<>(failPayload);
            giveUpPayload.put("attempts", attempt);
            String giveUpText = "\n\n🚫 已尝试 " + attempt + " 次构建仍失败，请根据错误信息手动描述修复需求。\n";
            chatHistoryService.addChatMessage(appId, failText + giveUpText,
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            return Flux.just(
                    SseEventCodec.encode(SseEventCodec.BUILD_FAILED, failPayload),
                    failText,
                    SseEventCodec.encode(SseEventCodec.BUILD_GIVE_UP, giveUpPayload),
                    giveUpText
            );
        }

        Map<String, Object> fixingPayload = new HashMap<>();
        fixingPayload.put("attempt", attempt);
        fixingPayload.put("errorFile", StrUtil.nullToDefault(result.getErrorFile(), ""));
        fixingPayload.put("errorMessage", StrUtil.nullToDefault(result.getErrorMessage(), ""));

        String fixingText = formatFixingText(result, attempt);
        String fixPrompt = buildFixPrompt(result);
        chatHistoryService.addChatMessage(appId, failText + fixingText,
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());

        Flux<String> fixStream = runAiFixStream(appId, loginUser, generationTask, fixPrompt);

        return Flux.concat(
                Flux.just(
                        SseEventCodec.encode(SseEventCodec.BUILD_FAILED, failPayload),
                        failText,
                        SseEventCodec.encode(SseEventCodec.BUILD_FIXING, fixingPayload),
                        fixingText
                ),
                fixStream,
                buildWithRetry(appId, projectPath, loginUser, generationTask, attempt + 1)
        );
    }

    private Flux<String> runAiFixStream(long appId,
                                        User loginUser,
                                        AiGenerationTask generationTask,
                                        String fixPrompt) {
        StringBuilder fixHistory = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        // 修复轮次不绑定 generationTask 的 Subscription，避免覆盖外层 SSE 的停止句柄；
        // 仍通过 takeWhile 感知用户取消。
        Flux<String> rawFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                fixPrompt, CodeGenTypeEnum.VUE_PROJECT, appId, null);
        return transformVueJsonChunks(rawFlux, fixHistory, seenToolIds)
                .takeWhile(chunk -> generationTask == null || !generationTask.isCancelled())
                .doOnComplete(() -> {
                    if (generationTask != null && generationTask.isCancelled()) {
                        return;
                    }
                    String content = fixHistory.toString();
                    if (StrUtil.isNotBlank(content)) {
                        chatHistoryService.addChatMessage(appId, content,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .onErrorResume(error -> {
                    log.error("构建修复 AI 调用失败: {}", error.getMessage(), error);
                    String msg = "\n\n⚠️ 自动修复过程出错: " + AiStreamErrors.userMessage(error) + "\n";
                    return Flux.just(msg);
                });
    }

    /**
     * 将 Vue TokenStream JSON 块转为前端可读文本（与 JsonMessageStreamHandler 一致）。
     */
    public Flux<String> transformVueJsonChunks(Flux<String> originFlux,
                                               StringBuilder chatHistoryStringBuilder,
                                               Set<String> seenToolIds) {
        return originFlux
                .map(chunk -> handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds))
                .filter(StrUtil::isNotEmpty);
    }

    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            return "";
        }
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    seenToolIds.add(toolId);
                    BaseTool tool = toolManager.getTool(toolName);
                    return tool.generateToolRequestResponse();
                }
                return "";
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                return "";
            }
        }
    }

    private static Map<String, Object> buildFailPayload(BuildResult result, int attempt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attempt", attempt);
        payload.put("maxAttempts", MAX_BUILD_ATTEMPTS);
        payload.put("errorFile", StrUtil.nullToDefault(result.getErrorFile(), ""));
        payload.put("errorLine", result.getErrorLine() == null ? "" : result.getErrorLine());
        payload.put("errorMessage", StrUtil.nullToDefault(result.getErrorMessage(), "未知构建错误"));
        return payload;
    }

    private static String formatBuildFailedText(BuildResult result, int attempt) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n❌ 构建失败（第 ").append(attempt).append(" 次）");
        if (StrUtil.isNotBlank(result.getErrorFile())) {
            sb.append("\n- 文件: ").append(result.getErrorFile());
        }
        if (result.getErrorLine() != null) {
            sb.append("\n- 行号: ").append(result.getErrorLine());
        }
        sb.append("\n- 错误: ").append(StrUtil.nullToDefault(result.getErrorMessage(), "未知错误"));
        sb.append("\n");
        return sb.toString();
    }

    private static String formatFixingText(BuildResult result, int attempt) {
        String file = StrUtil.blankToDefault(result.getErrorFile(), "相关文件");
        return "\n\n🔧 正在自动修复 " + file + "（第 " + attempt + "/" + MAX_BUILD_ATTEMPTS + " 次尝试）...\n";
    }

    static String buildFixPrompt(BuildResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vue 项目执行 `npm run build` 失败，请只修复导致构建失败的文件，不要改动无关文件，不要重新生成整个项目。\n");
        if (StrUtil.isNotBlank(result.getErrorFile())) {
            sb.append("- 出错文件: ").append(result.getErrorFile()).append("\n");
        }
        if (result.getErrorLine() != null) {
            sb.append("- 出错行号: ").append(result.getErrorLine()).append("\n");
        }
        sb.append("- 错误信息: ").append(StrUtil.nullToDefault(result.getErrorMessage(), "未知错误")).append("\n");
        if (StrUtil.isNotBlank(result.getOutput())) {
            String output = result.getOutput();
            int max = 2500;
            if (output.length() > max) {
                output = output.substring(Math.max(0, output.length() - max));
            }
            sb.append("\n构建完整输出（末尾片段）：\n```\n").append(output).append("\n```\n");
        }
        sb.append("\n请先用 readFile 查看出错文件，再用 modifyFile 或 writeFile 修复，最后调用 exit 结束。");
        return sb.toString();
    }
}
