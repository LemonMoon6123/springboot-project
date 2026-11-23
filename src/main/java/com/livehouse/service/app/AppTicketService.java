package com.livehouse.service.app;

import com.livehouse.vo.app.ticket.TicketPresentVo;
import com.livehouse.vo.app.ticket.TicketWalletVo;

public interface AppTicketService {

    TicketWalletVo wallet(Long userId, String status, int current, int size);

    TicketPresentVo presentCode(Long userId, Long ticketId);
}