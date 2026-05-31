package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    @Async
    public void sendOtpEmail(String toEmail, String otp) {

        long startTime = System.currentTimeMillis();

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(
                    fromEmail,
                    "Reminiscence"
            );

            helper.setTo(toEmail);

            helper.setSubject("Verify Your Email - Reminiscence");

            helper.setText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <body>
                        <h2>Verify Your Email</h2>
                        <p>Your OTP is:</p>
                        <h1>%s</h1>
                        <p>Valid for 10 minutes.</p>
                    </body>
                    </html>
                    """.formatted(otp),
                    true
            );

            log.info("Sending OTP email to {}", toEmail);

            mailSender.send(message);

            long endTime = System.currentTimeMillis();

            log.info(
                    "OTP email sent successfully to {} in {} ms",
                    toEmail,
                    (endTime - startTime)
            );

        } catch (Exception e) {

            long endTime = System.currentTimeMillis();

            log.error(
                    "Failed to send OTP email to {} after {} ms",
                    toEmail,
                    (endTime - startTime),
                    e
            );

            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}