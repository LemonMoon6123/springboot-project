package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.service.ISeckillTicketService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/seckill")
public class SeckillTicketController {

    @Resource
    private ISeckillTicketService seckillTicketService;

    /**
     * 秒杀抢票
     * @param ticketTypeId 票种ID
     * @param quantity 购买数量，默认为1
     * @return 秒杀结果
     */
    @PostMapping("/ticket/{ticketTypeId}")
    public Result seckillTicket(
            @PathVariable("ticketTypeId") Long ticketTypeId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity
    ) {
        return seckillTicketService.seckillTicket(ticketTypeId, quantity);
    }
}