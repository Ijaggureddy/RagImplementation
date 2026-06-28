package com.jagadeesh.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the RAG implementation demo.
 *
 * <p>Bootstraps Spring Boot, which in turn wires up the pgvector
 * {@code VectorStore}, the OpenAI {@code ChatModel}, and the beans defined
 * in {@code RagConfig}.</p>
 */
@SpringBootApplication
public class BuildingRagMain {

    /**
     * Starts the Spring application context.
     *
     * @param args standard command-line arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(BuildingRagMain.class, args);
        System.out.println("Main - Application started successfully.");
    }
}