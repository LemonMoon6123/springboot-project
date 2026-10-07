package com.scaffold.controller.admin;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.QaAnswerRequest;
import com.scaffold.entity.ProductFavorite;
import com.scaffold.entity.ProductQa;
import com.scaffold.entity.ProductReview;
import com.scaffold.service.FavoriteService;
import com.scaffold.service.ProductQaService;
import com.scaffold.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@Tag(name = "A23-管理端-评价/问答/收藏", description = "管理员管理商品评价、问答、收藏记录")
public class AdminReviewQaFavoriteController {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ProductQaService productQaService;
    @Autowired
    private FavoriteService favoriteService;

    @GetMapping("/reviews")
    @Operation(summary = "评价分页查询", description = "按商品/用户分页查询评价;需管理员")
    public Result<PageResult<ProductReview>> reviews(@Parameter(hidden = true) HttpServletRequest request,
                                                     @RequestParam(required = false) Long productId,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(reviewService.page(productId, null, userId, page, size));
    }

    @DeleteMapping("/reviews/{id}")
    @Operation(summary = "删除评价", description = "删除指定评价;需管理员")
    public Result<Void> deleteReview(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        reviewService.delete(id);
        return Result.ok();
    }

    @GetMapping("/qa")
    @Operation(summary = "问答分页查询", description = "按商品/未回答状态分页查询问答;需管理员")
    public Result<PageResult<ProductQa>> qa(@Parameter(hidden = true) HttpServletRequest request,
                                            @RequestParam(required = false) Long productId,
                                            @RequestParam(required = false) Boolean unanswered,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(productQaService.page(productId, null, unanswered, page, size));
    }

    @PostMapping("/qa/{id}/answer")
    @Operation(summary = "管理员回答提问", description = "管理员代答指定商品提问;需管理员")
    public Result<ProductQa> answerQa(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                      @RequestBody QaAnswerRequest body) {
        Long adminId = AuthContext.requireLogin(request);
        AuthContext.requireAdmin(request);
        return Result.ok(productQaService.answer(id, adminId, body.getAnswer(), null, true));
    }

    @DeleteMapping("/qa/{id}")
    @Operation(summary = "删除提问", description = "删除指定商品提问;需管理员")
    public Result<Void> deleteQa(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        productQaService.delete(id);
        return Result.ok();
    }

    @GetMapping("/favorites")
    @Operation(summary = "收藏分页查询", description = "按用户/商品分页查询收藏记录;需管理员")
    public Result<PageResult<ProductFavorite>> favorites(@Parameter(hidden = true) HttpServletRequest request,
                                                         @RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) Long productId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin(request);
        return Result.ok(favoriteService.page(userId, productId, page, size));
    }

    @DeleteMapping("/favorites/{id}")
    @Operation(summary = "删除收藏", description = "删除指定收藏记录;需管理员")
    public Result<Void> deleteFavorite(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        AuthContext.requireAdmin(request);
        favoriteService.deleteById(id);
        return Result.ok();
    }
}
