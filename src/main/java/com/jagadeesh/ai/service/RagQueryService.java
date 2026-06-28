package com.jagadeesh.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles the "query" half of the RAG pipeline: retrieving relevant chunks
 * from the {@link VectorStore} for a question, stuffing them into a prompt,
 * and asking the LLM to answer using only that context.
 */
@Service
public class RagQueryService {

    /** Default number of chunks to retrieve when the caller doesn't specify one. */
    private static final int DEFAULT_TOP_K = 5;

    private static final String RAG_QUERY_PROMPT =
            "You are a helpful assistant that answers questions based on the provided context. If the answer is not contained within the context, respond with 'I don't know.'\n\nContext:\n%s\n\nQuestion: %s\nAnswer:";

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    /**
     * @param vectorStore the store to search for relevant chunks
     * @param chatClient  the client used to call the LLM with the assembled prompt
     */
    public RagQueryService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * Answers a question using the default number of retrieved chunks
     * ({@value #DEFAULT_TOP_K}).
     *
     * @param question the user's natural-language question
     * @return the LLM's answer plus the source chunks it was given
     */
    public RagQueryResult ask(String question) {
        return ask(question, DEFAULT_TOP_K);
    }

    /**
     * Answers a question using retrieval-augmented generation.
     *
     * <p>Steps: similarity-search the vector store for the {@code topK} most
     * relevant chunks, concatenate their text into a context block, format
     * it into {@link #RAG_QUERY_PROMPT} along with the question, and send
     * that prompt to the {@link ChatClient}.</p>
     *
     * @param question the user's natural-language question
     * @param topK     how many chunks to retrieve; falls back to
     *                 {@value #DEFAULT_TOP_K} if {@code null} or not positive
     *                 (previously this was hardcoded to 5 and the caller's
     *                 requested {@code topK} from the API was silently
     *                 dropped — this overload restores that control)
     * @return the LLM's answer plus the source chunks it was given
     */
    public RagQueryResult ask(String question, Integer topK) {
        int effectiveTopK = (topK != null && topK > 0) ? topK : DEFAULT_TOP_K;

        // Retrieve relevant documents from the vector store based on the question
        var relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(effectiveTopK).build());

        // Combine the content of the retrieved documents into a single context string
        StringBuilder contextBuilder = new StringBuilder();
        for (var doc : relevantDocs) {
            contextBuilder.append(doc.getText()).append("\n\n");
        }
        String context = contextBuilder.toString();

        // Format the prompt with the context and question
        String prompt = String.format(RAG_QUERY_PROMPT, context, question);

        // Use the chat client to get a response based on the prompt
        String response = chatClient.prompt(prompt)
                .call()
                .content();

        // Fix: IngestionService stores the filename under the "source" metadata
        // key, but this previously read it back under "fileName" — a key
        // mismatch that meant every Source.fileName() came back as "Unknown".
        var sourceDocs = relevantDocs.stream()
                .map(doc -> new Source(
                        String.valueOf(doc.getMetadata().getOrDefault("source", "Unknown")),
                        String.valueOf(doc.getMetadata().getOrDefault("documentId", "")),
                        snippet(doc.getText())))
                .toList();
        return new RagQueryResult(response, sourceDocs);
    }

    /**
     * Truncates a chunk's text to a short preview for display alongside an answer.
     *
     * @param text the full chunk text, may be {@code null}
     * @return the first 240 characters followed by "...", or the full text
     *         if it's 240 characters or shorter; empty string if {@code text} is null
     */
    private static String snippet(String text) {
        if (text == null) return "";
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    /**
     * Result of a RAG query.
     *
     * @param answer the LLM-generated answer
     * @param source the chunks that were retrieved and used as context
     */
    public record RagQueryResult(String answer, List<Source> source) {
    }

    /**
     * A single retrieved chunk's provenance, returned alongside the answer
     * so callers can see what the answer was based on.
     *
     * @param fileName   original filename the chunk came from
     * @param documentId id shared by all chunks from that ingestion
     * @param snippet    short preview of the chunk's text
     */
    public record Source(String fileName, String documentId, String snippet) {
    }
}