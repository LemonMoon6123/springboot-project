package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.service.ITicketTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 票种控制器
 */
@RestController
@RequestMapping("/ticket-type")
public class TicketTypeController {

    @Resource
    private ITicketTypeService ticketTypeService;

    @GetMapping("/of/show/{showId}")
    public Result queryTicketTypesByShowId(@PathVariable("showId") Long showId) {
        return Result.ok(ticketTypeService.lambdaQuery()
                .eq(com.livehouse.entity.TicketType::getShowId, showId)
                .orderByAsc(com.livehouse.entity.TicketType::getPrice)
                .list());
    }
}
