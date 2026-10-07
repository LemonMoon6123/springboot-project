package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.DateTimes;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Product;
import com.scaffold.entity.ProductQa;
import com.scaffold.entity.User;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.ProductQaMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Service
public class ProductQaService {

    @Autowired
    private ProductQaMapper productQaMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;

    public PageResult<ProductQa> page(Long productId, Long merchantId, Boolean unanswered,
                                      int page, int size) {
        LambdaQueryWrapper<ProductQa> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProductQa::getProductId, productId);
        }
        if (merchantId != null) {
            wrapper.inSql(ProductQa::getProductId,
                    "SELECT id FROM product WHERE merchant_id = " + merchantId);
        }
        if (Boolean.TRUE.equals(unanswered)) {
            wrapper.and(w -> w.isNull(ProductQa::getAnswer).or().eq(ProductQa::getAnswer, ""));
        }
        wrapper.orderByDesc(ProductQa::getId);
        Page<ProductQa> result = productQaMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillExtra);
        return PageResult.of(result);
    }

    public ProductQa ask(Long userId, Long productId, String question) {
        if (productId == null) {
            throw new BusinessException("商品不能为空");
        }
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        ProductQa qa = new ProductQa();
        qa.setProductId(productId);
        qa.setUserId(userId);
        qa.setQuestion(question.trim());
        productQaMapper.insert(qa);
        ProductQa saved = productQaMapper.selectById(qa.getId());
        fillExtra(saved);
        return saved;
    }

    public ProductQa answer(Long id, Long answerBy, String answer, Long merchantId, boolean admin) {
        ProductQa qa = productQaMapper.selectById(id);
        if (qa == null) {
            throw new BusinessException(404, "问答不存在");
        }
        if (!admin) {
            Product product = productMapper.selectById(qa.getProductId());
            if (product == null || !product.getMerchantId().equals(merchantId)) {
                throw new BusinessException(403, "无权回答该问题");
            }
        }
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("回答不能为空");
        }
        ProductQa patch = new ProductQa();
        patch.setId(id);
        patch.setAnswer(answer.trim());
        patch.setAnswerBy(answerBy);
        patch.setAnsweredAt(DateTimes.now());
        productQaMapper.updateById(patch);
        ProductQa saved = productQaMapper.selectById(id);
        fillExtra(saved);
        return saved;
    }

    public void delete(Long id) {
        if (productQaMapper.selectById(id) == null) {
            throw new BusinessException(404, "问答不存在");
        }
        productQaMapper.deleteById(id);
    }

    private void fillExtra(ProductQa qa) {
        if (qa == null) {
            return;
        }
        if (qa.getUserId() != null) {
            User user = userMapper.selectById(qa.getUserId());
            if (user != null) {
                qa.setNickname(user.getNickname());
            }
        }
        if (qa.getProductId() != null) {
            Product product = productMapper.selectById(qa.getProductId());
            if (product != null) {
                qa.setProductName(product.getName());
                qa.setProductCover(product.getCover());
            }
        }
    }
}
