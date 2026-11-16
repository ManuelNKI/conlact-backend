package com.conlact.conlact_backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class OrderEmailEvent extends ApplicationEvent {

    private final UUID orderId;
    private final String customerEmail;
    private final String customerName;
    private final String orderNumber;
    private final String total;
    private final String status;
    private final String actionUrl;
    private final boolean isNewOrder;

    public OrderEmailEvent(Object source, UUID orderId, String customerEmail,
                           String customerName, String orderNumber, String total,
                           String status, String actionUrl, boolean isNewOrder) {
        super(source);
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.orderNumber = orderNumber;
        this.total = total;
        this.status = status;
        this.actionUrl = actionUrl;
        this.isNewOrder = isNewOrder;
    }
}
