package com.nylg.zziiaicodemother.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 */
@Deprecated
public class CodeParser {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*([\\s\\S]*?)(?:```|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_HTML_PATTERN = Pattern.compile("(?is)(<!DOCTYPE html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>)");

    /**
     * 解析 HTML 单文件代码
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        if (StrUtil.isNotBlank(htmlCode)) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            String rawHtml = extractRawHtml(codeContent);
            result.setHtmlCode(StrUtil.isNotBlank(rawHtml) ? rawHtml.trim() : codeContent.trim());
        }
        return result;
    }

    /**
     * 解析多文件代码（HTML + CSS + JS）
     */
    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
        if (StrUtil.isBlank(codeContent)) {
            return new MultiFileCodeResult();
        }

        MultiFileCodeResult result = new MultiFileCodeResult();
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);

        if (StrUtil.isBlank(htmlCode)) {
            htmlCode = extractRawHtml(codeContent);
        }

        if (StrUtil.isBlank(htmlCode) && StrUtil.isBlank(cssCode) && StrUtil.isBlank(jsCode)) {
            MultiFileCodeResult jsonResult = tryParseJsonResult(codeContent);
            if (jsonResult != null) {
                return jsonResult;
            }
        }

        if (StrUtil.isNotBlank(htmlCode)) {
            result.setHtmlCode(htmlCode.trim());
        }
        if (StrUtil.isNotBlank(cssCode)) {
            result.setCssCode(cssCode.trim());
        }
        if (StrUtil.isNotBlank(jsCode)) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    private static MultiFileCodeResult tryParseJsonResult(String codeContent) {
        String trimmed = codeContent.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            MultiFileCodeResult jsonResult = JSONUtil.toBean(trimmed, MultiFileCodeResult.class);
            if (jsonResult == null) {
                return null;
            }
            if (StrUtil.isAllBlank(jsonResult.getHtmlCode(), jsonResult.getCssCode(), jsonResult.getJsCode())) {
                return null;
            }
            return jsonResult;
        } catch (Exception ignored) {
            return null;
        }
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
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
