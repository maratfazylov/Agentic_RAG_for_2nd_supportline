# Hybrid RAG AI Agent

> Telegram bot + OpenRouter LLM + Redis Vector Store + Atlassian MCP + Expert Routing + RAG Fusion — в Docker

## Архитектура

```
┌──────────────┐     ┌────────────────────────────────────────────────────────────┐
│   Telegram   │     │                Hybrid RAG Agent                            │
│   User       │◄───►│                                                            │
└──────────────┘     │  ┌───────────┐  ┌────────────────────────────────────┐     │
                     │  │ Telegram  │  │        AgentOrchestrator            │     │
                     │  │ Bot       │──►                                    │     │
                     │  │ +AnswerTr │  │  ┌──────────────────────────────┐  │     │
                     │  │  tracker  │  │  │ RagFusion                     │  │     │
                     │  └───────────┘  │  │  ┌────────────────────────┐  │  │     │
                     │                 │  │  │ QueryParaphraser (×3)  │  │  │     │
                     │                 │  │  │   → HybridRetrieve(×4) │  │  │     │
                     │                 │  │  │   → ReciprocalRankFus. │  │  │     │
                     │                 │  │  └────────────────────────┘  │  │     │
                     │                 │  └──────────────────────────────┘  │     │
                     │                 │                                    │     │
                     │                 │  ┌──────────────────────────────┐  │     │
                     │                 │  │ ExpertRouter                 │  │     │
                     │                 │  │  → QuestionClassifier        │  │     │
                     │                 │  │  → ExpertResolver            │  │     │
                     │                 │  │     ├─ AnswerTracker          │  │     │
                     │                 │  │     ├─ AtlassianMcpStub       │  │     │
                     │                 │  │     └─ ExpertRegistry         │  │     │
                     │                 │  └──────────────────────────────┘  │     │
                     │                 │                                    │     │
                     │                 │  ┌────────────┐ ┌──────────────┐  │     │
                     │                 │  │ ConvMemory │ │ ToolRegistry  │  │     │
                     │                 │  └────────────┘ └──────────────┘  │     │
                     │                 └──────────┬─────────────────────────┘     │
                     │                            │                               │
                     │                 ┌──────────▼────────────────────────┐      │
                     │                 │          OpenRouter API           │      │
                     │                 │     (LLM + Embeddings)            │      │
                     │                 └───────────────────────────────────┘      │
                     └────────────────────────────────────────────────────────────┘
```

## Стек

| Компонент            | Технология                                          |
|----------------------|------------------------------------------------------|
| Язык                 | Java 21                                              |
| Фреймворк            | Spring Boot 3.4                                      |
| Telegram API         | telegrambots-spring-boot-starter 7.11                |
| LLM Provider         | OpenRouter (openai/gpt-4o-mini)                      |
| Vector Store         | Redis Stack (RediSearch + JSON + Vector Search)      |
| Enterprise KB        | Atlassian MCP (Confluence) — stub, `@Profile("!prod")` |
| HTTP Client          | OkHttp 4                                             |
| JSON                 | Jackson                                              |
| Сборка               | Maven                                                |
| Деплой               | Docker Compose                                       |

## Структура проекта

