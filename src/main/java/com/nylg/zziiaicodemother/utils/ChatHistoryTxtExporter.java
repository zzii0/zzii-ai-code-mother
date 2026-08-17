package com.nylg.zziiaicodemother.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.model.entity.ChatHistory;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryExportModeEnum;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将对话历史组装为文本，支持完整版与精简版（折叠超长代码块）。
 */
public final class ChatHistoryTxtExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?ms)^(`{3,})([^\r\n]*)\r?\n(.*?)(?:\r?\n)\\1");
    /** 精简模式下，超过此行数的代码块会被折叠 */
    private static final int COMPACT_CODE_BLOCK_MAX_LINES = 30;
    /** 精简模式下，超长代码块保留的正文行数 */
    private static final int COMPACT_CODE_PREVIEW_LINES = 30;

    private ChatHistoryTxtExporter() {
    }

    public static String buildMarkdown(String appName,
                                       Long appId,
                                       List<ChatHistory> historyList,
                                       ChatHistoryExportModeEnum exportMode,
                                       LocalDateTime exportTime) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 应用对话记录：").append(sanitizeInlineText(appName)).append("\n\n");
        markdown.append("- 导出时间：").append(exportTime.format(DATE_TIME_FORMATTER)).append("\n");
        markdown.append("- 应用 ID：").append(appId).append("\n");
        markdown.append("- 导出模式：").append(exportMode.getText()).append("\n\n");
        markdown.append("> 重要提示：请用「记事本」打开本文件。\n\n");
        markdown.append("---\n\n");

        if (CollUtil.isEmpty(historyList)) {
            markdown.append("> 暂无对话记录\n");
            return markdown.toString();
        }

        for (ChatHistory chatHistory : historyList) {
            ChatHistoryMessageTypeEnum messageTypeEnum =
                    ChatHistoryMessageTypeEnum.getEnumByValue(chatHistory.getMessageType());
            String roleText = messageTypeEnum != null ? messageTypeEnum.getText() : "未知";
            String createTimeText = chatHistory.getCreateTime() != null
                    ? chatHistory.getCreateTime().format(DATE_TIME_FORMATTER)
                    : "未知时间";
            markdown.append("## ").append(roleText).append(" · ").append(createTimeText).append("\n\n");
            String message = StrUtil.blankToDefault(chatHistory.getMessage(), "");
            if (ChatHistoryExportModeEnum.COMPACT.equals(exportMode)) {
                message = compactLongCodeBlocks(message);
            }
            markdown.append(message).append("\n\n");
        }
        return markdown.toString();
    }

    /**
     * 精简模式：超长代码块保留开头 30 行，其余用省略说明替代。
     */
    public static String compactLongCodeBlocks(String content) {
        if (StrUtil.isBlank(content)) {
            return content;
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String fence = matcher.group(1);
            String language = StrUtil.blankToDefault(matcher.group(2), "").trim();
            String body = matcher.group(3);
            int lineCount = countLines(body);
            if (lineCount > COMPACT_CODE_BLOCK_MAX_LINES) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(buildCompactCodeBlock(fence, language, body, lineCount)));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 保留代码块开头若干行，并标注省略行数。
     */
    static String buildCompactCodeBlock(String fence, String language, String body, int totalLines) {
        String[] lines = body.split("\\R", -1);
        int previewCount = Math.min(COMPACT_CODE_PREVIEW_LINES, lines.length);
        StringBuilder compact = new StringBuilder();
        compact.append(fence);
        if (StrUtil.isNotBlank(language)) {
            compact.append(language);
        }
        compact.append('\n');
        for (int i = 0; i < previewCount; i++) {
            compact.append(lines[i]);
            if (i < previewCount - 1) {
                compact.append('\n');
            }
        }
        compact.append('\n');
        compact.append('\n');
        compact.append("...（其余代码已省略，共 ").append(totalLines).append(" 行，此处仅保留前 ")
                .append(previewCount).append(" 行）...");
        compact.append('\n');
        compact.append(fence);
        return compact.toString();
    }

    private static int countLines(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        return text.split("\\R", -1).length;
    }

    private static String sanitizeInlineText(String text) {
        if (StrUtil.isBlank(text)) {
            return "未命名应用";
        }
        return text.replace("\r", " ").replace("\n", " ").trim();
    }
}
