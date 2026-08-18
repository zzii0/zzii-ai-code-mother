package com.nylg.zziiaicodemother.core.handler;

import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.exception.AiStreamErrors;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器。
 * 根据代码生成类型选择合适的流处理器，并把 {@link AiGenerationTask} 向下传递：
 *   HTML、MULTI_FILE → {@link SimpleTextStreamHandler}
 *   VUE_PROJECT → {@link JsonMessageStreamHandler}
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    @Resource
    private SimpleTextStreamHandler simpleTextStreamHandler;

    /**
     * 根据代码类型选择流处理器，并将 generationTask / userMessage 传递给具体 Handler。
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenType,
                                  String userMessage,
                                  AiGenerationTask generationTask) {
        return switch (codeGenType) {
            case VUE_PROJECT ->
                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser, generationTask)
                            .onErrorMap(AiStreamErrors::toBusinessException);
            case HTML, MULTI_FILE ->
                    simpleTextStreamHandler.handle(
                            originFlux, chatHistoryService, appId, loginUser, codeGenType, userMessage, generationTask)
                            .onErrorMap(AiStreamErrors::toBusinessException);
        };
    }
}
