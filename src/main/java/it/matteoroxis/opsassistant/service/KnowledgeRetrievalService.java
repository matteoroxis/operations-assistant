package it.matteoroxis.opsassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Exposes semantic search over the knowledge base as an explicit service.
 * In Article 1 this is used directly in tests and diagnostic endpoints;
 * the ChatClient uses the same store internally via QuestionAnswerAdvisor.
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorStore vectorStore;

    public KnowledgeRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Plain semantic search — no metadata filtering.
     */
    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("search: found {} result(s) for query '{}'", results.size(), query);
        return results;
    }

    /**
     * Semantic search with optional system and/or environment pre-filter.
     * Provides narrower, more relevant results when the caller knows the target system.
     */
    public List<Document> searchWithFilter(String query, int topK, String system, String environment) {
        Optional<Filter.Expression> filter = buildFilter(system, environment);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);
        filter.ifPresent(builder::filterExpression);

        List<Document> results = vectorStore.similaritySearch(builder.build());
        log.debug("searchWithFilter: found {} result(s) [system={}, environment={}]",
                results.size(), system, environment);
        return results;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<Filter.Expression> buildFilter(String system, String environment) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expr = null;

        if (system != null && !system.isBlank()) {
            expr = b.eq("system", system);
        }
        if (environment != null && !environment.isBlank()) {
            FilterExpressionBuilder.Op envExpr = b.eq("environment", environment);
            expr = (expr != null) ? b.and(expr, envExpr) : envExpr;
        }

        return Optional.ofNullable(expr).map(FilterExpressionBuilder.Op::build);
    }
}
