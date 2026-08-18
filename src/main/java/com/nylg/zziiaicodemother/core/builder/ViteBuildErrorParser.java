package com.nylg.zziiaicodemother.core.builder;

import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Vite / Vue 编译器的构建输出中解析出错文件与错误信息。
 */
public final class ViteBuildErrorParser {

    private static final Pattern FILE_HEADER = Pattern.compile(
            "(?i)(?:^|\\n)file:\\s*(.+?)(?::undefined|:(\\d+))?(?::\\d+)?\\s*(?:\\n|$)");
    private static final Pattern ABSOLUTE_SOURCE_PATH = Pattern.compile(
            "(?m)^((?:[A-Za-z]:)?[\\\\/][^\\r\\n]+\\.(?:vue|ts|tsx|js|jsx|css|scss|json))\\s*$");
    private static final Pattern CARET_LINE = Pattern.compile(
            "(?m)^\\s*(\\d+)\\s*\\|[^\\r\\n]*\\r?\\n\\s*\\|\\s*[\\^~]+");
    private static final Pattern LINE_MARKER = Pattern.compile("(?m)^\\s*(\\d+)\\s*\\|");
    private static final Pattern VITE_PLUGIN_ERROR = Pattern.compile(
            "(?m)^\\[vite(?::[^\\]]+)?]\\s*(.+)$");
    private static final Pattern SYNTAX_ERROR = Pattern.compile(
            "(?m)^(?:error during build:\\s*)?(?:SyntaxError|Error|RollupError):\\s*(.+)$");

    private ViteBuildErrorParser() {
    }

    public static BuildResult parseFailure(int exitCode, String output, File projectDir) {
        String text = StrUtil.nullToEmpty(output);
        String errorFile = extractErrorFile(text, projectDir);
        Integer errorLine = extractErrorLine(text);
        String errorMessage = extractErrorMessage(text);
        if (StrUtil.isBlank(errorMessage)) {
            errorMessage = "构建失败，退出码: " + exitCode;
        }
        return BuildResult.fail(exitCode, text, errorFile, errorLine, errorMessage);
    }

    static String extractErrorFile(String output, File projectDir) {
        Matcher fileHeader = FILE_HEADER.matcher(output);
        if (fileHeader.find()) {
            return toRelativePath(fileHeader.group(1).trim(), projectDir);
        }
        Matcher absolute = ABSOLUTE_SOURCE_PATH.matcher(output);
        if (absolute.find()) {
            return toRelativePath(absolute.group(1).trim(), projectDir);
        }
        return null;
    }

    static Integer extractErrorLine(String output) {
        Matcher fileHeader = FILE_HEADER.matcher(output);
        if (fileHeader.find() && fileHeader.group(2) != null) {
            try {
                return Integer.parseInt(fileHeader.group(2));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Matcher caretLine = CARET_LINE.matcher(output);
        if (caretLine.find()) {
            try {
                return Integer.parseInt(caretLine.group(1));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Matcher lineMarker = LINE_MARKER.matcher(output);
        if (lineMarker.find()) {
            try {
                return Integer.parseInt(lineMarker.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static String extractErrorMessage(String output) {
        Matcher vite = VITE_PLUGIN_ERROR.matcher(output);
        if (vite.find()) {
            return vite.group(1).trim();
        }
        Matcher syntax = SYNTAX_ERROR.matcher(output);
        if (syntax.find()) {
            return syntax.group(1).trim();
        }
        return null;
    }

    private static String toRelativePath(String path, File projectDir) {
        if (StrUtil.isBlank(path) || projectDir == null) {
            return path;
        }
        try {
            String absoluteProject = projectDir.getAbsolutePath();
            String normalized = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);
            String normalizedProject = absoluteProject.replace('/', File.separatorChar).replace('\\', File.separatorChar);
            if (normalized.regionMatches(true, 0, normalizedProject, 0, normalizedProject.length())) {
                String relative = normalized.substring(normalizedProject.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.replace('\\', '/');
            }
        } catch (Exception ignored) {
            // fall through
        }
        return path.replace('\\', '/');
    }
}
