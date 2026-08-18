package com.nylg.zziiaicodemother.core;

import cn.hutool.json.JSONUtil;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorService;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import com.nylg.zziiaicodemother.ai.model.message.AiResponseMessage;
import com.nylg.zziiaicodemother.ai.model.message.ToolExecutedMessage;
import com.nylg.zziiaicodemother.ai.model.message.ToolRequestMessage;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.validator.NativeArtifactFixOrchestrator;
import com.nylg.zziiaicodemother.exception.AiStreamErrors;
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

import java.io.File;

/**
 * 代码生成器外观类。
 * <p>
 * 流式路径（{@link #generateAndSaveCodeStream}）支持「手动停止」：
 * 通过 {@link AiGenerationTask} 感知取消，停止向前端推流。
 * HTML / MULTI_FILE 的校验、补生成与保存由 {@link NativeArtifactFixOrchestrator} 在流结束后执行。
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private NativeArtifactFixOrchestrator nativeArtifactFixOrchestrator;

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
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield nativeArtifactFixOrchestrator.prepareAndSaveParsed(
                        htmlCodeResult, CodeGenTypeEnum.HTML, appId, userMessage);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield nativeArtifactFixOrchestrator.prepareAndSaveParsed(
                        multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE, appId, userMessage);
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
        // HTML / MULTI_FILE：只推流；校验与保存由 StreamHandler 在结束后调用编排器
        return processCodeStream(contentFlux, generationTask);
    }

    /**
     * 将 TokenStream 转换为 Flux（Vue 工程模式）。
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, AiGenerationTask generationTask) {
        return Flux.create(sink -> {
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
                        sink.error(AiStreamErrors.toBusinessException(error));
                    })
                    .start();
            sink.onCancel(() -> {
                if (generationTask != null) {
                    generationTask.cancel();
                }
            });
        });
    }

    /**
     * HTML / MULTI_FILE 展示流：实时推 chunk；落盘与校验由 SimpleTextStreamHandler 触发。
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, AiGenerationTask generationTask) {
        return attachGenerationLifecycle(
                codeStream
                        .takeWhile(chunk -> generationTask == null || !generationTask.isCancelled())
                        .doOnCancel(() -> log.info("AI 代码流已取消")),
                generationTask);
    }

    private Flux<String> attachGenerationLifecycle(Flux<String> flux, AiGenerationTask generationTask) {
        if (generationTask == null) {
            return flux;
        }
        return flux.doOnSubscribe(generationTask::bindSubscription);
    }
}
