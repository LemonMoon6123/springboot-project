package com.scaffold.agent;

import com.scaffold.common.BusinessException;
import com.scaffold.dto.RagDocView;
import com.scaffold.dto.RagOverview;
import com.scaffold.dto.RagSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 知识库：内置 classpath:rag/*.md + 后台上传 uploads/rag/*.md。
 */
@Service
public class RagKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);
    private static final Pattern TITLE = Pattern.compile("^\\s*#\\s+(.+)$", Pattern.MULTILINE);
    private static final long MAX_UPLOAD_BYTES = 1024 * 1024;
    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_UPLOAD = "upload";

    private final EmbeddingModel embeddingModel;
    private final CopyOnWriteArrayList<RagDocView> docs = new CopyOnWriteArrayList<>();
    private volatile VectorStore vectorStore;

    @Value("${app.upload.path:uploads}")
    private String uploadPath;

    public RagKnowledgeService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public synchronized void bindAndLoad(VectorStore store) {
        this.vectorStore = store;
        reloadInternal(true);
    }

    public RagOverview overview() {
        RagOverview overview = new RagOverview();
        overview.setDocCount(docs.size());
        overview.setChunkCount(docs.stream().mapToInt(RagDocView::getChunkCount).sum());
        overview.setTotalChars(docs.stream().mapToInt(RagDocView::getCharCount).sum());
        overview.setStoreType("SimpleVectorStore（内存，上传后即时重建）");
        overview.setEmbeddingType("HashingEmbeddingModel（本地哈希向量）");
        overview.setDocs(listDocs(false));
        return overview;
    }

    public List<RagDocView> listDocs(boolean withContent) {
        List<RagDocView> list = new ArrayList<>();
        for (RagDocView src : docs) {
            list.add(copyDoc(src, withContent));
        }
        list.sort(Comparator.comparing(RagDocView::getFilename, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    public RagDocView getDoc(String filename) {
        return copyDoc(findDoc(filename), true);
    }

    public List<RagSearchHit> search(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("请输入检索关键词");
        }
        if (vectorStore == null) {
            throw new BusinessException("向量库尚未就绪");
        }
        int k = Math.max(1, Math.min(topK <= 0 ? 5 : topK, 12));
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query.trim()).topK(k).similarityThreshold(0.0).build());
        List<RagSearchHit> result = new ArrayList<>();
        if (hits == null) {
            return result;
        }
        for (Document doc : hits) {
            String filename = null;
            Double score = null;
            if (doc.getMetadata() != null) {
                Object source = doc.getMetadata().get("source");
                if (source == null) {
                    source = doc.getMetadata().get("filename");
                }
                if (source != null) {
                    filename = String.valueOf(source);
                }
                Object dist = doc.getMetadata().get("distance");
                if (dist instanceof Number n) {
                    score = Math.max(0, 1.0 - n.doubleValue());
                }
            }
            result.add(new RagSearchHit(filename, doc.getText(), score));
        }
        return result;
    }

    public RagOverview reload() {
        reloadInternal(true);
        return overview();
    }

    public synchronized RagOverview upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Markdown 文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("单个知识库文件不能超过 1MB");
        }
        String filename = safeMarkdownName(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("文件内容为空");
            }
            Path dir = resolveUploadDir();
            Path target = dir.resolve(filename);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("已保存上传知识库文档: {}", target);
        } catch (IOException e) {
            throw new BusinessException("保存知识库文件失败: " + e.getMessage());
        }
        reloadInternal(true);
        return overview();
    }

    public synchronized RagOverview deleteUploaded(String filename) {
        String name = safeMarkdownName(filename);
        Path target = resolveUploadDir().resolve(name);
        try {
            if (!Files.exists(target)) {
                throw new BusinessException(404, "只能删除后台上传的文档，内置文档请改源码 rag 目录");
            }
            Files.delete(target);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("删除失败: " + e.getMessage());
        }
        reloadInternal(true);
        return overview();
    }

    private void reloadInternal(boolean rebuildVectors) {
        try {
            Map<String, LoadedDoc> catalog = loadCatalog();
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> allChunks = new ArrayList<>();
            List<RagDocView> views = new ArrayList<>();

            for (LoadedDoc loaded : catalog.values()) {
                List<Document> parts = splitMarkdown(loaded, splitter);
                List<RagDocView.RagChunkView> chunkViews = new ArrayList<>();
                int idx = 0;
                for (Document part : parts) {
                    Map<String, Object> meta = new HashMap<>(part.getMetadata());
                    meta.put("source", loaded.filename);
                    meta.put("origin", loaded.origin);
                    Document chunk = new Document(part.getText(), meta);
                    allChunks.add(chunk);
                    chunkViews.add(new RagDocView.RagChunkView(
                            idx++,
                            part.getText(),
                            part.getText() == null ? 0 : part.getText().length()));
                }
                RagDocView view = new RagDocView();
                view.setFilename(loaded.filename);
                view.setTitle(extractTitle(loaded.content, loaded.filename));
                view.setContent(loaded.content);
                view.setSummary(extractSummary(loaded.content));
                view.setCharCount(loaded.content.length());
                view.setChunkCount(chunkViews.size());
                view.setChunks(chunkViews);
                view.setOrigin(loaded.origin);
                view.setUploaded(SOURCE_UPLOAD.equals(loaded.origin));
                views.add(view);
            }

            if (rebuildVectors) {
                SimpleVectorStore fresh = SimpleVectorStore.builder(embeddingModel).build();
                if (!allChunks.isEmpty()) {
                    fresh.add(allChunks);
                }
                if (vectorStore instanceof SwappableVectorStore swap) {
                    swap.replace(fresh);
                } else {
                    vectorStore = fresh;
                }
                log.info("RAG 知识库已重建 {} 个文档 / {} 个片段", views.size(), allChunks.size());
            }

            docs.clear();
            docs.addAll(views);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("RAG 知识库加载失败: {}", e.getMessage());
            throw new BusinessException("知识库加载失败: " + e.getMessage());
        }
    }

    private Map<String, LoadedDoc> loadCatalog() throws IOException {
        Map<String, LoadedDoc> catalog = new LinkedHashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:rag/*.md");
        for (Resource resource : resources) {
            if (!resource.exists() || !resource.isReadable()) {
                continue;
            }
            String filename = resource.getFilename();
            if (!StringUtils.hasText(filename)) {
                continue;
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            catalog.put(filename, new LoadedDoc(filename, content, SOURCE_BUILTIN, resource));
        }
        Path dir = resolveUploadDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    String filename = path.getFileName().toString();
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    catalog.put(filename, new LoadedDoc(filename, content, SOURCE_UPLOAD, new FileSystemResource(path)));
                }
            }
        }
        return catalog;
    }

    private List<Document> splitMarkdown(LoadedDoc loaded, TokenTextSplitter splitter) {
        TextReader reader = new TextReader(loaded.resource);
        reader.getCustomMetadata().put("filename", loaded.filename);
        return splitter.apply(reader.get());
    }

    private Path resolveUploadDir() {
        try {
            Path dir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("rag");
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new BusinessException("无法创建知识库上传目录: " + e.getMessage());
        }
    }

    private RagDocView findDoc(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException("文件名不能为空");
        }
        return docs.stream()
                .filter(d -> filename.equals(d.getFilename()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "知识库文档不存在: " + filename));
    }

    private static RagDocView copyDoc(RagDocView src, boolean withContent) {
        RagDocView copy = new RagDocView();
        copy.setFilename(src.getFilename());
        copy.setTitle(src.getTitle());
        copy.setSummary(src.getSummary());
        copy.setChunkCount(src.getChunkCount());
        copy.setCharCount(src.getCharCount());
        copy.setOrigin(src.getOrigin());
        copy.setUploaded(src.isUploaded());
        if (withContent) {
            copy.setContent(src.getContent());
            copy.setChunks(src.getChunks());
        } else {
            copy.setContent(null);
            copy.setChunks(List.of());
        }
        return copy;
    }

    static String safeMarkdownName(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("文件名不能为空");
        }
        String name = Paths.get(raw.replace('\\', '/')).getFileName().toString().trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BusinessException("仅支持 .md 文件");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new BusinessException("文件名不合法");
        }
        if (!name.matches("[\\w.\\-\\u4e00-\\u9fa5]+\\.md")) {
            throw new BusinessException("文件名仅支持中英文、数字、点、下划线和短横线");
        }
        return name;
    }

    private static String extractTitle(String content, String filename) {
        if (StringUtils.hasText(content)) {
            Matcher m = TITLE.matcher(content);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        if (filename == null) {
            return "未命名文档";
        }
        return filename.replaceAll("\\.md$", "");
    }

    private static String extractSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = content
                .replaceAll("^#+\\s+", "")
                .replaceAll("(?m)^#+\\s+", "")
                .replaceAll("(?m)^-\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() > 120 ? plain.substring(0, 120) + "…" : plain;
    }

    private record LoadedDoc(String filename, String content, String origin, Resource resource) {
    }
}
