# Hybrid RAG AI Agent for 2nd Line Support

> L2/L3 engineers spend 40+ minutes per incident manually searching past tickets and runbooks.  
> This agent cuts that — routing questions to the right expert and surfacing relevant context automatically.

RAG Fusion over Confluence knowledge base + activity-based expert scoring + Telegram interface.  
Built in Java 21 / Spring Boot 3.4. Runs in Docker Compose in one command.

---

## The Problem

When an incident hits, an engineer has to:
1. Manually search Confluence for relevant runbooks
2. Remember (or ask around) who owns this service/topic
3. Wait for a response from whoever might know

This agent replaces steps 1–3 with an automated pipeline:  
**question → retrieve context → find the right expert → forward with context**.

---

## Architecture

```
┌──────────────┐     ┌────────────────────────────────────────────────────────────┐
│   Telegram   │     │                Hybrid RAG Agent                            │
│   User       │◄───►│                                                            │
└──────────────┘     │  ┌───────────┐  ┌────────────────────────────────────┐     │
                     │  │ Telegram  │  │        AgentOrchestrator            │     │
                     │  │ Bot       │──►                                    │     │
                     │  │ +Answer   │  │  ┌──────────────────────────────┐  │     │
                     │  │  Tracker  │  │  │ RagFusion                    │  │     │
                     │  └───────────┘  │  │  QueryParaphraser (×3)       │  │     │
                     │                 │  │  → HybridRetrieve (×4 async) │  │     │
                     │                 │  │  → ReciprocalRankFusion       │  │     │
                     │                 │  └──────────────────────────────┘  │     │
                     │                 │                                    │     │
                     │                 │  ┌──────────────────────────────┐  │     │
                     │                 │  │ ExpertRouter                 │  │     │
                     │                 │  │  QuestionClassifier          │  │     │
                     │                 │  │  ExpertResolver              │  │     │
                     │                 │  │   ├─ AnswerTracker (Redis)   │  │     │
                     │                 │  │   ├─ AtlassianMcpStub        │  │     │
                     │                 │  │   └─ ExpertRegistry          │  │     │
                     │                 │  └──────────────────────────────┘  │     │
                     │                 │                                    │     │
                     │                 │  ┌────────────┐ ┌──────────────┐  │     │
                     │                 │  │ ConvMemory │ │ ToolRegistry  │  │     │
                     │                 │  └────────────┘ └──────────────┘  │     │
                     │                 └──────────┬─────────────────────────┘     │
                     │                            │                               │
                     │                 ┌──────────▼────────────────────────┐      │
                     │                 │        OpenRouter API              │      │
                     │                 │     (LLM + Embeddings)             │      │
                     │                 └───────────────────────────────────┘      │
                     └────────────────────────────────────────────────────────────┘
```

---

## RAG Fusion Pipeline

Classic single-query RAG misses semantically equivalent questions phrased differently.  
RAG Fusion generates multiple query variants and merges results via Reciprocal Rank Fusion:

```
User query
    │
    ├── QueryParaphraser → 3 paraphrased variants via LLM
    │
    ├── allQueries = [original, p1, p2, p3]
    │
    ├── CompletableFuture (parallel):
    │     q1 → HybridRagEngine → Redis FT.SEARCH + Confluence
    │     q2 → HybridRagEngine → Redis FT.SEARCH + Confluence
    │     q3 → HybridRagEngine → Redis FT.SEARCH + Confluence
    │     q4 → HybridRagEngine → Redis FT.SEARCH + Confluence
    │
    └── ReciprocalRankFusion.merge()
          score(d) = Σ 1/(60 + rank_i(d))
          → fused + reranked List<Document>
```

**Why RRF over simple score averaging:** documents appearing in multiple query results get boosted proportionally to their rank consistency, not just raw similarity scores. This handles vocabulary mismatch without tuning per-source weights.

---

## Expert Resolver Chain

When the classifier identifies a known topic with sufficient confidence, `ExpertResolver` finds the right person — not just by static assignment, but by actual activity:

