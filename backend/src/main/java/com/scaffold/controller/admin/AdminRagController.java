package com.scaffold.controller.admin;

import com.scaffold.agent.RagKnowledgeService;
import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.dto.RagDocView;
import com.scaffold.dto.RagOverview;
import com.scaffold.dto.RagSearchHit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/rag")
@Tag(name = "A17-管理端-RAG 知识库", description = "管理员管理 RAG 知识库:文档上传/删除/检索/重载")
public class AdminRagController {

    @Autowired
    private RagKnowledgeService ragKnowledgeService;

    @GetMapping("/overview")
    @Operation(summary = "RAG 总览", description = "获取 RAG 知识库文档、切片、向量库总览;需管理员")
    public Result<RagOverview> overview(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.overview());
    }

    @GetMapping("/docs")
    @Operation(summary = "文档列表", description = "列出 RAG 知识库全部文档;需管理员")
    public Result<List<RagDocView>> docs(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.listDocs(false));
    }

    @GetMapping("/docs/{filename:.+}")
    @Operation(summary = "文档详情", description = "按文件名获取指定文档详情;需管理员")
    public Result<RagDocView> docDetail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable String filename) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.getDoc(filename));
    }

    @PostMapping(value = "/docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文档到 RAG 知识库,自动切分并生成向量;需管理员")
    public Result<RagOverview> upload(@Parameter(hidden = true) HttpServletRequest request,
                                     @Parameter(description = "待上传的文档文件(txt/md/pdf 等)") @RequestParam("file") MultipartFile file) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.upload(file));
    }

    @DeleteMapping("/docs/{filename:.+}")
    @Operation(summary = "删除已上传文档", description = "按文件名删除已上传的文档及其向量;需管理员")
    public Result<RagOverview> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable String filename) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.deleteUploaded(filename));
    }

    @PostMapping("/search")
    @Operation(summary = "语义检索", description = "对知识库执行语义检索,返回 topK 命中;需管理员")
    public Result<List<RagSearchHit>> search(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        AuthContext.requireAdmin(request);
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        int topK = 5;
        if (body.get("topK") != null) {
            try {
                topK = Integer.parseInt(String.valueOf(body.get("topK")));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return Result.ok(ragKnowledgeService.search(query, topK));
    }

    @PostMapping("/reload")
    @Operation(summary = "重载知识库", description = "重新加载 RAG 知识库内容;需管理员")
    public Result<RagOverview> reload(@Parameter(hidden = true) HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        return Result.ok(ragKnowledgeService.reload());
    }
}
