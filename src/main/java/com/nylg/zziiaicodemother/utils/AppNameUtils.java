package com.nylg.zziiaicodemother.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 应用名称工具类
 */
public final class AppNameUtils {

    private static final int MAX_APP_NAME_LENGTH = 50;

    private AppNameUtils() {
    }

    /**
     * 根据 initPrompt 生成兜底名称（截取前 12 个字符）
     */
    public static String buildFallbackName(String initPrompt) {
        if (StrUtil.isBlank(initPrompt)) {
            return "未命名应用";
        }
        return initPrompt.substring(0, Math.min(initPrompt.length(), 12));
    }

    /**
     * 解析 AI 生成的应用名称，无效时回退到兜底名称
     */
    public static String resolveAppName(String aiName, String fallbackName) {
        if (StrUtil.isBlank(aiName)) {
            return fallbackName;
        }
        String name = aiName.trim()
                .replaceAll("[\\r\\n]+", "")
                .replaceAll("^[\"'「『《【\\[]|[\"'」』》】\\]]$", "");
        if (StrUtil.isBlank(name)) {
            return fallbackName;
        }
        return name.length() > MAX_APP_NAME_LENGTH
                ? name.substring(0, MAX_APP_NAME_LENGTH)
                : name;
    }
}
