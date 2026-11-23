package com.livehouse.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.TicketType;
import com.livehouse.mapper.TicketTypeMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 演出项目最低价：该项目下所有场次、上架票种的 MIN(price)。
 */
public final class PerformanceTicketPrice {

    private PerformanceTicketPrice() {
    }

    public static BigDecimal minListedPrice(TicketTypeMapper ticketTypeMapper, Long performanceId) {
        if (performanceId == null || ticketTypeMapper == null) {
            return null;
        }
        List<TicketType> types = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getStatus, 1)
                .inSql(TicketType::getShowId,
                        "SELECT id FROM tb_show WHERE performance_id = " + performanceId));
        return types.stream()
                .map(TicketType::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    public static String priceText(TicketTypeMapper ticketTypeMapper, Long performanceId) {
        BigDecimal min = minListedPrice(ticketTypeMapper, performanceId);
        if (min == null) {
            return "";
        }
        return "¥" + min.stripTrailingZeros().toPlainString() + " 起";
    }
}