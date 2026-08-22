package com.exchangerate.manager.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI's {@code spring-ai-starter-model-ollama} auto-configuration only provides a
 * {@link ChatClient.Builder} bean ({@code ChatClientAutoConfiguration}); it does not expose a
 * {@link ChatClient} bean directly. This builds the single {@link ChatClient} used by
 * {@code TrendInsightService}.
 *
 * <p>No default system prompt is attached here: {@code TrendInsightService} constructs the system
 * prompt per call (it depends on the request's resolved data), per research.md's "Spring AI
 * integration style" decision.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
