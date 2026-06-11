package com.anupam.reminiscence.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.project.id}")
    private String projectId;

    @Value("${firebase.private.key.id}")
    private String privateKeyId;

    @Value("${firebase.private.key}")
    private String privateKey;

    @Value("${firebase.client.email}")
    private String clientEmail;

    @Value("${firebase.client.id}")
    private String clientId;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {

                String sanitizedPrivateKey = privateKey
                        .replace("\\n", "\n")
                        .trim();

                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("type", "service_account");
                jsonMap.put("project_id", projectId.trim());
                jsonMap.put("private_key_id", privateKeyId.trim());
                jsonMap.put("private_key", sanitizedPrivateKey);
                jsonMap.put("client_email", clientEmail.trim());
                jsonMap.put("client_id", clientId.trim()); // Added this line

                ObjectMapper mapper = new ObjectMapper();
                String perfectJsonString = mapper.writeValueAsString(jsonMap);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(
                                new ByteArrayInputStream(perfectJsonString.getBytes(StandardCharsets.UTF_8))
                        ))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase Admin SDK initialized successfully step-by-step!");
            }
        } catch (IOException e) {
            System.err.println("❌ Error initializing Firebase Admin SDK: " + e.getMessage());
        }
    }
}