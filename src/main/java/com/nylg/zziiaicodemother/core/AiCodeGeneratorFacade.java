package com.nylg.zziiaicodemother.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorService;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import com.nylg.zziiaicodemother.ai.model.message.AiResponseMessage;
import com.nylg.zziiaicodemother.ai.model.message.ToolExecutedMessage;
import com.nylg.zziiaicodemother.ai.model.message.ToolRequestMessage;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.parser.CodeParserExecutor;
import com.nylg.zziiaicodemother.core.saver.CodeFileSaverExecutor;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;

/**
 * 代码生成器外观类。
 * <p>
 * 流式路径（{@link #generateAndSaveCodeStream}）支持「手动停止」：
 * 通过 {@link AiGenerationTask} 感知取消，停止向前端推流，并跳过异步代码落盘。
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    private static final String MULTI_FILE_RETRY_PROMPT_TEMPLATE = """
            请根据以下需求重新生成完整的多文件网站代码。
            必须严格输出恰好 3 个 Markdown 代码块，顺序为：
            1. ```html （完整 index.html）
            2. ```css （完整 style.css）
            3. ```javascript （完整 script.js）
            禁止省略 CSS 或 JavaScript，禁止用说明文字替代代码，禁止只输出 HTML。
            
            原始需求：
            %s
            """;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        //根据appId创建AI服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                multiFileCodeResult = ensureCompleteMultiFileResult(multiFileCodeResult, userMessage, aiCodeGeneratorService);
                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 id
     * @param generationTask  生成任务上下文，用于支持手动停止；LangGraph 等内部调用可传 null
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage,
                                                  CodeGenTypeEnum codeGenTypeEnum,
                                                  Long appId,
                                                  AiGenerationTask generationTask) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        Flux<String> contentFlux = switch (codeGenTypeEnum) {
            case HTML -> aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
            case VUE_PROJECT -> processTokenStream(
                    aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage), generationTask);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return attachGenerationLifecycle(contentFlux, generationTask);
        }
        return processCodeStream(contentFlux, codeGenTypeEnum, appId, userMessage, aiCodeGeneratorService, generationTask);
    }

    /**
     * 将 TokenStream 转换为 Flux（Vue 工程模式）。
     * <p>
     * 停止策略：每个回调先检查 generationTask.isCancelled()，为 true 则结束 sink；
     * 客户端断开连接时 sink.onCancel 会反向标记任务取消。
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, AiGenerationTask generationTask) {
        return Flux.create(sink -> {
            // 停止后不再向 SSE 推送，但上游 TokenStream 可能仍在跑（LangChain4j 1.1 限制）
            Runnable stopStreamingToClient = () -> {
                if (!sink.isCancelled()) {
                    sink.complete();
                }
            };
            tokenStream.onPartialResponse((String partialResponse) -> {
                        if (generationTask != null && generationTask.isCancelled()) {
                            stopStreamingToClient.run();
                            return;
                        }
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        if (generationTask != null && generationTask.isCancelled()) {
                            stopStreamingToClient.run();
                            return;
                        }
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        if (generationTask != null && generationTask.isCancelled()) {
                            stopStreamingToClient.run();
                            return;
                        }
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (generationTask != null && generationTask.isCancelled()) {
                            stopStreamingToClient.run();
                            return;
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        if (generationTask != null && generationTask.isCancelled()) {
                            stopStreamingToClient.run();
                            return;
                        }
                        sink.error(error);
                    })
                    .start();
            // 前端关闭 EventSource 或 subscription.cancel() 时同步标记任务取消
            sink.onCancel(() -> {
                if (generationTask != null) {
                    generationTask.cancel();
                }
            });
        });
    }


    /**
     * 构建展示流（HTML / MULTI_FILE）：实时推 chunk 给前端，流结束后再异步保存代码。
     * <p>
     * 停止相关逻辑：
     * - takeWhile：取消后不再收集新 chunk
     * - doOnComplete：已取消则跳过 scheduleAsyncCodeSave
     */
    private Flux<String> processCodeStream(Flux<String> codeStream,
                                           CodeGenTypeEnum codeGenTypeEnum,
                                           Long appId,
                                           String userMessage,
                                           AiCodeGeneratorService aiCodeGeneratorService,
                                           AiGenerationTask generationTask) {
        StringBuilder stringBuilder = new StringBuilder();
        return attachGenerationLifecycle(
                codeStream
                        // 用户停止后不再向下游传递新的 AI 输出片段
                        .takeWhile(chunk -> generationTask == null || !generationTask.isCancelled())
                        .doOnNext(stringBuilder::append)
                        .doOnComplete(() -> {
                            // 手动停止时不保存代码文件（保留停止前的版本）
                            if (generationTask != null && generationTask.isCancelled()) {
                                log.info("用户已停止生成，跳过代码保存，appId={}", appId);
                                return;
                            }
                            scheduleAsyncCodeSave(
                                    stringBuilder.toString(),
                                    codeGenTypeEnum,
                                    appId,
                                    userMessage,
                                    aiCodeGeneratorService,
                                    generationTask);
                        })
                        .doOnCancel(() -> log.info("AI 代码流已取消，appId={}", appId)),
                generationTask);
    }

    /** 订阅建立时绑定 Subscription，供停止 API 调用 task.cancel() 中断 SSE */
    private Flux<String> attachGenerationLifecycle(Flux<String> flux, AiGenerationTask generationTask) {
        if (generationTask == null) {
            return flux;
        }
        return flux.doOnSubscribe(generationTask::bindSubscription);
    }

    /**
     * 异步保存代码，避免阻塞 SSE 流式输出。
     */
    private void scheduleAsyncCodeSave(String completeCode,
                                       CodeGenTypeEnum codeGenTypeEnum,
                                       Long appId,
                                       String userMessage,
                                       AiCodeGeneratorService aiCodeGeneratorService,
                                       AiGenerationTask generationTask) {
        Mono.fromRunnable(() -> saveParsedCode(
                        completeCode,
                        codeGenTypeEnum,
                        appId,
                        userMessage,
                        aiCodeGeneratorService,
                        generationTask))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("代码异步保存任务失败，appId={}", appId, error)
                );
    }

    /**
     * 解析并在必要时重试后保存代码（在后台线程执行）。
     * 停止生成后不应写入半成品，因此在入口再次检查 cancelled。
     */
    private void saveParsedCode(String completeCode,
                                CodeGenTypeEnum codeGenTypeEnum,
                                Long appId,
                                String userMessage,
                                AiCodeGeneratorService aiCodeGeneratorService,
                                AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            log.info("用户已停止生成，跳过代码保存，appId={}", appId);
            return;
        }
        try {
            Object parserResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
            if (codeGenTypeEnum == CodeGenTypeEnum.MULTI_FILE) {
                parserResult = ensureCompleteMultiFileResult(
                        (MultiFileCodeResult) parserResult,
                        userMessage,
                        aiCodeGeneratorService);
            }
            File file = CodeFileSaverExecutor.executeSaver(parserResult, codeGenTypeEnum, appId);
            log.info("代码异步保存成功，路径为：{}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("代码异步保存失败，appId={}", appId, e);
        }
    }

    /**
     * 确保多文件结果完整；不完整时自动重试一次生成
     */
    private MultiFileCodeResult ensureCompleteMultiFileResult(MultiFileCodeResult firstResult,
                                                               String userMessage,
                                                               AiCodeGeneratorService aiCodeGeneratorService) {
        if (isCompleteMultiFileResult(firstResult)) {
            return firstResult;
        }
        log.warn("多文件代码不完整（html={}, css={}, js={}），触发自动重试一次",
                hasCode(firstResult != null ? firstResult.getHtmlCode() : null),
                hasCode(firstResult != null ? firstResult.getCssCode() : null),
                hasCode(firstResult != null ? firstResult.getJsCode() : null));

        String retryPrompt = String.format(MULTI_FILE_RETRY_PROMPT_TEMPLATE, userMessage);
        // 重试使用同步结构化输出，提高三文件齐全概率
        MultiFileCodeResult retryResult = aiCodeGeneratorService.generateMultiFileCode(retryPrompt);
        if (!isCompleteMultiFileResult(retryResult)) {
            // 同步结果可能仍是结构化字段，也可能是模型把 markdown 塞进某个字段；再解析一次兜底
            retryResult = tryRecoverFromEmbeddedMarkdown(retryResult);
        }
        if (!isCompleteMultiFileResult(retryResult)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "多文件代码生成不完整，缺少 CSS 或 JavaScript，请重新生成");
        }
        log.info("多文件代码自动重试成功，已补齐三文件");
        return retryResult;
    }

    /**
     * 若结构化字段里嵌了 markdown，尝试再解析一次
     */
    private MultiFileCodeResult tryRecoverFromEmbeddedMarkdown(MultiFileCodeResult result) {
        if (result == null) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        if (StrUtil.isNotBlank(result.getHtmlCode())) {
            content.append(result.getHtmlCode()).append('\n');
        }
        if (StrUtil.isNotBlank(result.getCssCode())) {
            content.append(result.getCssCode()).append('\n');
        }
        if (StrUtil.isNotBlank(result.getJsCode())) {
            content.append(result.getJsCode()).append('\n');
        }
        if (StrUtil.isNotBlank(result.getDescription())) {
            content.append(result.getDescription()).append('\n');
        }
        if (content.isEmpty()) {
            return result;
        }
        Object parsed = CodeParserExecutor.executeParser(content.toString(), CodeGenTypeEnum.MULTI_FILE);
        if (parsed instanceof MultiFileCodeResult recovered && isCompleteMultiFileResult(recovered)) {
            return recovered;
        }
        return result;
    }

    private static boolean isCompleteMultiFileResult(MultiFileCodeResult result) {
        return result != null
                && StrUtil.isNotBlank(result.getHtmlCode())
                && StrUtil.isNotBlank(result.getCssCode())
                && StrUtil.isNotBlank(result.getJsCode());
    }

    private static boolean hasCode(String code) {
        return StrUtil.isNotBlank(code);
    }
}
