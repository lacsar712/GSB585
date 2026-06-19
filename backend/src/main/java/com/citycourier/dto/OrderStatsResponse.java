package com.citycourier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderStatsResponse {
    private long total;
    private long created;
    private long assigned;
    private long pickedUp;
    private long inTransit;
    private long delivered;
    private long cancelled;
}
