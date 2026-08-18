package com.nylg.zziiaicodemother.exception;

/**
 * AI 流式接口正常结束但未返回任何有效内容（空 SSE / 无文本、无工具调用）。
 */
public class AiStreamingEmptyResponseException extends BusinessException {

    public static final String USER_MESSAGE =
            "AI 模型未返回有效内容，可能是网络超时或服务暂时不可用，请稍后重试";

    public AiStreamingEmptyResponseException() {
        super(ErrorCode.SYSTEM_ERROR, USER_MESSAGE);
    }
}
