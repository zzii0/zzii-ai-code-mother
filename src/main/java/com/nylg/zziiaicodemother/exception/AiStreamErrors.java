package com.nylg.zziiaicodemother.exception;

import cn.hutool.core.util.StrUtil;

/**
 * 将 AI 流式链路中的异常转为用户可读文案。
 */
public final class AiStreamErrors {

    private AiStreamErrors() {
    }

    public static String userMessage(Throwable error) {
        if (error == null) {
            return AiStreamingEmptyResponseException.USER_MESSAGE;
        }
        if (error instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        Throwable root = rootCause(error);
        if (root instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        if (isEmptyStreamingResponse(root)) {
            return AiStreamingEmptyResponseException.USER_MESSAGE;
        }
        String message = StrUtil.blankToDefault(root.getMessage(), error.getMessage());
        if (StrUtil.isBlank(message)) {
            return "AI 生成失败，请稍后重试";
        }
        if (message.contains("completeResponse") && message.contains("null")) {
            return AiStreamingEmptyResponseException.USER_MESSAGE;
        }
        return "AI 生成失败：" + message;
    }

    public static Throwable toBusinessException(Throwable error) {
        if (error instanceof BusinessException) {
            return error;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, userMessage(error));
    }

    private static boolean isEmptyStreamingResponse(Throwable error) {
        return error instanceof AiStreamingEmptyResponseException
                || error instanceof dev.langchain4j.model.chat.response.EmptyStreamingChatResponseException;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
