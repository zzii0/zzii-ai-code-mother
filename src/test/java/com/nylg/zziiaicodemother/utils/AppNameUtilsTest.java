package com.nylg.zziiaicodemother.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppNameUtilsTest {

    @Test
    void buildFallbackName_shouldTruncateLongPrompt() {
        assertEquals("做一个精美的作品展示网", AppNameUtils.buildFallbackName("做一个精美的作品展示网站，包含首页和作品列表"));
    }

    @Test
    void resolveAppName_shouldUseFallbackWhenBlank() {
        assertEquals("默认名称", AppNameUtils.resolveAppName("  ", "默认名称"));
    }

    @Test
    void resolveAppName_shouldStripQuotesAndLimitLength() {
        assertEquals("作品展示网", AppNameUtils.resolveAppName("「作品展示网」", "默认名称"));
        String longName = "A".repeat(60);
        assertEquals(50, AppNameUtils.resolveAppName(longName, "默认名称").length());
    }
}
