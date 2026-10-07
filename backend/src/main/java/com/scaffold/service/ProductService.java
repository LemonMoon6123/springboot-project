package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Category;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.Product;
import com.scaffold.mapper.CategoryMapper;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private MerchantMapper merchantMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    public PageResult<Product> page(String keyword, Long categoryId, Long merchantId, Integer status,
                                    int page, int size) {
        return page(keyword, categoryId, merchantId, status, null, page, size);
    }

    public PageResult<Product> page(String keyword, Long categoryId, Long merchantId, Integer status,
                                    String sort, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = buildWrapper(keyword, categoryId, merchantId, status);
        applySort(wrapper, sort);
        PageResult<Product> result = PageResult.of(productMapper.selectPage(new Page<>(page, size), wrapper));
        enrich(result.getList());
        return result;
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(Product::getSales).orderByDesc(Product::getId);
        } else if ("price_asc".equals(sort)) {
            wrapper.orderByAsc(Product::getPrice).orderByDesc(Product::getId);
        } else if ("price_desc".equals(sort)) {
            wrapper.orderByDesc(Product::getPrice).orderByDesc(Product::getId);
        } else {
            wrapper.orderByDesc(Product::getId);
        }
    }

    public List<Product> listHot(int limit) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales)
                .orderByDesc(Product::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        enrich(list);
        return list;
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        enrich(Collections.singletonList(product));
        return product;
    }

    public Product getOnShelf(Long id) {
        Product product = getById(id);
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException("商品已下架");
        }
        return product;
    }

    public Product create(Product product, Long merchantId) {
        if (!StringUtils.hasText(product.getName())) {
            throw new BusinessException("商品名称不能为空");
        }
        if (product.getPrice() == null || product.getPrice() < 0) {
            throw new BusinessException("商品价格无效");
        }
        product.setMerchantId(merchantId);
        if (product.getStock() == null) {
            product.setStock(0);
        }
        if (product.getSales() == null) {
            product.setSales(0);
        }
        if (product.getStatus() == null) {
            product.setStatus(0);
        }
        if (product.getOriginalPrice() == null) {
            product.setOriginalPrice(product.getPrice());
        }
        productMapper.insert(product);
        return getById(product.getId());
    }

  public Product update(Long id, Product product, Long operatorId, boolean admin) {
        Product existing = getById(id);
        if (!admin && !existing.getMerchantId().equals(operatorId)) {
            throw new BusinessException(403, "无权修改该商品");
        }
        product.setId(id);
        product.setMerchantId(null);
        product.setSales(null);
        // 显式保留详情/简介，避免前端漏传时被忽略后无法回填
        if (product.getDetail() == null) {
            product.setDetail(existing.getDetail());
        }
        if (product.getDescription() == null) {
            product.setDescription(existing.getDescription());
        }
        productMapper.updateById(product);
        return getById(id);
    }

    public Product onShelf(Long id, Long operatorId, boolean admin) {
        Product existing = getById(id);
        if (!admin && !existing.getMerchantId().equals(operatorId)) {
            throw new BusinessException(403, "无权操作该商品");
        }
        Product patch = new Product();
        patch.setId(id);
        patch.setStatus(1);
        productMapper.updateById(patch);
        return getById(id);
    }

    public Product offShelf(Long id, Long operatorId, boolean admin) {
        Product existing = getById(id);
        if (!admin && !existing.getMerchantId().equals(operatorId)) {
            throw new BusinessException(403, "无权操作该商品");
        }
        Product patch = new Product();
        patch.setId(id);
        patch.setStatus(0);
        productMapper.updateById(patch);
        return getById(id);
    }

    public void delete(Long id, Long operatorId, boolean admin) {
        Product existing = getById(id);
        if (!admin && !existing.getMerchantId().equals(operatorId)) {
            throw new BusinessException(403, "无权删除该商品");
        }
        productMapper.deleteById(id);
    }

    public void changeStock(Long id, int delta) {
        Product product = getById(id);
        int stock = product.getStock() == null ? 0 : product.getStock();
        int next = stock + delta;
        if (next < 0) {
            throw new BusinessException("库存不足");
        }
        Product patch = new Product();
        patch.setId(id);
        patch.setStock(next);
        productMapper.updateById(patch);
    }

    public void increaseSales(Long id, int qty) {
        Product product = getById(id);
        int sales = product.getSales() == null ? 0 : product.getSales();
        Product patch = new Product();
        patch.setId(id);
        patch.setSales(sales + qty);
        productMapper.updateById(patch);
    }

    public void decreaseSales(Long id, int qty) {
        Product product = getById(id);
        int sales = product.getSales() == null ? 0 : product.getSales();
        Product patch = new Product();
        patch.setId(id);
        patch.setSales(Math.max(0, sales - qty));
        productMapper.updateById(patch);
    }

    public void enrich(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Set<Long> merchantIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (Product p : products) {
            if (p.getMerchantId() != null) {
                merchantIds.add(p.getMerchantId());
            }
            if (p.getCategoryId() != null) {
                categoryIds.add(p.getCategoryId());
            }
        }
        List<Merchant> merchants = merchantIds.isEmpty()
                ? Collections.emptyList()
                : merchantMapper.selectByIds(merchantIds);
        Map<Long, String> shopMap = merchants.stream()
                .collect(Collectors.toMap(Merchant::getId, m -> {
                    if (StringUtils.hasText(m.getShopName())) {
                        return m.getShopName();
                    }
                    if (StringUtils.hasText(m.getNickname())) {
                        return m.getNickname();
                    }
                    return m.getUsername();
                }, (a, b) -> a));
        Map<Long, String> shopAvatarMap = merchants.stream()
                .filter(m -> StringUtils.hasText(m.getAvatar()))
                .collect(Collectors.toMap(Merchant::getId, Merchant::getAvatar, (a, b) -> a));
        Map<Long, String> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        for (Product p : products) {
            if (p.getMerchantId() != null) {
                p.setShopName(shopMap.get(p.getMerchantId()));
                p.setShopAvatar(shopAvatarMap.get(p.getMerchantId()));
            }
            if (p.getCategoryId() != null) {
                p.setCategoryName(categoryMap.get(p.getCategoryId()));
            }
        }
    }

    public Product enrichOne(Product product) {
        if (product != null) {
            enrich(Collections.singletonList(product));
        }
        return product;
    }

    private LambdaQueryWrapper<Product> buildWrapper(String keyword, Long categoryId, Long merchantId, Integer status) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword);
        }
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        return wrapper;
    }
}
