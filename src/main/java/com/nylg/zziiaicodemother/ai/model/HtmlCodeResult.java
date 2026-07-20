package com.nylg.zziiaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
// HTML代码结果
@Description("HTML代码结果")
@Data
public class HtmlCodeResult {

    // HTML代码
    @Description("HTML代码")
    private String htmlCode;

    // 描述
    @Description("HTML代码的描述")
    private String description;
}