```
ExpertResolver.resolve(topic)
    │
    ├── 1. AnswerTracker.getTopExperts(topic)
    │         ZREVRANGE activity:{topic} — who actually answered lately
    │         score >= expert.activity.threshold → pick this person
    │
    ├── 2. AtlassianMcpClient.getPageOwner(topic)
    │         Last editor of the Confluence page owns the topic
    │         (stub in dev, real MCP endpoint in prod)
    │
    ├── 3. ExpertRegistry.getExperts(topic)
    │         Static fallback — manually maintained Redis hash
    │
    └── 4. Optional.empty() → ANSWER_DIRECTLY
```

**Why this order matters:** static registries go stale. Activity tracking reflects who is *actually* helping today, not who was assigned 6 months ago.

---

## Answer Activity Tracking

Every reply in Telegram closes the feedback loop:

```
User replies to a question
    │
    ├── TelegramBot detects reply_to_message
    ├── AnswerTracker.trackAnswer(chatId, replyToMessageId, username)
    │     → looks up topic by questionMessageId (TTL 24h)
    │     → ZINCRBY activity:{topic} 1 username
    │     → SETEX lastSeen:{topic}:{username} (30d TTL)
    │
    └── ExpertResolver reads this score next time
```

```bash
# Inspect expert activity for a topic:
redis-cli ZREVRANGE activity:kafka 0 -1 WITHSCORES
```

---

## Expert Escalation Message

When routing to an expert, the bot forwards a structured message to their Telegram DM:

```
📌 New question about: kafka
From: @engineer_name

How do I tune Kafka consumer lag for a high-throughput topic?

— sent via KnowledgeBot
```

The general chat receives: *"Forwarded to @kafka_owner — they own this area. Usually responds within an hour."*

---

## Atlassian MCP — Stub vs Production

```java
@Component
@Profile("!prod")   // stub only in dev/staging
public class AtlassianMcpStub implements AtlassianMcpClient {
    // 8 fake Confluence pages: kafka, spark, data-vault,
    // incident-response, redis, yarn, etl-pipeline, data-quality
}
```

In production: swap `AtlassianMcpStub` for a real MCP endpoint bean with `@Profile("prod")`.  
**Routing and RAG logic are unchanged** — the interface contract stays the same.

---

## Stack

| Component      | Technology                                              |
|----------------|---------------------------------------------------------|
| Language        | Java 21                                                |
| Framework       | Spring Boot 3.4                                        |
| Telegram API    | telegrambots-spring-boot-starter 7.11                  |
| LLM Provider    | OpenRouter (openai/gpt-4o-mini)                        |
| Vector Store    | Redis Stack (RediSearch + JSON + KNN Vector Search)    |
| Enterprise KB   | Atlassian MCP (Confluence) — stub, `@Profile("!prod")` |
| HTTP Client     | OkHttp 4                                               |
| Build           | Maven                                                  |
| Deploy          | Docker Compose                                         |

---

## Project Structure

```
src/main/java/com/hybridrag/
├── HybridRagAgentApplication.java
│
├── config/
│   ├── RedisConfig.java                 # Jedis Pooled connection
│   └── LLMConfig.java                   # OpenRouter + embedding properties
│
├── model/
│   ├── ChatMessage.java                 # role + content + timestamp
│   ├── Document.java                    # id + content + embedding + metadata
│   └── QueryContext.java                # query + history + username + retrieved docs
│
├── bot/
│   ├── TelegramBot.java                 # Long-polling + forwardToExpert
│   └── BotConfig.java                   # Token + username beans
│
├── llm/
│   ├── LLMService.java                  # Interface: chat + embed
│   └── OpenRouterClient.java            # HTTP calls to OpenRouter API
│
├── knowledge/
│   ├── KnowledgeSource.java             # Common retrieval interface
│   ├── RedisKnowledgeSource.java        # VectorStore → KnowledgeSource adapter
│   ├── AtlassianMcpClient.java          # Atlassian MCP interface
│   ├── AtlassianMcpStub.java            # @Profile("!prod") dev stub
│   ├── AtlassianKnowledgeSource.java    # MCP → KnowledgeSource adapter
│   └── ConfluencePage.java              # record: pageId, title, spaceKey, body
│
├── rag/
│   ├── HybridRagEngine.java             # Parallel search across all KnowledgeSources
│   ├── SourceMerger.java                # Weighted rerank by source
│   ├── RagFusion.java                   # Fusion orchestrator
│   ├── QueryParaphraser.java            # LLM → N paraphrased queries
│   ├── ReciprocalRankFusion.java        # RRF: score = Σ 1/(k + rank)
│   ├── EmbeddingService.java            # Wrapper over LLMService.embed
│   └── DocumentChunker.java            # Text chunking strategies
│
├── routing/
│   ├── ExpertRegistry.java              # Redis hash: topic → [experts]
│   ├── ExpertProperties.java            # @ConfigurationProperties(prefix = "expert")
│   ├── ExpertResolver.java              # Chain: activity → Confluence → registry
│   ├── ExpertRouter.java                # Classifier + Resolver → escalation decision
│   ├── QuestionClassifier.java          # LLM classification with conversation history
│   └── AnswerTracker.java               # ZINCRBY activity tracking
│
├── agent/
│   ├── AgentOrchestrator.java           # Main agent loop
│   └── ConversationMemory.java          # Redis-backed conversation history
│
├── store/
│   ├── VectorStore.java                 # Interface: similarity search
│   └── RedisVectorStore.java            # Redis JSON + FT.SEARCH FLAT COSINE
│
└── tool/
    ├── Tool.java                        # Interface: name + execute
    ├── ToolRegistry.java                # Plugin registry + call parsing
    └── WeatherTool.java                 # Example tool stub
```

