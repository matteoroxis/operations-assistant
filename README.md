# Operations Assistant

> A stateful AI agent for IT/Operations teams — built with **Spring AI** and **MongoDB Atlas**.
>
> This project is the companion code for a 3-part article series published on [Foojay.io](https://foojay.io).

---

## Series overview

| Article | Tag | Topic |
|---------|-----|-------|
| Part 1 | `v1-rag` | **RAG Foundation** — knowledge ingestion, semantic search, stateless Q&A |
| Part 2 | `v2-memory` | **Memory** — short-term conversational memory + long-term cross-session memory |
| Part 3 | `v3-stateful` | **Stateful Agent** — checkpoint persistence, pause/resume, tool execution |

Each tag represents a self-contained, runnable state of the application matching the code shown in the corresponding article.

---

## What it does

The Operations Assistant helps IT and operations teams:

- Search operational **runbooks, SOPs, postmortems and alert notes** using semantic search
- Maintain **conversational continuity** within a session (short-term memory)
- Recall **persistent facts and preferences** across sessions (long-term memory)
- **Checkpoint a multi-step workflow** so a task can be suspended and resumed exactly where it left off

MongoDB Atlas acts as the **unified backend** for knowledge retrieval, memory and execution state — no additional infrastructure required.

---

## Architecture

```
Operator / UI
      │
      ▼
Spring Boot REST API
      │
      ▼
Operations Assistant Orchestrator
      ├── Spring AI ChatClient
      │       ├── QuestionAnswerAdvisor  (RAG retrieval)
      │       ├── MessageChatMemoryAdvisor  (short-term memory, Part 2+)
      │       └── LongTermMemoryAdvisor  (long-term memory, Part 2+)
      ├── IngestionService
      ├── KnowledgeRetrievalService
      ├── MemoryService  (Part 2+)
      └── CheckpointService  (Part 3)
            │
            ▼
      MongoDB Atlas
            ├── knowledge_chunks   (embeddings + runbooks)
            ├── conversations      (short-term memory, Part 2+)
            ├── memories           (long-term memory, Part 2+)
            ├── checkpoints        (workflow state, Part 3)
            └── tool_executions    (audit trail, Part 3)
```

---

## Tech stack

| Component | Technology |
|-----------|-----------|
| Runtime | Java 21 + Spring Boot 3.4 |
| AI orchestration | Spring AI 1.0.0 |
| Chat model | OpenAI GPT-4o |
| Embedding model | OpenAI text-embedding-3-small (1536 dims) |
| Vector + document store | MongoDB Atlas (M0 free tier) |
| UI | Thymeleaf + Bootstrap 5 |

---

## Prerequisites

- **Java 21** JDK
- **Maven 3.9+**
- **MongoDB Atlas** account — free M0 cluster is sufficient ([create one here](https://www.mongodb.com/cloud/atlas/register))
- **OpenAI API key** ([platform.openai.com](https://platform.openai.com))

---

## Getting started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/operations-assistant.git
cd operations-assistant
```

To follow a specific article, check out the corresponding tag:

```bash
git checkout v1-rag
```

### 2. Create the Atlas Vector Search index

In the Atlas UI, navigate to your cluster → **Atlas Search** → **Create Search Index** → **JSON Editor**, select the `ops_assistant` database and the `knowledge_chunks` collection, then paste:

```json
{
  "fields": [
    {
      "type": "vector",
      "path": "embedding",
      "numDimensions": 1536,
      "similarity": "cosine"
    },
    { "type": "filter", "path": "sourceType" },
    { "type": "filter", "path": "system" },
    { "type": "filter", "path": "environment" },
    { "type": "filter", "path": "severity" },
    { "type": "filter", "path": "team" }
  ]
}
```

Name the index **`knowledge_vector_index`**.

> **Note:** Atlas Vector Search indexes are eventually consistent. Wait a few seconds after creation before running your first query.

### 3. Set environment variables

```bash
# MongoDB Atlas connection string
export MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority"

# OpenAI API key
export OPENAI_API_KEY="sk-..."
```

On Windows (PowerShell):

```powershell
$env:MONGODB_URI  = "mongodb+srv://<user>:<password>@<cluster>.mongodb.net/..."
$env:OPENAI_API_KEY = "sk-..."
```

### 4. Run the application

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

### 5. Load sample runbooks

The project ships with four sample runbooks under `src/main/resources/runbooks/`:

| File | Content |
|------|---------|
| `runbook-cpu-investigation.md` | High CPU alert investigation on Kubernetes |
| `runbook-service-rollback.md` | Safe rollback procedure for a Kubernetes Deployment |
| `runbook-disk-alert.md` | Disk space alert investigation and remediation |
| `runbook-network-latency.md` | Network latency investigation between microservices |

Load them with a single API call:

```bash
curl -X POST http://localhost:8080/api/ops/knowledge/ingest/samples
```

Or click **Load Sample Runbooks** in the UI.

---

## API reference (Part 1)

### Ingest a document

```
POST /api/ops/knowledge/ingest
Content-Type: application/json
```

```json
{
  "content": "## Runbook: ...",
  "sourceType": "runbook",
  "system": "payment-service",
  "environment": "prod",
  "severity": "high",
  "team": "operations"
}
```

Response:

```json
{ "chunks": 3, "status": "ingested" }
```

### Load sample runbooks

```
POST /api/ops/knowledge/ingest/samples
```

### Ask a question

```
POST /api/ops/chat
Content-Type: application/json
```

```json
{
  "message": "How do I investigate a high CPU alert on a Java service?",
  "system": "payment-service",
  "environment": "prod"
}
```

Response:

```json
{ "answer": "..." }
```

The `system` and `environment` fields are optional metadata filters — they restrict the vector search to chunks matching those values.

---

## Project structure

```
src/
├── main/
│   ├── java/it/matteoroxis/opsassistant/
│   │   ├── OpsAssistantApplication.java
│   │   ├── api/
│   │   │   ├── ChatController.java          POST /api/ops/chat
│   │   │   ├── KnowledgeController.java     POST /api/ops/knowledge/ingest
│   │   │   └── dto/                         Request/response records
│   │   ├── config/
│   │   │   └── ChatConfig.java              ChatClient + QuestionAnswerAdvisor bean
│   │   ├── service/
│   │   │   ├── IngestionService.java        Chunk → embed → store
│   │   │   └── KnowledgeRetrievalService.java  Direct similarity search
│   │   └── web/
│   │       └── WebController.java           Serves index.html
│   └── resources/
│       ├── application.yml
│       ├── runbooks/                         Sample runbooks for demo
│       └── templates/index.html             Bootstrap 5 UI
└── test/
    └── java/...
```

---

## Configuration reference

All properties can be overridden via environment variables or `application.yml`.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.data.mongodb.uri` | `MONGODB_URI` | `mongodb://localhost:27017` | Atlas connection string |
| `spring.data.mongodb.database` | `MONGODB_DATABASE` | `ops_assistant` | Database name |
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | — | OpenAI API key |
| `spring.ai.openai.chat.options.model` | — | `gpt-4o` | Chat model |
| `spring.ai.openai.embedding.options.model` | — | `text-embedding-3-small` | Embedding model |
| `spring.ai.vectorstore.mongodb.collection-name` | — | `knowledge_chunks` | Collection for embeddings |
| `spring.ai.vectorstore.mongodb.index-name` | — | `knowledge_vector_index` | Atlas Search index name |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
