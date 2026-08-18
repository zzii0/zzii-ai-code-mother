package dev.langchain4j.model.chat.response;

/**
 * 流式模型连接正常结束，但未组装出任何有效 {@link ChatResponse}。
 */
public class EmptyStreamingChatResponseException extends RuntimeException {

    public EmptyStreamingChatResponseException() {
        super("Streaming chat model returned no content");
    }
}