---

## Configuration

```yaml
expert:
  activity:
    threshold: 5          # min activity score to trust over static registry
    window-days: 30       # lookback window for activity scoring
  routing:
    threshold: 0.6        # classifier confidence threshold for escalation

rag:
  rrf-k: 60               # RRF constant

spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 10
        queue-capacity: 50
```

## Environment Variables

| Variable                        | Default                 | Description                        |
|---------------------------------|-------------------------|------------------------------------|
| `TELEGRAM_BOT_TOKEN`            | —                       | Telegram bot token                 |
| `TELEGRAM_BOT_USERNAME`         | —                       | Bot username                       |
| `OPENROUTER_API_KEY`            | —                       | OpenRouter API key                 |
| `OPENROUTER_MODEL`              | openai/gpt-4o-mini      | Chat model                         |
| `REDIS_HOST`                    | localhost               | Redis host                         |
| `REDIS_PORT`                    | 6379                    | Redis port                         |
| `EMBEDDING_MODEL`               | text-embedding-3-small  | Embedding model                    |
| `EMBEDDING_DIMENSION`           | 1536                    | Vector dimension                   |
| `EXPERT_ROUTING_THRESHOLD`      | 0.6                     | Classifier confidence threshold    |
| `EXPERT_ACTIVITY_THRESHOLD`     | 5                       | Min score for activity-based pick  |
| `EXPERT_ACTIVITY_WINDOW_DAYS`   | 30                      | Activity lookback window           |
| `RRF_K`                         | 60                      | RRF constant k                     |
| `RAG_WEIGHT_CONFLUENCE`         | 0.6                     | Confluence source weight           |
| `RAG_WEIGHT_REDIS`              | 0.4                     | Redis vector source weight         |

---

## Quick Start

```bash
cp .env.example .env      # fill in your tokens
docker compose up -d      # Redis Stack + agent
```

Send a message to your bot in Telegram. If it looks like a technical question (contains `?` + embedding similarity > 0.65), the agent will retrieve context and either answer directly or escalate to an expert.

---

## Extending with Custom Tools

```java
@Component
public class MyTool implements Tool {
    public MyTool(ToolRegistry registry) { registry.register(this); }
    public String getName() { return "mytool"; }
    public String execute(String args, QueryContext ctx) { return "result"; }
}
```

---

## Roadmap

- [x] Hybrid RAG (Redis vector + Confluence MCP stub)
- [x] RAG Fusion (paraphrase → N×parallel retrieve → RRF)
- [x] Activity-based expert resolver chain
- [x] Answer tracking feedback loop (ZINCRBY)
- [x] Conversation history in classifier context
- [x] Real Telegram forwarding to expert DM
- [x] Docker Compose deploy
- [ ] REST API for document indexing (`POST /api/documents/index`)
- [ ] PDF / URL ingestion pipeline
- [ ] Unit + integration tests
- [ ] Eval benchmark (retrieval precision, answer faithfulness)
- [ ] LangFuse observability integration
- [ ] CI/CD (GitHub Actions)
- [ ] GraphRAG layer for multi-hop Confluence page relationships
