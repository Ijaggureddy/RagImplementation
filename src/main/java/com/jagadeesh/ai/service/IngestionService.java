package com.jagadeesh.ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Handles the "ingestion" half of the RAG pipeline: turning an uploaded file
 * into embedded, searchable chunks in the {@link VectorStore}.
 *
 * <p>Pipeline for each call to {@link #ingestResult(MultipartFile)}:</p>
 * <ol>
 *   <li>Wrap the upload bytes in a named {@link Resource} so Tika can sniff
 *       the file type from the filename.</li>
 *   <li>Parse the file into one or more {@link Document}s via
 *       {@link TikaDocumentReader} (supports PDF, DOCX, TXT, HTML, etc.).</li>
 *   <li>Tag every document with a shared {@code documentId} and the original
 *       {@code source} filename, so results can be traced back later.</li>
 *   <li>Split the documents into embedding-sized chunks with
 *       {@link TokenTextSplitter}.</li>
 *   <li>Embed and persist the chunks in the {@link VectorStore}.</li>
 * </ol>
 */
@Service
public class IngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    /**
     * @param vectorStore  the configured pgvector-backed store that chunks
     *                     are embedded into
     * @param textSplitter the shared chunking strategy, provided by the
     *                     {@code TokenTextSplitter} bean in {@code RagConfig}
     *
     *                     <p><b>Fix:</b> this constructor previously declared
     *                     a {@code TokenTextSplitter} parameter but then
     *                     ignored it and built a new instance inline. That
     *                     left no bean to satisfy the constructor injection
     *                     and the app failed to start with a
     *                     {@code NoSuchBeanDefinitionException}. The splitter
     *                     is now defined once in {@code RagConfig} and used
     *                     as-is here.</p>
     */
    public IngestionService(VectorStore vectorStore, TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
    }

    /**
     * Ingests a single uploaded file: parses it, chunks it, embeds the
     * chunks, and writes them to the vector store.
     *
     * @param multipartFile the uploaded file (must not be empty; the
     *                       controller checks this before calling in)
     * @return summary of what was stored: a generated document id, the
     *         original filename, and how many chunks were produced
     * @throws IOException if the file's bytes cannot be read, or if Tika
     *                      fails to parse the document's content
     */
    public IngestResult ingestResult(MultipartFile multipartFile) throws IOException {
        String filename = multipartFile.getOriginalFilename() == null ? "upload" : multipartFile.getOriginalFilename();
        Resource resource = new ByteArrayResource(multipartFile.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        String docId = UUID.randomUUID().toString();
        documents.forEach(doc -> {
            doc.getMetadata().put("source", filename);
            doc.getMetadata().put("documentId", docId);
        });

        List<Document> chunks = textSplitter.apply(documents);
        vectorStore.add(chunks);
        return new IngestResult(docId, filename, chunks.size());
    }

    /**
     * Result of an ingestion call, returned to the API caller.
     *
     * @param documentId generated UUID shared by all chunks from this upload
     * @param filename   original filename as uploaded
     * @param chunkCount number of chunks written to the vector store
     */
    public record IngestResult(String documentId, String filename, int chunkCount) {
    }
}