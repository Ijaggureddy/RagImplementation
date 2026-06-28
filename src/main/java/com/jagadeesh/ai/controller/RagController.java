package com.jagadeesh.ai.controller;

import com.jagadeesh.ai.service.IngestionService;
import com.jagadeesh.ai.service.RagQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * REST endpoints for the RAG demo: uploading documents to ingest, asking
 * questions answered against those documents, and a basic health check.
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final IngestionService ingestionService;
    private final RagQueryService ragService;

    /**
     * @param ingestionService service that parses, chunks, and stores uploaded documents
     * @param ragService       service that retrieves context and asks the LLM
     */
    public RagController(IngestionService ingestionService, RagQueryService ragService) {
        this.ingestionService = ingestionService;
        this.ragService = ragService;
    }

    /**
     * Uploads and ingests a single document into the vector store.
     *
     * @param file the document to ingest (PDF, DOCX, TXT, etc., via Tika)
     * @return 200 with ingestion summary on success, 400 if the file is empty
     * @throws IOException if the file cannot be read or parsed
     */
    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<IngestionService.IngestResult> ingestDocument(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ingestionService.ingestResult(file));
    }

    /**
     * Answers a question using retrieval-augmented generation over
     * previously ingested documents.
     *
     * @param request the question to ask, and an optional number of chunks
     *                to retrieve ({@code topK})
     * @return 200 with the answer and the source chunks it was based on
     */
    @PostMapping("/chat")
    public ResponseEntity<RagQueryService.RagQueryResult> chat(@RequestBody ChatRequest request) {
        // Fix: request.topK() was previously accepted by the API but never
        // forwarded to the service, which always retrieved a hardcoded 5
        // chunks. It's now passed through (ask() falls back to a default
        // when topK is null or not positive).
        return ResponseEntity.ok(ragService.ask(request.query(), request.topK()));
    }

    /**
     * Basic liveness check.
     *
     * @return a simple status map
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("Status", "UP");
    }

    /**
     * Request body for {@link #chat(ChatRequest)}.
     *
     * @param query the natural-language question
     * @param topK  optional override for how many chunks to retrieve;
     *              {@code null} uses the service's default
     */
    public record ChatRequest(String query, Integer topK) {
    }
}