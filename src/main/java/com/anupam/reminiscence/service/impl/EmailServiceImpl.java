package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final RestTemplate restTemplate;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Override
    @Async
    public void sendOtpEmail(String toEmail, String otp) {

        long startTime = System.currentTimeMillis();

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> body = Map.of(
                    "sender", Map.of(
                            "name", "Reminiscence",
                            "email", "anutarai.2001@gmail.com"
                    ),
                    "to", List.of(
                            Map.of("email", toEmail)
                    ),
                    "subject", "Verify Your Email - Reminiscence",
                    "htmlContent",
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
                    """.formatted(otp)
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            log.info("Sending OTP email to {}", toEmail);

            restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email",
                    request,
                    String.class
            );

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