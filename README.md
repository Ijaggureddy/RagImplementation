# RAG Implementation with Spring AI

A Retrieval-Augmented Generation (RAG) application built with Spring Boot and Spring AI. Upload documents (PDF, DOCX, TXT, HTML, etc.), have them chunked and embedded into a pgvector database, then ask natural-language questions answered exclusively from that document context.

Built as a self-learning project to understand how RAG pipelines work end-to-end in a Java/Spring ecosystem.

---

## Architecture Overview

```
┌──────────────┐       ┌──────────────────┐       ┌─────────────────┐
│   Client     │──────▶│  RagController   │──────▶│IngestionService │
│  (curl/UI)   │       │  /api/rag/**     │       │                 │
└──────────────┘       └──────────────────┘       └────────┬────────┘
                              │                            │
                              │ /chat                      │ parse ➜ chunk ➜ embed ➜ store
                              ▼                            ▼
                       ┌──────────────────┐       ┌─────────────────┐
                       │ RagQueryService  │       │    pgvector     │
                       │                  │◀─────▶│  (PostgreSQL)   │
                       └────────┬─────────┘       └─────────────────┘
                                │
                                │ prompt with context
                                ▼
                       ┌──────────────────┐
                       │  Google Gemini   │
                       │  (via Spring AI) │
                       └──────────────────┘
```

**Ingestion pipeline:** Tika parses the uploaded file → `TokenTextSplitter` chunks it into ~500-token segments → Google's `text-embedding-004` model embeds each chunk → pgvector stores the embeddings.

**Query pipeline:** User's question is similarity-searched against pgvector (top-K chunks) → retrieved chunks are concatenated into a context block → context + question are formatted into a prompt → Gemini (`gemini-3.1 Pro`) generates an answer grounded in that context.

---

## Tech Stack

| Layer              | Technology                                         |
|--------------------|----------------------------------------------------|
| Framework          | Spring Boot 3.5.x, Java 21                         |
| AI Framework       | Spring AI 1.1.8                                    |
| Chat Model         | Google Gemini\Prefer your own model                |
| Embedding Model    | Google text-embedding-004 (768 dimensions)         |
| Vector Store       | pgvector on PostgreSQL 16                          |
| Document Parsing   | Apache Tika (via `spring-ai-tika-document-reader`) |
| Containerization   | Docker Compose (auto-managed by Spring Boot)       |
| Security           | Spring Security                                    |
| Build Tool         | Gradle (Kotlin DSL)                                |

---

## Project Structure

```
RagImplementation/
├── src/main/java/com/jagadeesh/ai/
│   ├── BuildingRagMain.java              # Application entry point
│   ├── config/
│   │   ├── RagConfig.java                # ChatClient + TokenTextSplitter beans
│   │   └── SecurityConfig.java           # Permits /api/rag/** endpoints
│   ├── controller/
│   │   └── RagController.java            # REST endpoints (ingest, chat, health)
│   └── service/
│       ├── IngestionService.java          # Document parsing, chunking, embedding
│       └── RagQueryService.java           # Similarity search + LLM prompting
├── src/main/resources/
│   └── application.yml                    # All configuration (Gemini, pgvector, server)
├── compose.yaml                           # pgvector Docker Compose definition
├── build.gradle.kts                       # Dependencies and build config
├── settings.gradle.kts
└── README.md
```

---

## Prerequisites

Before running this project, make sure you have the following installed:

- **Java 21** — verify with `java -version`
- **Gradle 8.x** — or use the included `./gradlew` wrapper
- **Docker Desktop** — required for the pgvector container
  - macOS: download from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
  - The `pgvector/pgvector:pg16` image supports Apple Silicon (M1/M2/M3) natively
- **Google AI Studio API Key** — free, no billing required
  - Get one at [aistudio.google.com/apikey](https://aistudio.google.com/apikey)

---

## Setup and Installation

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/RagImplementation.git
cd RagImplementation
```

### 2. Set the Gemini API key

Set it as an environment variable so it's never committed to source control:

```bash
# macOS / Linux
export GEMINI_API_KEY="your-api-key-here"

# To make it permanent, add to your shell profile:
echo 'export GEMINI_API_KEY="your-api-key-here"' >> ~/.zshrc
source ~/.zshrc
```

> **IntelliJ users:** The IDE may not inherit your shell's environment variables.
> Go to **Run → Edit Configurations → Environment Variables** and add `GEMINI_API_KEY=your-key`.

### 3. Start Docker Desktop

Launch Docker Desktop and wait until the whale icon in the menu bar is steady (not animating). Verify it's running:

```bash
docker version
```

You do **not** need to manually run `docker compose up` — Spring Boot's docker-compose integration handles it automatically on startup.

### 4. Build and run

```bash
./gradlew clean build
./gradlew bootRun
```

On first run you'll see Spring Boot:
1. Pull the `pgvector/pgvector:pg16` Docker image (one-time download, ~110 MB)
2. Create the container, volume, and network
3. Wait for Postgres to report healthy
4. Create the `vector` extension and `vector_store` table automatically
5. Start Tomcat on port **8080**

Look for `Main - Application started successfully.` in the console output.

---

## API Endpoints

### Health Check

```bash
curl http://localhost:8080/api/rag/health
```

**Response:**
```json
{"Status": "UP"}
```

### Upload and Ingest a Document

```bash
curl -X POST http://localhost:8080/api/rag/documents \
  -F "file=@/path/to/your/document.pdf"
```

**Supported file types:** PDF, DOCX, TXT, HTML, and any other format Apache Tika can parse.

**Response:**
```json
{
  "documentId": "a1b2c3d4-...",
  "filename": "document.pdf",
  "chunkCount": 42
}
```

### Ask a Question

```bash
curl -X POST http://localhost:8080/api/rag/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "What is this document about?"}'
```

**With optional topK parameter (defaults to 5):**

```bash
curl -X POST http://localhost:8080/api/rag/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "What are the key findings?", "topK": 10}'
```

**Response:**
```json
{
  "answer": "The document discusses...",
  "source": [
    {
      "fileName": "document.pdf",
      "documentId": "a1b2c3d4-...",
      "snippet": "First 240 characters of the relevant chunk..."
    }
  ]
}
```

---

## Configuration

All configuration lives in `src/main/resources/application.yml`:

```yaml
server:
  port: 8080
  servlet:
    multipart:
      max-file-size: 30MB
      max-request-size: 30MB

spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}        # Read from environment variable
        chat:
          options:
            model: gemini-3.1 Pro       
            temperature: 0.7
            max-output-tokens: 2048
        embedding:
          api-key: ${GEMINI_API_KEY}
          text:
            model: text-embedding-004

    vectorstore:
      pgvector:
        dimensions: 768                   # Must match the embedding model's output
        distance-type: COSINE_DISTANCE
        index-type: HNSW
        initialize-schema: true           # Auto-creates table on first run
