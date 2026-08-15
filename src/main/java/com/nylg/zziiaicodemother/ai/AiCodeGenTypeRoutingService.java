package com.nylg.zziiaicodemother.ai;

import com.nylg.zziiaicodemother.ai.model.result.AppCreateAiResult;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * 使用结构化输出返回代码生成类型与应用名称
 *
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求分析应用创建信息（名称 + 代码生成类型）
     *
     * @param userPrompt 用户输入的需求描述
     * @return 应用创建 AI 分析结果
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    AppCreateAiResult analyzeAppCreate(String userPrompt);
}
