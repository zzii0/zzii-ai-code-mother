package com.nylg.zziiaicodemother.ai.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

// 多文件代码结果
@Data
@Description("多文件代码结果")
public class MultiFileCodeResult {

    // HTML代码
    @Description("HTML代码")
    private String htmlCode;

    // CSS代码
    @Description("CSS代码")
    private String cssCode;

    @Description("JavaScript代码")
    // JavaScript代码
    private String jsCode;

    //描述
    @Description("多文件代码的描述")
    private String description;
}