```

### Key configuration points

| Property | Purpose |
|----------|---------|
| `GEMINI_API_KEY` | Set as environment variable, never hardcode |
| `dimensions: 768` | Must match `text-embedding-004` output (not 1536 like OpenAI) |
| `initialize-schema: true` | Creates the pgvector extension and `vector_store` table automatically |
| `max-file-size: 30MB` | Adjust based on your document sizes |

---

## Docker Compose

The `compose.yaml` defines the pgvector database:

```yaml
services:
  pgvector:
    image: 'pgvector/pgvector:pg16'
    environment:
      - 'POSTGRES_DB=mydatabase'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=myuser'
    labels:
      - "org.springframework.boot.service-connection=postgres"
    ports:
      - '5432:5432'
    volumes:
      - pgvector-data:/var/lib/postgresql/data

volumes:
  pgvector-data:
```

**Important notes:**
- The `service-connection` label lets Spring Boot auto-discover the datasource credentials from the container — no manual `spring.datasource.*` config needed.
- The named volume (`pgvector-data`) persists embeddings across container restarts.
- Postgres only applies `POSTGRES_USER`/`POSTGRES_DB`/`POSTGRES_PASSWORD` on the **first** initialization of an empty volume. If you change credentials, you must wipe the volume first: `docker compose -f compose.yaml down -v`.

---

## Common Issues and Troubleshooting

### "Unable to start docker process. Is docker correctly installed?"
Docker Desktop isn't running. Launch it and wait for the whale icon to stabilize, then retry.

### "role ... does not exist" or "database ... does not exist"
Stale Docker volume with old credentials. Wipe and re-init:
```bash
docker compose -f compose.yaml down -v
./gradlew bootRun
```

### "undefined volume pgvector-data"
The `volumes:` top-level section in `compose.yaml` is missing or incorrectly indented. It must be at column 0, a sibling of `services:`, not nested under it.

### Application starts but API returns 401 Unauthorized
Spring Security is locking down endpoints. Make sure `SecurityConfig.java` is in your project — it permits all `/api/rag/**` requests.

### Embedding dimension mismatch errors
Ensure `spring.ai.vectorstore.pgvector.dimensions` matches your embedding model's output. Google's `text-embedding-004` outputs **768** dimensions, not 1536 (which is OpenAI's default). If you've already stored embeddings with a different dimension, wipe the volume and restart.

---

## How It Works (For Learning)

This project demonstrates the core RAG pattern in four steps:

1. **Parse** — Apache Tika reads the uploaded file regardless of format (PDF, Word, HTML, etc.) and extracts raw text.

2. **Chunk** — `TokenTextSplitter` breaks the text into ~500-token segments with overlap, small enough to fit within embedding model limits and large enough to preserve context.

3. **Embed and Store** — Each chunk is sent to Google's `text-embedding-004` model, which returns a 768-dimensional vector. The vector and the original text are stored together in pgvector (a PostgreSQL extension for vector similarity search).

4. **Retrieve and Generate** — When a question arrives, it's embedded using the same model, then pgvector finds the most similar chunks via cosine distance. Those chunks are stuffed into a prompt template as "context," and Gemini generates an answer constrained to that context.

The key insight: the LLM never sees the whole document. It only sees the chunks most relevant to the question, which keeps the prompt small and the answer focused.

---

## Future Improvements

- [ ] Add similarity score threshold to filter out irrelevant chunks
- [ ] Implement the Spring AI `QuestionAnswerAdvisor` for idiomatic RAG
- [ ] Add file type and size validation in `IngestionService`
- [ ] Support deleting documents and their associated embeddings
- [ ] Add conversation memory for multi-turn Q&A
- [ ] Create a simple frontend UI
- [ ] Add proper error handling with `@ControllerAdvice`
- [ ] Write integration tests

---

## License

This project is licensed for educational and personal use.

**Author:** Jagadeeswar Reddy

© 2026 Jagadeeswar Reddy. All rights reserved.

This project is provided as-is for learning purposes. You are free to fork, modify, and use it for personal and educational projects. Commercial use requires prior written permission from the author.

---

## Acknowledgments

- [Spring AI](https://docs.spring.io/spring-ai/reference/) — AI integration framework for Spring
- [pgvector](https://github.com/pgvector/pgvector) — Open-source vector similarity search for PostgreSQL
- [Google AI Studio](https://aistudio.google.com/) — Free Gemini API access/Get the Paid API key
- [Apache Tika](https://tika.apache.org/) — Content detection and text extraction
