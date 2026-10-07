package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.OrderItem;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductReview;
import com.scaffold.entity.ShopOrder;
import com.scaffold.entity.User;
import com.scaffold.mapper.OrderItemMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.ProductReviewMapper;
import com.scaffold.mapper.ShopOrderMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Service
public class ReviewService {

    @Autowired
    private ProductReviewMapper productReviewMapper;
    @Autowired
    private ShopOrderMapper shopOrderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProductMapper productMapper;

    public PageResult<ProductReview> page(Long productId, Long merchantId, Long userId,
                                          int page, int size) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProductReview::getProductId, productId);
        }
        if (userId != null) {
            wrapper.eq(ProductReview::getUserId, userId);
        }
        if (merchantId != null) {
            wrapper.inSql(ProductReview::getProductId,
                    "SELECT id FROM product WHERE merchant_id = " + merchantId);
        }
        wrapper.orderByDesc(ProductReview::getId);
        Page<ProductReview> result = productReviewMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillExtra);
        return PageResult.of(result);
    }

    public ProductReview create(Long userId, ProductReview review) {
        if (review.getOrderId() == null || review.getOrderItemId() == null) {
            throw new BusinessException("订单信息不完整");
        }
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessException("评分需为1-5星");
        }
        ShopOrder order = shopOrderMapper.selectById(review.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new BusinessException("仅已完成订单可评价");
        }
        OrderItem item = orderItemMapper.selectById(review.getOrderItemId());
        if (item == null || !item.getOrderId().equals(order.getId())) {
            throw new BusinessException("订单明细不存在");
        }
        Long exists = productReviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getOrderItemId, review.getOrderItemId()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该商品已评价");
        }
        review.setUserId(userId);
        review.setProductId(item.getProductId());
        if (!StringUtils.hasText(review.getContent())) {
            review.setContent("");
        }
        productReviewMapper.insert(review);
        ProductReview saved = productReviewMapper.selectById(review.getId());
        fillExtra(saved);
        return saved;
    }

    public void delete(Long id) {
        ProductReview review = productReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(404, "评价不存在");
        }
        productReviewMapper.deleteById(id);
    }

    private void fillExtra(ProductReview review) {
        if (review == null) {
            return;
        }
        if (review.getUserId() != null) {
            User user = userMapper.selectById(review.getUserId());
            if (user != null) {
                review.setNickname(user.getNickname());
                review.setAvatar(user.getAvatar());
            }
        }
        if (review.getProductId() != null) {
            Product product = productMapper.selectById(review.getProductId());
            if (product != null) {
                review.setProductName(product.getName());
                review.setProductCover(product.getCover());
            }
        }
    }
}
