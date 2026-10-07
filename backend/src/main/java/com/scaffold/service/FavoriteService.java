package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductFavorite;
import com.scaffold.entity.User;
import com.scaffold.mapper.ProductFavoriteMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    @Autowired
    private ProductFavoriteMapper productFavoriteMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductService productService;
    @Autowired
    private UserMapper userMapper;

    public List<ProductFavorite> listByUser(Long userId) {
        List<ProductFavorite> list = productFavoriteMapper.selectList(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .orderByDesc(ProductFavorite::getId));
        fillExtra(list);
        return list;
    }

    public PageResult<ProductFavorite> page(Long userId, Long productId, int page, int size) {
        LambdaQueryWrapper<ProductFavorite> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ProductFavorite::getUserId, userId);
        }
        if (productId != null) {
            wrapper.eq(ProductFavorite::getProductId, productId);
        }
        wrapper.orderByDesc(ProductFavorite::getId);
        Page<ProductFavorite> result = productFavoriteMapper.selectPage(new Page<>(page, size), wrapper);
        fillExtra(result.getRecords());
        return PageResult.of(result);
    }

    public ProductFavorite add(Long userId, Long productId) {
        if (productId == null) {
            throw new BusinessException("商品不能为空");
        }
        Product product = requireProduct(productId);
        ProductFavorite existing = productFavoriteMapper.selectOne(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId)
                .last("LIMIT 1"));
        if (existing != null) {
            fillExtra(List.of(existing));
            return existing;
        }
        ProductFavorite fav = new ProductFavorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        productFavoriteMapper.insert(fav);
        ProductFavorite saved = productFavoriteMapper.selectById(fav.getId());
        fillExtra(List.of(saved));
        return saved;
    }

    public void remove(Long userId, Long productId) {
        productFavoriteMapper.delete(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId));
    }

    public void deleteById(Long id) {
        if (productFavoriteMapper.selectById(id) == null) {
            throw new BusinessException(404, "收藏记录不存在");
        }
        productFavoriteMapper.deleteById(id);
    }

    public boolean isFavorite(Long userId, Long productId) {
        Long count = productFavoriteMapper.selectCount(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId));
        return count != null && count > 0;
    }

    private void fillExtra(List<ProductFavorite> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Product> products = list.stream()
                .map(ProductFavorite::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .map(productMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        productService.enrich(products);
        var productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        for (ProductFavorite fav : list) {
            Product product = productMap.get(fav.getProductId());
            fav.setProduct(product);
            if (product != null) {
                fav.setProductName(product.getName());
                fav.setProductCover(product.getCover());
                fav.setPrice(product.getPrice());
            }
            if (fav.getUserId() != null) {
                User user = userMapper.selectById(fav.getUserId());
                if (user != null) {
                    fav.setUsername(user.getUsername());
                    fav.setNickname(user.getNickname());
                }
            }
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        return productService.enrichOne(product);
    }
}
