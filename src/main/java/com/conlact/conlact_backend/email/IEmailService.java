package com.conlact.conlact_backend.email;

import com.conlact.conlact_backend.entity.ContactMessage;

import java.util.concurrent.CompletableFuture;

public interface IEmailService {
    CompletableFuture<Void> sendEmail(TemplatedEmail email);

    void deliverDirectly(TemplatedEmail email);

    boolean isEmailDeliveryEnabled();

    void sendContactNotificationToAdmin(ContactMessage contactMessage);

    /**
     * Sends an OTP code synchronously.
     * Throws {@link com.conlact.conlact_backend.email.EmailDeliveryException} if email
     * delivery is disabled or if sending fails.
     * Callers must not mark the challenge as sent until this method returns successfully.
     *
     * @param recipient the email address to send the code to
     * @param userName  display name of the user
     * @param code      the 6-digit OTP code
     * @param template  the email template to use (PASSWORD_RESET_CODE or EMAIL_CHANGE_CODE)
     */
    void sendOtpCode(String recipient, String userName, String code, EmailTemplate template);
}
