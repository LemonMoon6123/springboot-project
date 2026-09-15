package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.Result;
import com.livehouse.entity.ElectronicTicket;

/**
 * 电子票服务接口
 */
public interface IElectronicTicketService extends IService<ElectronicTicket> {

    /**
     * 生成电子票
     * @param orderId 订单ID
     * @return 生成结果
     */
    Result generateElectronicTickets(Long orderId);

    /**
     * 核销电子票
     * @param ticketCode 电子票号
     * @return 核销结果
     */
    Result verifyTicket(String ticketCode);

    /**
     * 查询电子票详情
     * @param ticketCode 电子票号
     * @return 电子票信息
     */
    Result getTicketByCode(String ticketCode);

    /**
     * 查询用户电子票列表
     * @param userId 用户ID
     * @return 电子票列表
     */
    Result queryUserTickets(Long userId);
}