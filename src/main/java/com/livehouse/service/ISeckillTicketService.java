package com.livehouse.service;

import com.livehouse.dto.Result;

/**
 * 秒杀票务服务接口
 */
public interface ISeckillTicketService {

    /**
     * 秒杀抢票
     * @param ticketTypeId 票种ID
     * @param quantity 购买数量
     * @return 秒杀结果
     */
    Result seckillTicket(Long ticketTypeId, Integer quantity);
}