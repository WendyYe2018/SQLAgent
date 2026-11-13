package org.sqlagent.service;

import org.springframework.stereotype.Service;

@Service
public class PromptContextBuilder {

    private final EmbeddingService embeddingService;
    private final SchemaService schemaService;

    public PromptContextBuilder(EmbeddingService embeddingService, SchemaService schemaService) {
        this.embeddingService = embeddingService;
        this.schemaService = schemaService;
    }

    public String buildSchemaContext(String question) {
        String relevantSchema = embeddingService.findRelevantSchema(question);
        return relevantSchema == null || relevantSchema.isBlank() ? schemaService.getSchema() : relevantSchema;
    }

    public String buildExampleSchemaContext() {
        String relevantSchema = embeddingService.findRelevantSchema("推荐问题生成");
        return relevantSchema == null || relevantSchema.isBlank() ? schemaService.getSchema() : relevantSchema;
    }
}
