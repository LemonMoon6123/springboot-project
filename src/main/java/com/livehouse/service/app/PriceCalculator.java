package com.livehouse.service.app;

import com.livehouse.config.LivehouseProperties;
import com.livehouse.entity.MemberLevel;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 试算与下单共用金额口径：折扣作用于票面总额 + 服务费（规格 4.3.1 示例 543.40）。
 */
@Component
public class PriceCalculator {

    private final LivehouseProperties livehouseProperties;

    public PriceCalculator(LivehouseProperties livehouseProperties) {
        this.livehouseProperties = livehouseProperties;
    }

    public PriceBreakdown calculate(BigDecimal unitPrice, int quantity, MemberLevel memberLevel) {
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFee = livehouseProperties.getOrder().getServiceFeePerTicket()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal rate = BigDecimal.ONE;
        Integer level = 0;
        String levelName = "普通";
        if (memberLevel != null && memberLevel.getDiscountRate() != null) {
            rate = memberLevel.getDiscountRate();
            level = memberLevel.getLevel();
            levelName = memberLevel.getName();
        }

        BigDecimal beforeDiscount = totalAmount.add(serviceFee);
        BigDecimal payAmount = beforeDiscount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal memberDiscountAmount = beforeDiscount.subtract(payAmount).setScale(2, RoundingMode.HALF_UP);

        PriceBreakdown breakdown = new PriceBreakdown();
        breakdown.setTotalAmount(totalAmount);
        breakdown.setServiceFee(serviceFee);
        breakdown.setMemberLevel(level);
        breakdown.setMemberLevelName(levelName);
        breakdown.setMemberDiscountRate(rate);
        breakdown.setMemberDiscountAmount(memberDiscountAmount);
        breakdown.setPayAmount(payAmount);
        return breakdown;
    }

    @Data
    public static class PriceBreakdown {
        private BigDecimal totalAmount;
        private BigDecimal serviceFee;
        private Integer memberLevel;
        private String memberLevelName;
        private BigDecimal memberDiscountRate;
        private BigDecimal memberDiscountAmount;
        private BigDecimal payAmount;
    }
}