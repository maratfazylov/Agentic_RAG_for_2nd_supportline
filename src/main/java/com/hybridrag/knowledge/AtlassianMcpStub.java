package com.hybridrag.knowledge;

import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!prod")
public class AtlassianMcpStub implements AtlassianMcpClient {

    private static final Logger log = LoggerFactory.getLogger(AtlassianMcpStub.class);

    private static final Map<String, String> PAGE_OWNERS = Map.of(
        "kafka", "kafka_owner",
        "spark", "spark_owner",
        "data-vault", "dv_owner",
        "redis", "redis_owner"
    );

    private final List<ConfluencePage> pages;

    public AtlassianMcpStub() {
        this.pages = seedPages();
        log.info("AtlassianMcpStub initialized with {} Confluence pages", pages.size());
    }

    @Override
    public List<ConfluencePage> searchPages(String query, int limit) {
        var q = query.toLowerCase();
        var result = new ArrayList<ConfluencePage>();
        for (var page : pages) {
            if (result.size() >= limit) break;
            if (page.title().toLowerCase().contains(q)
                || page.body().toLowerCase().contains(q)
                || page.spaceKey().equalsIgnoreCase(q)) {
                result.add(page);
            }
        }
        log.debug("AtlassianMCP stub search for '{}': {} hits", query, result.size());
        return result;
    }

    @Override
    public ConfluencePage getPage(String pageId) {
        return pages.stream()
            .filter(p -> p.pageId().equals(pageId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Optional<String> getPageOwner(String topic) {
        return Optional.ofNullable(PAGE_OWNERS.get(topic.toLowerCase()));
    }

    @Override
    public List<Document> searchAsDocuments(String query, int limit) {
        return searchPages(query, limit).stream()
            .map(this::toDocument)
            .toList();
    }

    private Document toDocument(ConfluencePage page) {
        var doc = new Document(page.pageId(), page.title() + "\n" + page.body(), "confluence/" + page.spaceKey());
        doc.setMetadata(java.util.Map.of(
            "pageId", page.pageId(),
            "spaceKey", page.spaceKey(),
            "author", page.author(),
            "lastModified", page.lastModified()
        ));
        return doc;
    }

    private List<ConfluencePage> seedPages() {
        return List.of(
            new ConfluencePage("CF-001",
                "Kafka Cluster Setup and Best Practices",
                "DE",
                """
                Apache Kafka is used as the central event backbone.
                Key configuration:
                - replication.factor=3 for critical topics
                - min.insync.replicas=2
                - retention.ms=604800000 (7 days)
                - cleanup.policy=delete with compaction for keyed topics
                Schema Registry must be HTTPs with mTLS.
                Consumer groups should use cooperative rebalancing.
                """,
                localDate(-5), "alex.ivanov"),

            new ConfluencePage("CF-002",
                "Spark Structured Streaming — Production Checklist",
                "DE",
                """
                Micro-batch mode preferred over continuous for ML pipelines.
                Checkpoints must be on S3 with consistent listing.
                Trigger.ProcessingTime(10 seconds) for near-real-time.
                Use writeStream.option("checkpointLocation", ...).
                Always set spark.sql.streaming.schemaInference=true for JSON sources.
                Watermark delay: max 1 hour.
                """,
                localDate(-12), "maria.petrova"),

            new ConfluencePage("CF-003",
                "Data Vault 2.0 Methodology",
                "DE",
                """
                Core DV2 entities: Hub, Link, Satellite.
                Hash keys: SHA-256 on business keys.
                - Hub: holds business keys + load timestamp + record source.
                - Satellite: descriptive attributes, historized.
                - Link: relationships between hubs.
                Raw vault → Business vault → Data Marts.
                No updates, only inserts (append-only).
                """,
                localDate(-3), "dmitry.sokolov"),

            new ConfluencePage("CF-004",
                "Incident Response Process — P1/P2",
                "OPS",
                """
                1. DETECT: Grafana alert → OpsGenie → PagerDuty.
                2. TRIAGE: assign severity within 5 min.
                3. MITIGATE: rollback or hotfix, SLA depends on severity.
                4. RESOLVE: deploy fix, verify metrics.
                5. POSTMORTEM: RCA within 48h, Jira ticket in IT-OPS.
                Slack channel #incidents for comms.
                """,
                localDate(-1), "ops-team"),

            new ConfluencePage("CF-005",
                "Kafka Schema Evolution Rules",
                "DE",
                """
                Schema Registry compatibility rules:
                - BACKWARD (default): new schema can read old data.
                - FORWARD: old schema can read new data.
                - FULL: both directions.
                - NONE: no checks (only for dev).
                Always evolve with added optional fields.
                Never remove or change type of existing fields.
                """,
                localDate(-20), "alex.ivanov"),

            new ConfluencePage("CF-006",
                "Spark Tuning: Shuffle Partitions",
                "DE",
                """
                spark.sql.shuffle.partitions = cores * 2.
                For 100GB data with 20 executors: 200 partitions.
                Auto Broadcast Join threshold: 10MB.
                Use AQE (Adaptive Query Execution) — enabled by default.
                Skew join hint: /*+ SKEW(t1(key)) */.
                """,
                localDate(-8), "maria.petrova"),

            new ConfluencePage("CF-007",
                "Data Vault — Satellite Historization",
                "DE",
                """
                Satellite structure:
                - Hash key (FK to Hub)
                - Load date (LDTS)
                - Record source (RSRC)
                - All descriptive columns
                Historization: new record for each change.
                Use load date as part of PK.
                Track satellite: add hash diff column.
                """,
                localDate(-2), "dmitry.sokolov"),

            new ConfluencePage("CF-008",
                "Incident Postmortem Template",
                "OPS",
                """
                ## Summary
                ## Timeline
                ## Root Cause
                ## Impact
                ## Action Items
                ## Lessons Learned
                Required fields: incident-id, severity, duration, services-affected.
                Jira project: IT-OPS, issue type: Postmortem.
                """,
                localDate(-30), "ops-team")
        );
    }

    private String localDate(int daysAgo) {
        return LocalDate.now().plusDays(daysAgo).toString();
    }
}
