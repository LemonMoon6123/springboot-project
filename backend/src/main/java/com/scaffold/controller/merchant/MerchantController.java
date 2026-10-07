package com.scaffold.controller.merchant;

import com.scaffold.common.AuthContext;
import com.scaffold.common.PageResult;
import com.scaffold.common.Result;
import com.scaffold.dto.AccountProfile;
import com.scaffold.dto.QaAnswerRequest;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductQa;
import com.scaffold.entity.ProductReview;
import com.scaffold.entity.ShopOrder;
import com.scaffold.service.AuthService;
import com.scaffold.service.MerchantService;
import com.scaffold.service.OrderService;
import com.scaffold.service.ProductQaService;
import com.scaffold.service.ProductService;
import com.scaffold.service.ReviewService;
import com.scaffold.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/merchant")
@Tag(name = "M01-商家端-店铺与商品管理", description = "商家资料、商品 CRUD、订单发货、售后处理、评价/问答、店铺统计")
public class MerchantController {

    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ProductQaService productQaService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private AuthService authService;

    @GetMapping("/profile")
    @Operation(summary = "商家资料", description = "获取当前登录商家资料;需商家登录")
    public Result<AccountProfile> profile(@Parameter(hidden = true) HttpServletRequest request) {
        Long id = AuthContext.requireMerchant(request);
        return Result.ok(authService.getCurrentUser(id, "MERCHANT"));
    }

    @PutMapping("/profile")
    @Operation(summary = "修改店铺资料", description = "修改店铺名称、简介等;需商家登录")
    public Result<Merchant> updateProfile(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Merchant body) {
        Long id = AuthContext.requireMerchant(request);
        return Result.ok(merchantService.updateProfile(id, body));
    }

    @GetMapping("/products")
    @Operation(summary = "商家商品分页", description = "按关键词/状态分页查询当前商家商品;需商家登录")
    public Result<PageResult<Product>> products(@Parameter(hidden = true) HttpServletRequest request,
                                                @RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productService.page(keyword, null, merchantId, status, page, size));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "商家商品详情", description = "获取指定商品详情(仅限本店铺);需商家登录")
    public Result<Product> productDetail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long merchantId = AuthContext.requireMerchant(request);
        Product product = productService.getById(id);
        if (product.getMerchantId() == null || !product.getMerchantId().equals(merchantId)) {
            throw new com.scaffold.common.BusinessException(403, "无权查看该商品");
        }
        return Result.ok(product);
    }

    @PostMapping("/products")
    @Operation(summary = "发布商品", description = "商家发布新商品;需商家登录")
    public Result<Product> createProduct(@Parameter(hidden = true) HttpServletRequest request, @RequestBody Product product) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productService.create(product, merchantId));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "修改商品", description = "修改指定商品信息(仅限本店铺);需商家登录")
    public Result<Product> updateProduct(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody Product product) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productService.update(id, product, merchantId, false));
    }

    @PostMapping("/products/{id}/on-shelf")
    @Operation(summary = "商品上架", description = "将指定商品上架;需商家登录")
    public Result<Product> onShelf(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productService.onShelf(id, merchantId, false));
    }

    @PostMapping("/products/{id}/off-shelf")
    @Operation(summary = "商品下架", description = "将指定商品下架;需商家登录")
    public Result<Product> offShelf(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productService.offShelf(id, merchantId, false));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "删除商品", description = "删除指定商品(仅限本店铺);需商家登录")
    public Result<Void> deleteProduct(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long merchantId = AuthContext.requireMerchant(request);
        productService.delete(id, merchantId, false);
        return Result.ok();
    }

    @GetMapping("/orders")
    @Operation(summary = "商家订单分页", description = "按订单状态/售后状态分页查询;需商家登录")
    public Result<PageResult<ShopOrder>> orders(@Parameter(hidden = true) HttpServletRequest request,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Integer afterSaleStatus,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(orderService.pageForMerchant(merchantId, status, afterSaleStatus, page, size));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "商家订单详情", description = "获取指定订单详情(仅限本店铺);需商家登录")
    public Result<ShopOrder> orderDetail(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        Long merchantId = AuthContext.requireMerchant(request);
        ShopOrder order = orderService.getDetail(id);
        if (!order.getMerchantId().equals(merchantId)) {
            return Result.fail(403, "无权查看该订单");
        }
        return Result.ok(order);
    }

    @PostMapping("/orders/{id}/ship")
    @Operation(summary = "订单发货", description = "对已支付订单填写物流信息并发货;需商家登录")
    public Result<ShopOrder> ship(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                  @RequestBody(required = false) com.scaffold.dto.ShipOrderRequest body) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(orderService.ship(id, merchantId, false, body));
    }

    @PostMapping("/orders/{id}/after-sale")
    @Operation(summary = "处理售后申请", description = "商家同意/拒绝用户售后申请;需商家登录")
    public Result<ShopOrder> handleAfterSale(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                             @RequestBody com.scaffold.dto.AfterSaleHandleRequest body) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(orderService.handleAfterSale(merchantId, id, body, false));
    }

    @GetMapping("/reviews")
    @Operation(summary = "商家评价分页", description = "分页查询本店铺商品的评价;需商家登录")
    public Result<PageResult<ProductReview>> reviews(@Parameter(hidden = true) HttpServletRequest request,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(reviewService.page(null, merchantId, null, page, size));
    }

    @GetMapping("/qa")
    @Operation(summary = "商家问答分页", description = "分页查询本店铺商品的问答;需商家登录")
    public Result<PageResult<ProductQa>> qa(@Parameter(hidden = true) HttpServletRequest request,
                                            @RequestParam(required = false) Boolean unanswered,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productQaService.page(null, merchantId, unanswered, page, size));
    }

    @PostMapping("/qa/{id}/answer")
    @Operation(summary = "回复商品提问", description = "商家回答指定商品的提问;需商家登录")
    public Result<ProductQa> answer(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                    @RequestBody QaAnswerRequest body) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(productQaService.answer(id, merchantId, body.getAnswer(), merchantId, false));
    }

    @GetMapping("/stats")
    @Operation(summary = "店铺统计", description = "返回当前店铺经营统计数据;需商家登录")
    public Result<Map<String, Object>> stats(@Parameter(hidden = true) HttpServletRequest request) {
        Long merchantId = AuthContext.requireMerchant(request);
        return Result.ok(statsService.merchantStats(merchantId));
    }
}