```
src/main/java/com/hybridrag/
├── HybridRagAgentApplication.java       # Точка входа (Spring Boot)
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
│   ├── TelegramBot.java                 # Long-polling consumer + forwardToExpert
│   └── BotConfig.java                   # Token + username beans
│
├── llm/
│   ├── LLMService.java                  # Interface: chat + embed
│   └── OpenRouterClient.java            # HTTP вызовы OpenRouter API
│
├── knowledge/
│   ├── KnowledgeSource.java             # Общий интерфейс источника знаний
│   ├── RedisKnowledgeSource.java        # Адаптер VectorStore → KnowledgeSource
│   ├── AtlassianMcpClient.java          # Интерфейс Atlassian MCP
│   ├── AtlassianMcpStub.java            # @Profile("!prod"), getPageOwner
│   ├── AtlassianKnowledgeSource.java    # Адаптер MCP → KnowledgeSource
│   └── ConfluencePage.java              # record: pageId, title, spaceKey, body, ...
│
├── rag/
│   ├── RagEngine.java                   # Retrieval + prompt builder (fallback)
│   ├── HybridRagEngine.java             # Параллельный search по всем KnowledgeSource
│   ├── SourceMerger.java                # Взвешенный rerank результатов
│   ├── RagFusion.java                   # Fusion оркестратор
│   ├── QueryParaphraser.java            # LLM → N перефразированных запросов
│   ├── ReciprocalRankFusion.java        # RRF: score = Σ 1/(k + rank)
│   ├── EmbeddingService.java            # Обёртка над LLMService.embed
│   └── DocumentChunker.java             # Разбивка текста на чанки
│
├── routing/
│   ├── ExpertRegistry.java              # Redis hash: topic → [эксперты]
│   ├── ExpertProperties.java            # @ConfigurationProperties(prefix = "expert")
│   ├── ExpertResolver.java              # Цепочка: activity → Confluence → registry
│   ├── ExpertRouter.java                # Classifier + Resolver → эскалация
│   ├── QuestionClassifier.java          # LLM-based классификация (+ history context)
│   └── AnswerTracker.java               # Activity tracking (ZINCRBY)
│
├── agent/
│   ├── AgentOrchestrator.java           # Главный цикл агента
│   └── ConversationMemory.java          # История диалогов в Redis
│
├── store/
│   ├── VectorStore.java                 # Interface: similarity search
│   └── RedisVectorStore.java            # Redis JSON + FT.SEARCH (FLAT, COSINE)
│
└── tool/
    ├── Tool.java                        # Interface: name + execute
    ├── ToolRegistry.java                # Регистратор + парсинг вызовов
    └── WeatherTool.java                 # Пример тула (заглушка)
```

## Hybrid RAG + Fusion Pipeline

```
User query
    │
    ├──► RagFusion.search(query, topK=10)
    │       │
    │       ├── QueryParaphraser.paraphrase(query, 3)
    │       │     → LLM генерирует 3 варианта
    │       │
    │       ├── allQueries = [original, p1, p2, p3]  ← 4 запроса
    │       │
    │       ├── Параллельно (CompletableFuture):
    │       │     q1 → HybridRagEngine.retrieve → Redis FT.SEARCH + Confluence
    │       │     q2 → HybridRagEngine.retrieve → Redis FT.SEARCH + Confluence
    │       │     q3 → HybridRagEngine.retrieve → Redis FT.SEARCH + Confluence
    │       │     q4 → HybridRagEngine.retrieve → Redis FT.SEARCH + Confluence
    │       │
    │       └── ReciprocalRankFusion.merge(perQueryResults)
    │             score(d) = Σ 1/(60 + rank_i(d))
    │             → fused + reranked List<Document>
    │
    ├──► ExpertRouter.route()
    │       QuestionClassifier.classify(query, topics, history)
    │       → topic + confidence
    │       Если confidence < threshold → ROUTE_TO_EXPERT
    │
    ├──► RagFusion.buildFusionPrompt(query, fusion)
    │       System + [FusedDocument[0..N]] + User query
    │
    ├──► LLM chat completion → OpenRouter
    │
    └──► Response → Telegram
```

## Expert Resolver Chain

При классификации вопроса в известный topic, `ExpertResolver` ищет эксперта в порядке приоритета:

```
ExpertResolver.resolve(topic)
    │
    ├── 1. AnswerTracker.getTopExperts(topic, 3)
    │       • ZREVRANGE activity:{topic}
    │       • score >= expert.activity.threshold (default: 5)
    │
    ├── 2. AtlassianMcpClient.getPageOwner(topic)
    │       • AtlassianMcpStub.PAGE_OWNERS map
    │       • В проде — Confluence page metadata
    │
    ├── 3. ExpertRegistry.getExperts(topic)
    │       • Статический Redis hash
    │
    └── 4. Optional.empty() → ANSWER_DIRECTLY
```

## Answer Tracking

`AnswerTracker` использует Redis для activity-based рейтинга:

