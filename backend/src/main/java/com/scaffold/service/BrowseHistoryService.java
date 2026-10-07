package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.DateTimes;
import com.scaffold.common.PageResult;
import com.scaffold.entity.BrowseHistory;
import com.scaffold.entity.Product;
import com.scaffold.mapper.BrowseHistoryMapper;
import com.scaffold.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BrowseHistoryService {

    @Autowired
    private BrowseHistoryMapper browseHistoryMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductService productService;

    @Transactional
    public BrowseHistory record(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return null;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        BrowseHistory existing = browseHistoryMapper.selectOne(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getProductId, productId)
                .last("LIMIT 1"));
        String now = DateTimes.now();
        if (existing != null) {
            BrowseHistory patch = new BrowseHistory();
            patch.setId(existing.getId());
            patch.setViewedAt(now);
            browseHistoryMapper.updateById(patch);
            existing.setViewedAt(now);
            existing.setProduct(product);
            return existing;
        }
        BrowseHistory row = new BrowseHistory();
        row.setUserId(userId);
        row.setProductId(productId);
        row.setViewedAt(now);
        browseHistoryMapper.insert(row);
        row.setProduct(product);
        return row;
    }

    public PageResult<BrowseHistory> page(Long userId, int page, int size) {
        Page<BrowseHistory> result = browseHistoryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getViewedAt)
                        .orderByDesc(BrowseHistory::getId));
        fillProducts(result.getRecords());
        return PageResult.of(result);
    }

    public List<BrowseHistory> listRecent(Long userId, int limit) {
        List<BrowseHistory> list = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getViewedAt)
                .orderByDesc(BrowseHistory::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        fillProducts(list);
        return list;
    }

    public void remove(Long userId, Long productId) {
        browseHistoryMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getProductId, productId));
    }

    public void clear(Long userId) {
        browseHistoryMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId));
    }

    public long count(Long userId) {
        return browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId));
    }

    private void fillProducts(List<BrowseHistory> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(BrowseHistory::getProductId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        List<Product> products = productMapper.selectBatchIds(ids);
        productService.enrich(products);
        java.util.Map<Long, Product> map = products.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (BrowseHistory row : list) {
            row.setProduct(map.get(row.getProductId()));
        }
    }
}
