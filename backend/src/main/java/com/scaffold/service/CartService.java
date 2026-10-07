package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.common.BusinessException;
import com.scaffold.entity.CartItem;
import com.scaffold.entity.Product;
import com.scaffold.mapper.CartItemMapper;
import com.scaffold.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductService productService;

    public List<CartItem> list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getId));
        fillProducts(items);
        return items;
    }

    public CartItem add(Long userId, Long productId, Integer quantity) {
        if (productId == null) {
            throw new BusinessException("商品不能为空");
        }
        int qty = quantity == null || quantity < 1 ? 1 : quantity;
        Product product = requireOnShelf(productId);
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + qty);
            cartItemMapper.updateById(existing);
            existing.setProduct(product);
            return existing;
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(qty);
        cartItemMapper.insert(item);
        item.setProduct(product);
        return item;
    }

    public CartItem updateQuantity(Long userId, Long id, Integer quantity) {
        CartItem item = getOwned(userId, id);
        if (quantity == null || quantity < 1) {
            throw new BusinessException("数量至少为1");
        }
        item.setQuantity(quantity);
        cartItemMapper.updateById(item);
        item.setProduct(requireProduct(item.getProductId()));
        return item;
    }

    public void delete(Long userId, Long id) {
        getOwned(userId, id);
        cartItemMapper.deleteById(id);
    }

    public void clear(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
    }

    public CartItem getOwned(Long userId, Long id) {
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(404, "购物车项不存在");
        }
        return item;
    }

    private void fillProducts(List<CartItem> items) {
        List<Product> products = items.stream()
                .map(CartItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .map(productMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        productService.enrich(products);
        var map = products.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (CartItem item : items) {
            item.setProduct(map.get(item.getProductId()));
        }
    }

    private Product requireOnShelf(Long productId) {
        Product product = requireProduct(productId);
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException("商品已下架");
        }
        return product;
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        return productService.enrichOne(product);
    }
}
