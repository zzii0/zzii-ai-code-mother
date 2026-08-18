package com.nylg.zziiaicodemother.core.parser;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFileCodeParserTest {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();

    @Test
    void parseCode_shouldExtractThreeFencedBlocks() {
        String content = """
                说明文字
                ```html
                <html></html>
                ```
                ```css
                body{}
                ```
                ```javascript
                console.log(1);
                ```
                """;
        MultiFileCodeResult result = parser.parseCode(content);
        assertEquals("<html></html>", result.getHtmlCode());
        assertEquals("body{}", result.getCssCode());
        assertEquals("console.log(1);", result.getJsCode());
    }

    @Test
    void parseCode_shouldAllowUnclosedCssFence() {
        String content = """
                ```html
                <html></html>
                ```
                ```css
                body{color:red;}
                """;
        MultiFileCodeResult result = parser.parseCode(content);
        assertTrue(StrUtil.isNotBlank(result.getHtmlCode()));
        assertTrue(StrUtil.isNotBlank(result.getCssCode()));
    }
}
