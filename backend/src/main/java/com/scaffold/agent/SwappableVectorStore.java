package com.scaffold.agent;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/**
 * 可整体替换内部 SimpleVectorStore，上传知识库后无需重启即可重建向量。
 */
public class SwappableVectorStore implements VectorStore {

    private volatile VectorStore delegate;

    public synchronized void replace(VectorStore next) {
        this.delegate = next;
    }

    private VectorStore require() {
        VectorStore current = delegate;
        if (current == null) {
            throw new IllegalStateException("向量库尚未就绪");
        }
        return current;
    }

    @Override
    public void add(List<Document> documents) {
        require().add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        require().delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        require().delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return require().similaritySearch(request);
    }
}