- **`trackQuestion`** — `SETEX question:{chatId}:{msgId}` → topic (TTL 24h)
- **`trackAnswer`** — при reply в Telegram: `ZINCRBY activity:{topic}` +1 + `SETEX lastSeen:{topic}:{user}` (30d)
- **`getTopExperts`** — `ZREVRANGE activity:{topic}` WITHSCORES, фильтр по lastSeen

```bash
# Просмотреть топ экспертов по Kafka:
redis-cli ZREVRANGE activity:kafka 0 -1 WITHSCORES
```

## Atlassian MCP Stub

```java
@Component
@Profile("!prod")   // заглушка только в dev
public class AtlassianMcpStub implements AtlassianMcpClient { ... }
```

8 фейковых Confluence страниц по темам Kafka, Spark, Data Vault, Incident Response.  
Также содержит `PAGE_OWNERS` map для `getPageOwner()`.

В продакшене заменяется на реальный MCP endpoint — routing и RAG логика не меняются.

## Expert Routing

`ExpertRouter` использует `@ConfigurationProperties(prefix = "expert")`:

```yaml
expert:
  activity:
    threshold: 5        # минимальный score для выбора по activity
    window-days: 30
  routing:
    threshold: 0.6      # порог уверенности классификатора
```

`QuestionClassifier.classify()` получает контекст последних сообщений диалога.

При эскалации `TelegramBot.forwardToExpert()` шлёт эксперту личное сообщение:
```
📌 New question about: {topic}
From: @{username}

{question}

— sent via KnowledgeBot
```

## Переменные окружения

| Переменная                     | По умолчанию                   | Описание                           |
|--------------------------------|--------------------------------|------------------------------------|
| TELEGRAM_BOT_TOKEN             | —                              | Токен Telegram бота                |
| TELEGRAM_BOT_USERNAME          | —                              | Username бота                      |
| OPENROUTER_API_KEY             | —                              | API ключ OpenRouter                |
| OPENROUTER_MODEL               | openai/gpt-4o-mini             | Модель для chat                    |
| REDIS_HOST                     | localhost                      | Хост Redis                         |
| REDIS_PORT                     | 6379                           | Порт Redis                         |
| EMBEDDING_MODEL                | text-embedding-3-small         | Модель для embeddings              |
| EMBEDDING_DIMENSION            | 1536                           | Размерность вектора                |
| EXPERT_ROUTING_THRESHOLD       | 0.6                            | Порог уверенности для роутинга     |
| EXPERT_ACTIVITY_THRESHOLD      | 5                              | Мин. score для выбора по activity  |
| EXPERT_ACTIVITY_WINDOW_DAYS    | 30                             | Окно активности эксперта           |
| RRF_K                          | 60                             | Константа RRF (Reciprocal Rank)    |
| RAG_WEIGHT_CONFLUENCE          | 0.6                            | Вес источника Confluence           |
| RAG_WEIGHT_REDIS               | 0.4                            | Вес источника Redis vector         |

## Tool System

```java
@Component
public class MyTool implements Tool {
    public MyTool(ToolRegistry registry) { this.registry = registry; }
    @PostConstruct void init() { registry.register(this); }
    public String getName() { return "mytool"; }
    public String execute(String args, QueryContext ctx) { return "result"; }
}
```

## Быстрый старт

```bash
cp .env.example .env   # заполнить токены
docker compose up -d    # Redis Stack + агент
```

## Roadmap

- [x] Базовая архитектура (Spring Boot + Telegram + OpenRouter + Redis)
- [x] Hybrid RAG (Redis vector + Confluence MCP stub)
- [x] RAG Fusion (paraphrase → N×retrieve → RRF)
- [x] Expert routing (resolver chain: activity → Confluence → registry)
- [x] Answer activity tracking (ZINCRBY)
- [x] @ConfigurationProperties
- [x] Docker Compose
- [ ] REST API для индексации (`POST /api/documents/index`)
- [ ] OpenRouter function calling (нативный)
- [ ] Ingestion: PDF/TXT/URL
- [ ] Web UI
- [ ] Unit / integration tests
- [ ] CI/CD (GitHub Actions)
