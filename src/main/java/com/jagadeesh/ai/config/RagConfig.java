package com.jagadeesh.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central Spring AI configuration for the RAG pipeline.
 *
 * <p>Defines the two collaborator beans that {@code IngestionService} and
 * {@code RagQueryService} depend on: a {@link ChatClient} for talking to the
 * LLM, and a {@link TokenTextSplitter} for chunking documents before they are
 * embedded and stored in the vector store.</p>
 */
@Configuration
public class RagConfig {

    /**
     * Builds the {@link ChatClient} used to send prompts to the configured
     * {@link ChatModel} (OpenAI, based on {@code build.gradle.kts}).
     *
     * @param chatModel the autoconfigured chat model provided by the
     *                   spring-ai-starter-model-openai dependency
     * @return a ready-to-use ChatClient with default settings
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .build();
    }

    /**
     * Builds the {@link TokenTextSplitter} used by {@code IngestionService}
     * to break ingested documents into embedding-sized chunks.
     *
     * <p>This used to be built directly inside {@code IngestionService},
     * which caused a startup failure: that class declared a
     * {@code TokenTextSplitter} constructor parameter with no bean
     * available to satisfy it. Defining it here as a {@code @Bean} fixes
     * that, and lets the chunking strategy be tuned in one place.</p>
     *
     * @return a configured TokenTextSplitter
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(500)              // tokens per chunk
                .withMinChunkSizeChars(350)      // min chars per chunk
                .withMinChunkLengthToEmbed(5)    // drop chunks shorter than this
                .withMaxNumChunks(1000)          // safety cap on chunk count
                .withKeepSeparator(true)         // preserve newline separators
                .build();
    }
}