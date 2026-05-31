# Code Bottlenecks

Анализ узких мест в коде — 46 найденных проблем.

## ⚡ Performance (6)

| # | Проблема | Файл |
|---|----------|------|
| 1 | `LongPollingSingleThreadUpdateConsumer` — очередь юзеров за одним LLM-вызовом (до 60s) | `TelegramBot.java:15` |
| 2 | `CompletableFuture` без `orTimeout()` — зависший источник не разблокируется | `HybridRagEngine.java:43` |
| 3 | `storeDocuments` через `forEach` — N+1 Redis вызовов за пакет | `RedisVectorStore.java:72-73` |
| 4 | `KEYS *` — блокирует Redis O(n) в проде | `RedisVectorStore.java:126` |
| 5 | `LLEN` + `LPOP` вместо `LTRIM` | `ConversationMemory.java:40-43` |
| 6 | `ForkJoinPool.commonPool()` без executor | `HybridRagEngine.java:36` |

## 🛡 Resilience (9)

| # | Проблема | Файл |
|---|----------|------|
| 7 | Нет retry на HTTP (5xx, network timeout) | `OpenRouterClient.java:69,111` |
| 8 | Нет circuit breaker — отказ LLM/Redis каскадит | — |
| 9 | Redis-exceptions silently swallowed | `ConversationMemory.java:44-46` |
| 10 | Ошибка LLM возвращается как строка → идёт в tool parser | `OpenRouterClient.java:74` |
| 11 | Нет per-source error isolation — один упавший источник валит весь search | `HybridRagEngine.java:43` |
| 12 | `hkeys(KEY)` без catch — JedisException крашит поток | `ExpertRegistry.java:51` |

## 🔒 Concurrency (2)

| # | Проблема | Файл |
|---|----------|------|
| 13 | `RPUSH+EXPIRE+LLEN+LPOP` не атомарны — race condition на history cap | `ConversationMemory.java:37-43` |
| 14 | `expert.telegramChatId()` — null → NPE (autounbox) | `ExpertResolver.java:39` |

## 🏗 Design (10)

| # | Проблема | Файл |
|---|----------|------|
| 15 | `instanceof AtlassianMcpStub` — нарушение LSP | `AtlassianKnowledgeSource.java:24` |
| 16 | Циркулярная зависимость `AgentOrchestrator` ↔ `TelegramBot` | `AgentOrchestrator.java:42` |
| 17 | `buildRagPrompt` с side-effect (мутирует ctx) | `RagEngine.java:60-64` |
| 18 | Парсинг tool call через `indexOf("/tool(")` — кривой | `ToolRegistry.java:44-58` |
| 19 | Source weights завязаны на имена ("confluence", "redis-vector") | `SourceMerger.java:22-28` |

## 📊 Observability (6)

| # | Проблема | Файл |
|---|----------|------|
| 20 | Нет tracing (Micrometer / OpenTelemetry) | — |
| 21 | Нет метрик (latency, RAG per-source, error rates) | — |
| 22 | `log.error` без `, e` — теряем stacktrace | `ConversationMemory.java:45,61` |
| 23 | Нет per-stage timing в handle() | `AgentOrchestrator.java:58` |

## ⚙ Config (4)

| # | Проблема | Файл |
|---|----------|------|
| 24 | Timeouts hardcoded вместо `@Value` | `OpenRouterClient.java:32-36` |
| 25 | Redis pool settings hardcoded | `RedisConfig.java:24-31` |
| 26 | TTL + prefix memory hardcoded | `ConversationMemory.java:20` |

## 🔐 Security (5)

| # | Проблема | Файл |
|---|----------|------|
| 27 | `LLMProperties` record → `toString()` включает API key | `LLMConfig.java:36-44` |
| 28 | Нет HTTPS enforcement на base-url | `OpenRouterClient.java:64` |
| 29 | Exception message летит в Telegram пользователю | `TelegramBot.java:60` |
