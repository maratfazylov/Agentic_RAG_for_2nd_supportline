package com.hybridrag.store;

import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.Schema;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RedisVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisVectorStore.class);
    private static final String INDEX_NAME = "doc_idx";
    private static final String DOC_PREFIX = "doc:";
    private final JedisPooled jedis;
    private final int dimension;

    public RedisVectorStore(JedisPooled jedis,
                            com.hybridrag.config.LLMConfig.LLMProperties props) {
        this.jedis = jedis;
        this.dimension = props.embeddingDimension();
        initIndex();
    }

    private void initIndex() {
        try {
            if (jedis.ftList().contains(INDEX_NAME)) return;

            var schema = new Schema()
                .addTagField("$id")
                .addTextField("$source")
                .addTextField("$content")
                .addVectorField("$embedding", Schema.VectorField.VectorAlgo.FLAT,
                    new Schema.VectorField.VectorAttribute("TYPE", "FLOAT32"),
                    new Schema.VectorField.VectorAttribute("DIM", dimension),
                    new Schema.VectorField.VectorAttribute("DISTANCE_METRIC", "COSINE"));

            jedis.ftCreate(INDEX_NAME,
                FTCreateParams.createParams()
                    .onJSON()
                    .addPrefix(DOC_PREFIX),
                schema);

            log.info("Redis vector index '{}' initialized (dim={})", INDEX_NAME, dimension);
        } catch (Exception e) {
            log.warn("Could not create Redis index (might already exist): {}", e.getMessage());
        }
    }

    @Override
    public void storeDocument(Document doc) {
        try {
            String key = DOC_PREFIX + doc.getId();
            jedis.jsonSet(key, Path2.ROOT_PREFIX, doc);
            log.debug("Stored document {}", doc.getId());
        } catch (Exception e) {
            log.error("Failed to store document {}: {}", doc.getId(), e.getMessage());
        }
    }

    @Override
    public void storeDocuments(List<Document> documents) {
        documents.forEach(this::storeDocument);
    }

    @Override
    public List<Document> similaritySearch(List<Float> queryEmbedding, int topK) {
        return similaritySearch(queryEmbedding, topK, 0.0);
    }

    @Override
    public List<Document> similaritySearch(List<Float> queryEmbedding, int topK, double scoreThreshold) {
        List<Document> results = new ArrayList<>();
        try {
            var bb = ByteBuffer.allocate(queryEmbedding.size() * 4);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            for (float val : queryEmbedding) {
                bb.putFloat(val);
            }

            String queryStr = "*=>[KNN $K @$embedding $BLOB AS score]";
            var query = new Query(queryStr)
                .addParam("K", topK * 2)
                .addParam("BLOB", bb.array())
                .setSortBy("score", true)
                .returnFields("$content", "$source", "$id", "score")
                .limit(0, topK * 2);

            var result = jedis.ftSearch(INDEX_NAME, query);
            for (var docResult : result.getDocuments()) {
                var props = docResult.getProperties();
                double score = 1 - Double.parseDouble(props.get("score").toString());
                if (score < scoreThreshold) continue;

                var doc = new Document(
                    safeString(props, "$id"),
                    safeString(props, "$content"),
                    safeString(props, "$source")
                );
                results.add(doc);

                if (results.size() >= topK) break;
            }
        } catch (Exception e) {
            log.error("Similarity search failed: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public void deleteDocument(String documentId) {
        jedis.del(DOC_PREFIX + documentId);
    }

    @Override
    public void deleteAll() {
        var keys = jedis.keys(DOC_PREFIX + "*");
        if (!keys.isEmpty()) {
            jedis.del(keys.toArray(new String[0]));
        }
    }

    private String safeString(List<Object> props, String field) {
        int idx = props.indexOf(field);
        if (idx >= 0 && idx + 1 < props.size()) {
            Object val = props.get(idx + 1);
            return val != null ? val.toString() : "";
        }
        return "";
    }
}
