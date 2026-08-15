package com.nylg.zziiaicodemother.ai.model.result;

import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 应用创建时的 AI 分析结果
 */
@Description("应用创建时的 AI 分析结果")
@Data
public class AppCreateAiResult {

    @Description("根据用户需求推荐的代码生成类型，可选值：HTML、MULTI_FILE、VUE_PROJECT")
    private CodeGenTypeEnum codeGenType;

    @Description("根据用户需求生成的简洁应用名称，使用中文，4-20 个字符，不要引号或换行")
    private String appName;
}
