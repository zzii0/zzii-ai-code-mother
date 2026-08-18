package com.nylg.zziiaicodemother.core.validator;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 原生 HTML / 多文件模式的确定性产物校验。
 * 不限制代码内容长度，仅要求非空，并对明显结构问题做轻量检查。
 */
@Component
public class NativeArtifactValidator {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?i)<\\s*html\\b");
    private static final Pattern HTML_CLOSE_PATTERN = Pattern.compile("(?i)</\\s*html\\s*>");
    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("```");

    public ArtifactValidationResult validateHtml(HtmlCodeResult result) {
        List<ArtifactIssue> issues = new ArrayList<>();
        validateIndexHtml(result != null ? result.getHtmlCode() : null, false, issues);
        return issues.isEmpty() ? ArtifactValidationResult.ok() : ArtifactValidationResult.fail(issues);
    }

    public ArtifactValidationResult validateMultiFile(MultiFileCodeResult result) {
        List<ArtifactIssue> issues = new ArrayList<>();
        String html = result != null ? result.getHtmlCode() : null;
        String css = result != null ? result.getCssCode() : null;
        String js = result != null ? result.getJsCode() : null;

        validateIndexHtml(html, true, issues);
        validateStyleCss(css, issues);
        validateScriptJs(js, issues);
        if (issues.isEmpty() && StrUtil.isNotBlank(html)) {
            validateHtmlReferences(html, issues);
        }
        return issues.isEmpty() ? ArtifactValidationResult.ok() : ArtifactValidationResult.fail(issues);
    }

    private void validateIndexHtml(String html, boolean multiFileMode, List<ArtifactIssue> issues) {
        if (StrUtil.isBlank(html)) {
            issues.add(issue("index.html", "HTML 内容为空"));
            return;
        }
        String trimmed = html.trim();
        if (looksLikeProseInsteadOfHtml(trimmed)) {
            issues.add(issue("index.html", "内容不像有效 HTML，可能混入了说明文字"));
            return;
        }
        if (!HTML_TAG_PATTERN.matcher(trimmed).find()) {
            issues.add(issue("index.html", "缺少 <html> 标签"));
            return;
        }
        if (!HTML_CLOSE_PATTERN.matcher(trimmed).find()) {
            issues.add(issue("index.html", "缺少 </html> 闭合标签，页面结构可能不完整"));
        }
        if (multiFileMode && !trimmed.toLowerCase(Locale.ROOT).contains("<head")) {
            issues.add(issue("index.html", "缺少 <head> 区域，可能不完整"));
        }
    }

    private void validateStyleCss(String css, List<ArtifactIssue> issues) {
        if (StrUtil.isBlank(css)) {
            issues.add(issue("style.css", "CSS 内容为空"));
            return;
        }
        if (MARKDOWN_FENCE_PATTERN.matcher(css).find()) {
            issues.add(issue("style.css", "CSS 中混入了 Markdown 代码块标记"));
        }
    }

    private void validateScriptJs(String js, List<ArtifactIssue> issues) {
        if (StrUtil.isBlank(js)) {
            issues.add(issue("script.js", "JavaScript 内容为空"));
            return;
        }
        if (MARKDOWN_FENCE_PATTERN.matcher(js).find()) {
            issues.add(issue("script.js", "JavaScript 中混入了 Markdown 代码块标记"));
        }
    }

    private void validateHtmlReferences(String html, List<ArtifactIssue> issues) {
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("style.css")) {
            issues.add(issue("index.html", "未引用 style.css"));
        }
        if (!lower.contains("script.js")) {
            issues.add(issue("index.html", "未引用 script.js"));
        }
    }

    private boolean looksLikeProseInsteadOfHtml(String content) {
        boolean hasHtmlTag = content.contains("<") && content.contains(">");
        if (!hasHtmlTag) {
            return true;
        }
        long fenceCount = MARKDOWN_FENCE_PATTERN.matcher(content).results().count();
        if (fenceCount >= 2 && !HTML_TAG_PATTERN.matcher(content).find()) {
            return true;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.startsWith("#") && !lower.contains("<html");
    }

    private static ArtifactIssue issue(String fileName, String message) {
        return ArtifactIssue.builder().fileName(fileName).message(message).build();
    }
}
