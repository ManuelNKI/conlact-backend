package com.conlact.conlact_backend.email;

import java.util.Set;

public enum EmailTemplate {
    NOTIFICATION("email/notification", "title", "message"),
    CONTACT_NOTIFICATION("email/contact-notification", "contactName", "contactEmail", "message"),
    ORDER_RECEIVED("email/order-received", "customerName", "orderNumber", "total"),
    ORDER_STATUS("email/order-status", "customerName", "orderNumber", "status");

    private final String templateName;
    private final Set<String> requiredVariables;

    EmailTemplate(String templateName, String... requiredVariables) {
        this.templateName = templateName;
        this.requiredVariables = Set.of(requiredVariables);
    }

    public String templateName() {
        return templateName;
    }

    public Set<String> requiredVariables() {
        return requiredVariables;
    }
}
