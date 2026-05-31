package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

    @Service
    @RequiredArgsConstructor
    public class EmailServiceImpl implements EmailService {

        @Value("${resend.api.key}")
        private String apiKey;

        private final RestTemplate restTemplate;

        @Override
        public void sendOtpEmail(String toEmail, String otp) {

            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("from", "reminiscence@resend.dev");
            body.put("to", List.of(toEmail));
            body.put("subject", "Verify Your Email");

            body.put(
                    "html",
                    "<h2>Email Verification for reminiscence </h2>" +
                            "<p>Your OTP is:</p>" +
                            "<h1>" + otp + "</h1>" +
                            "<p>Valid for 10 minutes.</p>"
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );
        }
    }