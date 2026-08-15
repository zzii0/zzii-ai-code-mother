package com.nylg.zziiaicodemother.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nylg.zziiaicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import com.nylg.zziiaicodemother.ai.guardrail.RetryOutputGuardrail;
import com.nylg.zziiaicodemother.ai.tools.ToolManager;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import com.nylg.zziiaicodemother.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.swing.*;
import java.time.Duration;

//AI代码生成服务工厂
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolMessage;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 和代码生成类型获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenTypeEnum) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(50)
                .build();
        // 从数据库加载历史对话到内存
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        //根据代码生成类型选择不同的模型配置
        return switch (codeGenTypeEnum) {
            // Vue 项目生成使用可以调用工具的推理模型
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolMessage.getAllTools())
                        .maxSequentialToolsInvocations(20)  //最多连续调用20次工具
                        .inputGuardrails(new PromptSafetyInputGuardrail())  //添加输入护轨
                        //.outputGuardrails(new RetryOutputGuardrail())   //添加输出护轨，但是为了流式输出，注释掉
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest,
                                "Error: there is no tool called " + toolExecutionRequest.name()))
                        .build();
            }
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail())  //添加输入护轨
                        //.outputGuardrails(new RetryOutputGuardrail())   //添加输出护轨，但是为了流式输出，注释掉
                        .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenTypeEnum.getValue());
        };
    }

    /**
     * 获取默认的AI服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 删除应用时失效该 app 相关的 AI 服务缓存。
     * 若 codeGenType 已知则只失效对应键；否则失效该 appId 下所有类型。
     */
    public void invalidateByAppId(long appId, String codeGenType) {
        CodeGenTypeEnum knownType = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (knownType != null) {
            serviceCache.invalidate(buildCacheKey(appId, knownType));
            log.info("已失效 AI 服务缓存，appId={}, type={}", appId, knownType.getValue());
            return;
        }
        for (CodeGenTypeEnum type : CodeGenTypeEnum.values()) {
            serviceCache.invalidate(buildCacheKey(appId, type));
        }
        log.info("已失效 AI 服务缓存（全部类型），appId={}", appId);
    }
}
