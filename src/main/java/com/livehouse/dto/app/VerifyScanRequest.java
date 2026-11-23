package com.livehouse.dto.app;

import lombok.Data;

@Data
public class VerifyScanRequest {

    private Long showId;
    private String ticketCode;
    private String shortCode;
    private String gate;
}