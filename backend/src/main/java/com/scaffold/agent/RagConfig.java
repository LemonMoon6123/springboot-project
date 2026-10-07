package com.scaffold.agent;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public SwappableVectorStore swappableVectorStore() {
        return new SwappableVectorStore();
    }

    @Bean
    public VectorStore vectorStore(SwappableVectorStore holder, EmbeddingModel embeddingModel,
                                   RagKnowledgeService ragKnowledgeService) {
        holder.replace(SimpleVectorStore.builder(embeddingModel).build());
        ragKnowledgeService.bindAndLoad(holder);
        return holder;
    }
}
