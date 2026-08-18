package com.nylg.zziiaicodemother.core.sse;

import cn.hutool.json.JSONUtil;

/**
 * 在字符串 Flux 中嵌入命名 SSE 事件的编解码器。
 * AppController 识别后转换为 {@code event: xxx} 推送给前端。
 */
public final class SseEventCodec {

    public static final String GENERATION_DONE = "generation_done";
    public static final String BUILD_START = "build_start";
    public static final String BUILD_SUCCESS = "build_success";
    public static final String BUILD_FAILED = "build_failed";
    public static final String BUILD_FIXING = "build_fixing";
    public static final String BUILD_GIVE_UP = "build_give_up";

    /** 原生 HTML / 多文件：校验与保存阶段事件 */
    public static final String VALIDATE_START = "validate_start";
    public static final String VALIDATE_FAILED = "validate_failed";
    public static final String ARTIFACT_FIXING = "artifact_fixing";
    public static final String SAVE_SUCCESS = "save_success";
    public static final String SAVE_GIVE_UP = "save_give_up";
    public static final String BUSINESS_ERROR = "business_error";

    private static final String PREFIX = "\u0001SSE_EVENT:";
    private static final char SEP = '\u0001';

    private SseEventCodec() {
    }

    public static String encode(String event, Object payload) {
        String json = payload == null ? "{}" : JSONUtil.toJsonStr(payload);
        return PREFIX + event + SEP + json;
    }

    public static boolean isEvent(String chunk) {
        return chunk != null && chunk.startsWith(PREFIX);
    }

    public static ParsedEvent parse(String chunk) {
        if (!isEvent(chunk)) {
            throw new IllegalArgumentException("not an SSE event chunk");
        }
        String body = chunk.substring(PREFIX.length());
        int sep = body.indexOf(SEP);
        if (sep < 0) {
            return new ParsedEvent(body, "{}");
        }
        return new ParsedEvent(body.substring(0, sep), body.substring(sep + 1));
    }

    public record ParsedEvent(String event, String data) {
    }
}
