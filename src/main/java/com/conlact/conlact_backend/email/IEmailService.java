package com.conlact.conlact_backend.email;

import com.conlact.conlact_backend.entity.ContactMessage;

import java.util.concurrent.CompletableFuture;

public interface IEmailService {
    CompletableFuture<Void> sendEmail(TemplatedEmail email);

    void sendContactNotificationToAdmin(ContactMessage contactMessage);
}
