package com.conlact.conlact_backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ContactEmailEvent extends ApplicationEvent {

    private final UUID contactMessageId;
    private final String contactName;
    private final String contactEmail;
    private final String message;
    private final String subject;
    private final String phone;

    public ContactEmailEvent(Object source, UUID contactMessageId,
                             String contactName, String contactEmail,
                             String message, String subject, String phone) {
        super(source);
        this.contactMessageId = contactMessageId;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.message = message;
        this.subject = subject;
        this.phone = phone;
    }
}
