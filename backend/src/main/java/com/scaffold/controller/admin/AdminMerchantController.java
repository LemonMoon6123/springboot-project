package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.MerchantAuditRequest;
import com.scaffold.entity.Merchant;
import com.scaffold.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/merchants")
@Tag(name = "A14-管理端-商家审核", description = "管理员查看/审核商家入驻申请")
public class AdminMerchantController {

    @Autowired
    private MerchantService merchantService;

    @GetMapping
    @Operation(summary = "商家分页查询", description = "按关键词/审核状态分页查询商家;需管理员")
    public Result<PageResult<Merchant>> page(@Parameter(hidden = true) HttpServletRequest request,
                                             @RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(required = false) Integer auditStatus,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(merchantService.page(keyword, auditStatus, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商家详情", description = "获取指定商家详情;需管理员")
    public Result<Merchant> detail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        return Result.ok(merchantService.getById(id));
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "审核商家入驻", description = "通过/拒绝商家入驻申请;需管理员")
    public Result<Merchant> audit(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                  @RequestBody MerchantAuditRequest body) {
        AuthContext.requireAdmin(request);
        return Result.ok(merchantService.audit(id, body));
    }
}
