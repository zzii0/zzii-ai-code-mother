package com.nylg.zziiaicodemother.core.parser;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

//HTML代码解析器
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_HTML_PATTERN = Pattern.compile("(?is)(<!DOCTYPE html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>)");

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        String htmlCode = extractCodeByPattern(codeContent);
        if (StrUtil.isNotBlank(htmlCode)) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            String rawHtml = extractRawHtml(codeContent);
            result.setHtmlCode(StrUtil.isNotBlank(rawHtml) ? rawHtml.trim() : codeContent.trim());
        }
        return result;
    }

    /**
     * 提取原始 HTML 内容
     */
    private static String extractRawHtml(String content) {
        Matcher matcher = RAW_HTML_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 根据正则模式提取代码
     */
    private static String extractCodeByPattern(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
