package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.ShopVO;
import com.scaffold.entity.Category;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductQa;
import com.scaffold.entity.ProductReview;
import com.scaffold.service.BrowseHistoryService;
import com.scaffold.service.CategoryService;
import com.scaffold.service.ProductQaService;
import com.scaffold.service.ProductService;
import com.scaffold.service.RecommendService;
import com.scaffold.service.ReviewService;
import com.scaffold.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Tag(name = "U01-用户端-商城浏览", description = "商品分类、商品列表/详情、店铺、推荐、评价、问答")
public class UserMallController {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private RecommendService recommendService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ProductQaService productQaService;
    @Autowired
    private ShopService shopService;
    @Autowired
    private BrowseHistoryService browseHistoryService;

    @GetMapping("/categories")
    @Operation(summary = "商品分类树", description = "返回所有启用的商品分类")
    public Result<List<Category>> categories() {
        return Result.ok(categoryService.listEnabled());
    }

    @GetMapping("/shops")
    @Operation(summary = "店铺分页列表", description = "按关键词搜索店铺,分页返回")
    public Result<PageResult<ShopVO>> shops(@RequestParam(defaultValue = "") String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "12") int size) {
        return Result.ok(shopService.page(keyword, page, size));
    }

    @GetMapping("/shops/{id}")
    @Operation(summary = "店铺详情", description = "根据店铺 ID 获取店铺公开信息")
    public Result<ShopVO> shopDetail(@PathVariable Long id) {
        return Result.ok(shopService.getPublicShop(id));
    }

    @GetMapping("/products")
    @Operation(summary = "商品分页列表", description = "支持关键词、分类、商家筛选与多种排序")
    public Result<PageResult<Product>> products(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Long merchantId,
                                                @RequestParam(defaultValue = "default") String sort,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        return Result.ok(productService.page(keyword, categoryId, merchantId, 1, sort, page, size));
    }

    @GetMapping("/products/hot")
    @Operation(summary = "热门商品", description = "返回指定数量的热门商品")
    public Result<List<Product>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(productService.listHot(limit));
    }

    @GetMapping("/products/recommend")
    @Operation(summary = "个性化推荐", description = "基于用户行为/商品/分类的推荐;未登录返回热门")
    public Result<List<Product>> recommend(@Parameter(hidden = true) HttpServletRequest request,
                                           @RequestParam(required = false) Long productId,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) String mode,
                                           @RequestParam(defaultValue = "10") int limit) {
        Long userId = AuthContext.getUserId(request);
        return Result.ok(recommendService.recommend(userId, productId, categoryId, mode, limit));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "商品详情", description = "获取在售商品详情;已登录用户自动记录浏览足迹")
    public Result<Product> productDetail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long userId = AuthContext.getUserId(request);
        Product product = productService.getOnShelf(id);
        if (userId != null) {
            try {
                browseHistoryService.record(userId, id);
            } catch (Exception ignored) {
                // 足迹失败不影响详情
            }
        }
        return Result.ok(product);
    }

    @GetMapping("/products/{id}/reviews")
    @Operation(summary = "商品评价分页", description = "分页查询指定商品的评价")
    public Result<PageResult<ProductReview>> productReviews(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.page(id, null, null, page, size));
    }

    @GetMapping("/products/{id}/qa")
    @Operation(summary = "商品问答分页", description = "分页查询指定商品的问答")
    public Result<PageResult<ProductQa>> productQa(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return Result.ok(productQaService.page(id, null, null, page, size));
    }

    @PostMapping("/reviews")
    @Operation(summary = "发表评价", description = "用户对已购商品发表评价;需登录")
    public Result<ProductReview> createReview(@Parameter(hidden = true) HttpServletRequest request, @RequestBody ProductReview review) {
        return Result.ok(reviewService.create(AuthContext.requireUser(request), review));
    }

    @PostMapping("/qa")
    @Operation(summary = "发起商品提问", description = "用户对商品发起提问;需登录")
    public Result<ProductQa> askQa(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.requireUser(request);
        Long productId = body.get("productId") == null ? null : Long.valueOf(body.get("productId").toString());
        String question = body.get("question") == null ? null : body.get("question").toString();
        return Result.ok(productQaService.ask(userId, productId, question));
    }
}
