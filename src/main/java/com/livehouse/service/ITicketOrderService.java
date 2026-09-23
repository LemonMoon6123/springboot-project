package com.livehouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livehouse.dto.Result;
import com.livehouse.entity.TicketOrder;

/**
 * 票务订单服务接口
 */
public interface ITicketOrderService extends IService<TicketOrder> {

    /**
     * 模拟支付回调
     * @param orderId 订单ID
     * @return 支付结果
     */
    Result simulatePayment(Long orderId);

    /**
     * 申请退票
     *
     * 校验通过后，在同一个事务里：更新订单状态为"已退票"、将订单下未核销的电子票作废、
     * 落库一条退票记录（本地消息表，状态=处理中）；事务提交后异步发送MQ消息触发
     * Redis/MySQL库存回补。即使消息丢失，定时任务也会扫描未完成的退票记录重新发送，
     * 保证库存回补的最终一致性。
     *
     * @param orderId  订单ID
     * @param userId   发起退票的用户ID（用于校验订单归属）
     * @param reason   退票原因
     * @return 处理结果
     */
    Result requestRefund(Long orderId, Long userId, String reason);

    /**
     * 查询订单的退票记录状态
     * @param orderId 订单ID
     * @param userId  用户ID（用于校验订单归属）
     * @return 退票记录，不存在则返回失败信息
     */
    Result getRefundStatus(Long orderId, Long userId);
}