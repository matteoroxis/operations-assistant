# Operations Assistant

> A stateful AI agent for IT/Operations teams — built with **Spring AI** and **MongoDB Atlas**.
>
> This project is the companion code for a 3-part article series published on [Foojay.io](https://foojay.io).

---

## Series overview

| Article | Topic |
|---------|-------|
| Part 1 | **RAG Foundation** — knowledge ingestion, semantic search, stateless Q&A |
| Part 2 | **Memory** — short-term conversational memory + long-term cross-session memory |
| Part 3 | **Stateful Agent** — checkpoint persistence, pause/resume, tool execution |


---

## What it does

The Operations Assistant helps IT and operations teams:

- Search operational **runbooks, SOPs, postmortems and alert notes** using semantic search
- Maintain **conversational continuity** within a session (short-term memory)
- Recall **persistent facts and preferences** across sessions (long-term memory)
- **Checkpoint a multi-step workflow** so a task can be suspended and resumed exactly where it left off

MongoDB Atlas acts as the **unified backend** for knowledge retrieval, long-term memory and execution state — no additional infrastructure required.

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
      │       ├── LongTermMemoryAdvisor      (long-term memory recall, Part 2+)
      │       ├── MessageChatMemoryAdvisor   (short-term memory, Part 2+)
      │       └── QuestionAnswerAdvisor      (RAG retrieval)
      ├── IngestionService
      ├── KnowledgeRetrievalService
      ├── MemoryService  (Part 2+)
      └── CheckpointService  (Part 3)
            │
            ▼
      MongoDB Atlas
            ├── knowledge_chunks   (embeddings + runbooks)
            ├── memories           (long-term memory, Part 2+)
            ├── checkpoints        (workflow state, Part 3)
            └── tool_executions    (audit trail, Part 3)

      In-process (JVM)
            └── InMemoryChatMemoryRepository  (short-term memory, Part 2)
```

> **Short-term memory note (Part 2):** Conversational history is kept in an in-process
> `InMemoryChatMemoryRepository` (Spring AI default). It is scoped to the JVM lifetime,
> which is sufficient for the article demo. A persistent alternative (JDBC, Cassandra, etc.)
> can be selected by adding the corresponding `spring-ai-starter-model-chat-memory-repository-*`
> dependency.

---

## Tech stack

| Component | Technology |
|-----------|-----------|
| Runtime | Java 21 + Spring Boot 3.4 |
| AI orchestration | Spring AI 1.0.0 |
| Chat model | OpenAI GPT-4o |
| Embedding model | OpenAI text-embedding-3-small (1536 dims) |
| Vector + document store | MongoDB Atlas (M0 free tier) |
| Short-term memory | Spring AI `InMemoryChatMemoryRepository` |
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


### 2. Create the Atlas Vector Search indexes

#### Knowledge index (Part 1+)

In the Atlas UI, navigate to your cluster → **Atlas Search** → **Create Search Index** → **JSON Editor**, select the `ops_assistant` database and the `knowledge_chunks` collection, then paste:

```json
{
  "fields": [
    { "type": "vector", "path": "embedding", "numDimensions": 1536, "similarity": "cosine" },
    { "type": "filter", "path": "sourceType" },
    { "type": "filter", "path": "system" },
    { "type": "filter", "path": "environment" },
    { "type": "filter", "path": "severity" },
    { "type": "filter", "path": "team" }
  ]
}
```

Name the index **`knowledge_vector_index`**.

#### Memories index (Part 2+)

Create a second index on the `memories` collection:

```json
{
  "fields": [
    { "type": "vector", "path": "embedding", "numDimensions": 1536, "similarity": "cosine" },
    { "type": "filter", "path": "userId" },
    { "type": "filter", "path": "memoryType" }
  ]
}
```

Name the index **`memories_vector_index`**.

#### TTL index on checkpoints (Part 3)

In Atlas UI, navigate to the `checkpoints` collection → **Indexes** → **Create Index**:

```json
{ "expiresAt": 1 }
```

Set **"Expire after"** to `0` seconds (MongoDB will delete documents once the current time surpasses the value stored in `expiresAt`). Checkpoints are given a 7-day TTL on creation.

On Atlas M10+ you can set `spring.data.mongodb.auto-index-creation=true` and Spring Data will create the index automatically on startup.

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
$env:MONGODB_URI     = "mongodb+srv://<user>:<password>@<cluster>.mongodb.net/..."
$env:OPENAI_API_KEY  = "sk-..."
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

## API reference

### Knowledge

#### Ingest a document

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

Response: `{ "chunks": 3, "status": "ingested" }`

#### Load sample runbooks

```
POST /api/ops/knowledge/ingest/samples
```

---

### Chat (Part 1+)

```
POST /api/ops/chat
Content-Type: application/json
```

**Part 1 payload:**
```json
{
  "message": "How do I investigate a high CPU alert on a Java service?",
  "system": "payment-service",
  "environment": "prod"
}
```

**Part 2+ payload** (adds `conversationId` and `userId`):
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "ops-user",
  "message": "How do I investigate a high CPU alert on a Java service?",
  "system": "payment-service",
  "environment": "prod"
}
```

- `conversationId`: optional — a new UUID is generated and returned if omitted. Pass the value from one response into the next request to maintain conversation continuity.
- `userId`: used to recall relevant long-term memories before answering.
- `system` / `environment`: optional metadata filters for the RAG vector search.

**Part 2 response:**
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": "..."
}
```

---

### Memory (Part 2+)

#### List memories for a user

```
GET /api/ops/memories/{userId}
```

Response: array of memory records.

```json
[
  {
    "id": "...",
    "userId": "ops-user",
    "content": "User prefers rollback via Helm rather than kubectl.",
    "memoryType": "PREFERENCE",
    "importanceScore": 0.8,
    "sourceConversationId": "550e8400-...",
    "score": 0.0
  }
]
```

#### Consolidate a conversation to long-term memory

Triggers the LLM to read the specified conversation and extract durable facts, preferences and decisions, saving them in the `memories` collection.

```
POST /api/ops/chat/{conversationId}/consolidate?userId={userId}
```

Response: `{ "conversationId": "...", "userId": "...", "memoriesSaved": 3 }`

---

### Workflow state & resume (Part 3)

#### Get current checkpoint

Returns the latest persisted checkpoint for a conversation.

```
GET /api/ops/chat/{conversationId}/state
```

Response: `Checkpoint` document (HTTP 200), or HTTP 404 if no checkpoint exists.

```json
{
  "checkpointId": "...",
  "conversationId": "550e8400-...",
  "taskId": "...",
  "workflowName": "incident-investigation",
  "currentStep": "AWAITING_APPROVAL",
  "status": "WAITING_APPROVAL",
  "stateData": {
    "lastUserMessage": "Investigate high CPU on payment-service",
    "lastAnswer": "..."
  },
  "toolExecutionRefs": ["..."],
  "createdAt": "2026-04-14T10:00:00Z",
  "updatedAt": "2026-04-14T10:01:30Z",
  "expiresAt": "2026-04-21T10:01:30Z"
}
```

#### Resume a paused workflow

Re-hydrates the checkpoint into a prompt and continues the conversation from where it was interrupted.

```
POST /api/ops/chat/{conversationId}/resume?userId={userId}
```

Response: same shape as `POST /api/ops/chat`.

---

### Demo scenario (Part 3)

1. Send: `"Investigate high CPU alert on payment-service in prod"` — a `Checkpoint` is created with `status=RUNNING`
2. Spring AI calls `ServiceStatusTool` → mocked CPU metrics are returned, stored in `tool_executions`, and linked to the checkpoint
3. The model proposes next steps; checkpoint advances to `status=WAITING_APPROVAL`
4. Close the browser (session ends)
5. Re-open the UI with the same `conversationId` → click **Resume Task** in the Workflow State panel
6. `POST /resume` re-injects the checkpoint state; the model recaps the investigation and proposes the next action

---

## Project structure

```
src/
├── main/
│   ├── java/it/matteoroxis/opsassistant/
│   │   ├── OpsAssistantApplication.java
│   │   ├── advisor/
│   │   │   └── LongTermMemoryAdvisor.java   Injects recalled memories (Part 2+)
│   │   ├── api/
│   │   │   ├── ChatController.java          POST /api/ops/chat
│   │   │   ├── KnowledgeController.java     POST /api/ops/knowledge/ingest
│   │   │   ├── MemoryController.java        GET /api/ops/memories, POST /consolidate (Part 2+)
│   │   │   └── dto/                         Request/response records
│   │   ├── config/
│   │   │   ├── ChatConfig.java              ChatClient + advisor chain + tool registration
│   │   │   ├── MongoMemoryConfig.java       Second VectorStore bean for memories (Part 2+)
│   │   │   └── ToolConfig.java             ServiceStatusTool bean (Part 3)
│   │   ├── domain/
│   │   │   ├── Checkpoint.java             Workflow state document — checkpoints collection (Part 3)
│   │   │   ├── CheckpointStatus.java       Enum: RUNNING / WAITING_INPUT / WAITING_APPROVAL / COMPLETED / FAILED (Part 3)
│   │   │   ├── MemoryRecord.java            Memory projection record (Part 2+)
│   │   │   ├── MemoryType.java             Enum: PREFERENCE / FACT / SUMMARY / EPISODE / DECISION (Part 2+)
│   │   │   └── ToolExecution.java          Tool call audit record — tool_executions collection (Part 3)
│   │   ├── repository/
│   │   │   └── CheckpointRepository.java   MongoRepository + findTopByConversationIdOrderByUpdatedAtDesc (Part 3)
│   │   ├── service/
│   │   │   ├── CheckpointService.java      create / updateStep / loadLatest / markCompleted / markFailed (Part 3)
│   │   │   ├── IngestionService.java        Chunk → embed → store
│   │   │   ├── KnowledgeRetrievalService.java  Direct similarity search
│   │   │   └── MemoryService.java           Long-term memory CRUD + consolidation (Part 2+)
│   │   ├── tool/
│   │   │   └── ServiceStatusTool.java      @Tool — mocked service health metrics + ToolExecution audit (Part 3)
│   │   ├── util/
│   │   │   └── ConversationContextHolder.java  ThreadLocal conversationId propagation into tools (Part 3)
│   │   └── web/
│   │       └── WebController.java           Serves index.html
│   └── resources/
│       ├── application.yml
│       ├── runbooks/                         Sample runbooks for demo
│       └── templates/index.html             Bootstrap 5 UI (4-column layout in Part 3)
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
| `spring.ai.vectorstore.mongodb.collection-name` | — | `knowledge_chunks` | Knowledge embeddings collection |
| `spring.ai.vectorstore.mongodb.index-name` | — | `knowledge_vector_index` | Atlas Search index for knowledge |
| `ops-assistant.memory.collection-name` | — | `memories` | Long-term memory collection (Part 2+) |
| `ops-assistant.memory.index-name` | — | `memories_vector_index` | Atlas Search index for memories (Part 2+) |
| `ops-assistant.memory.top-k` | — | `5` | Max memories recalled per request (Part 2+) |
| `ops-assistant.memory.similarity-threshold` | — | `0.6` | Min similarity score for memory recall (Part 2+) |
| `spring.data.mongodb.auto-index-creation` | — | `false` | Set to `true` on Atlas M10+ to auto-create the TTL index on `checkpoints.expiresAt`; on M0 create it manually (Part 3) |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
