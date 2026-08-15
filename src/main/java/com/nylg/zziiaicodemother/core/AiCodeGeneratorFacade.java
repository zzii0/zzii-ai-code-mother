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
 * 代码生成器外观类
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
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        //根据appId创建AI服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result, CodeGenTypeEnum.HTML, appId, userMessage, aiCodeGeneratorService);
            }
            case MULTI_FILE -> {
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(result, CodeGenTypeEnum.MULTI_FILE, appId, userMessage, aiCodeGeneratorService);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            //AI响应片段
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    //工具调用请求片段
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    //工具调用结果片段
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * 构建展示流：只负责把 AI 输出实时推给前端，不阻塞响应线程。
     * 解析、重试、保存放到流结束后的异步任务中执行。
     */
    private Flux<String> processCodeStream(Flux<String> codeStream,
                                           CodeGenTypeEnum codeGenTypeEnum,
                                           Long appId,
                                           String userMessage,
                                           AiCodeGeneratorService aiCodeGeneratorService) {
        StringBuilder stringBuilder = new StringBuilder();
        return codeStream
                .doOnNext(stringBuilder::append)
                .doOnComplete(() -> scheduleAsyncCodeSave(
                        stringBuilder.toString(),
                        codeGenTypeEnum,
                        appId,
                        userMessage,
                        aiCodeGeneratorService));
    }

    /**
     * 异步保存代码，避免阻塞 SSE 流式输出。
     */
    private void scheduleAsyncCodeSave(String completeCode,
                                       CodeGenTypeEnum codeGenTypeEnum,
                                       Long appId,
                                       String userMessage,
                                       AiCodeGeneratorService aiCodeGeneratorService) {
        Mono.fromRunnable(() -> saveParsedCode(
                        completeCode,
                        codeGenTypeEnum,
                        appId,
                        userMessage,
                        aiCodeGeneratorService))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("代码异步保存任务失败，appId={}", appId, error)
                );
    }

    /**
     * 解析并在必要时重试后保存代码（在后台线程执行）。
     */
    private void saveParsedCode(String completeCode,
                                CodeGenTypeEnum codeGenTypeEnum,
                                Long appId,
                                String userMessage,
                                AiCodeGeneratorService aiCodeGeneratorService) {
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
