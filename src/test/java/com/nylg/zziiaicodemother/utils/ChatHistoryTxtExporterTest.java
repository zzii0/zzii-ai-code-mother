package com.nylg.zziiaicodemother.utils;

import com.nylg.zziiaicodemother.model.entity.ChatHistory;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryExportModeEnum;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class ChatHistoryTxtExporterTest {

    @Test
    void buildMarkdown_shouldIncludeHeaderAndMessages() {
        ChatHistory userMessage = ChatHistory.builder()
                .message("设计一个企业官网")
                .messageType(ChatHistoryMessageTypeEnum.USER.getValue())
                .createTime(LocalDateTime.of(2026, 8, 14, 21, 31, 45))
                .build();
        ChatHistory aiMessage = ChatHistory.builder()
                .message("好的，我来帮你设计。")
                .messageType(ChatHistoryMessageTypeEnum.AI.getValue())
                .createTime(LocalDateTime.of(2026, 8, 14, 21, 32, 7))
                .build();

        String markdown = ChatHistoryTxtExporter.buildMarkdown(
                "企业官网",
                123L,
                List.of(userMessage, aiMessage),
                ChatHistoryExportModeEnum.FULL,
                LocalDateTime.of(2026, 8, 17, 13, 28, 0));

        Assertions.assertTrue(markdown.contains("# 应用对话记录：企业官网"));
        Assertions.assertTrue(markdown.contains("- 应用 ID：123"));
        Assertions.assertTrue(markdown.contains("- 导出模式：完整版"));
        Assertions.assertTrue(markdown.contains("## 用户 · 2026-08-14 21:31:45"));
        Assertions.assertTrue(markdown.contains("设计一个企业官网"));
        Assertions.assertTrue(markdown.contains("## AI · 2026-08-14 21:32:07"));
        Assertions.assertTrue(markdown.contains("> 重要提示：请用「记事本」打开本文件。"));
    }

    @Test
    void compactLongCodeBlocks_shouldKeepFirst30Lines() {
        StringBuilder longCode = new StringBuilder("```javascript\n");
        for (int i = 1; i <= 40; i++) {
            longCode.append("line ").append(i).append('\n');
        }
        longCode.append("```");

        String compact = ChatHistoryTxtExporter.compactLongCodeBlocks(longCode.toString());

        Assertions.assertTrue(compact.contains("```javascript"));
        Assertions.assertTrue(compact.contains("line 1"));
        Assertions.assertTrue(compact.contains("line 30"));
        Assertions.assertTrue(compact.contains("其余代码已省略，共 40 行"));
        Assertions.assertTrue(compact.contains("仅保留前 30 行"));
        Assertions.assertFalse(compact.contains("line 31"));
        Assertions.assertFalse(compact.contains("line 40"));
    }

    @Test
    void compactLongCodeBlocks_shouldKeepShortCodeBlockUnchanged() {
        String shortCode = """
                ```html
                <div>hello</div>
                ```
                """;

        String compact = ChatHistoryTxtExporter.compactLongCodeBlocks(shortCode);

        Assertions.assertEquals(shortCode, compact);
        Assertions.assertTrue(compact.contains("<div>hello</div>"));
    }

    @Test
    void buildMarkdown_fullMode_shouldNotAppendDiskSourceAppendix() {
        ChatHistory aiMessage = ChatHistory.builder()
                .message("```html\n<!DOCTYPE html>\n```")
                .messageType(ChatHistoryMessageTypeEnum.AI.getValue())
                .createTime(LocalDateTime.of(2026, 8, 14, 21, 32, 7))
                .build();

        String markdown = ChatHistoryTxtExporter.buildMarkdown(
                "日志记录站",
                445878391410593792L,
                List.of(aiMessage),
                ChatHistoryExportModeEnum.FULL,
                LocalDateTime.of(2026, 8, 17, 13, 28, 0));

        Assertions.assertFalse(markdown.contains("附录：当前应用源码"));
        Assertions.assertTrue(markdown.contains("<!DOCTYPE html>"));
        Assertions.assertFalse(markdown.contains("&lt;"));
    }

    @Test
    void buildMarkdown_compactMode_shouldNotEscapeCode() {
        StringBuilder longCode = new StringBuilder("```html\n");
        for (int i = 1; i <= 35; i++) {
            longCode.append("<div>line ").append(i).append("</div>\n");
        }
        longCode.append("```");

        ChatHistory aiMessage = ChatHistory.builder()
                .message(longCode.toString())
                .messageType(ChatHistoryMessageTypeEnum.AI.getValue())
                .createTime(LocalDateTime.of(2026, 8, 14, 21, 32, 7))
                .build();

        String markdown = ChatHistoryTxtExporter.buildMarkdown(
                "日志记录站",
                445878391410593792L,
                List.of(aiMessage),
                ChatHistoryExportModeEnum.COMPACT,
                LocalDateTime.of(2026, 8, 17, 13, 28, 0));

        Assertions.assertTrue(markdown.contains("<div>line 1</div>"));
        Assertions.assertTrue(markdown.contains("<div>line 30</div>"));
        Assertions.assertFalse(markdown.contains("<div>line 31</div>"));
        Assertions.assertFalse(markdown.contains("&lt;"));
    }
}
