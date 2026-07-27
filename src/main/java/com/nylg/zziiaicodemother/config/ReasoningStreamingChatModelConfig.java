package com.nylg.zziiaicodemother.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class ReasoningStreamingChatModelConfig {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private int maxTokens;

    @Bean
    public StreamingChatModel reasoningStreamingChatModel(){
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
