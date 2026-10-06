package com.conlact.conlact_backend.email;

import java.util.concurrent.CompletableFuture;

public interface IEmailService {
    CompletableFuture<Void> sendEmail(TemplatedEmail email);
}
