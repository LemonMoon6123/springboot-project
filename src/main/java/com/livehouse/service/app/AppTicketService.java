package com.livehouse.service.app;

import java.util.Map;

public interface AppTicketService {

    Map<String, Object> wallet(Long userId, String status, int current, int size);

    Map<String, Object> presentCode(Long userId, Long ticketId);
}