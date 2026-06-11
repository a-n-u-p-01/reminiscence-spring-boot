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

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {

                // Cleans up the string parsing so Java processes the text newlines correctly
                String sanitizedPrivateKey = privateKey
                        .replace("\\n", "\n")
                        .replace("\"", ""); // Removes any trailing shell quotes

                // Re-build the JSON architecture directly into local system memory
                String simulatedJsonFile = "{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"" + projectId + "\",\n" +
                        "  \"private_key_id\": \"" + privateKeyId + "\",\n" +
                        "  \"private_key\": \"" + sanitizedPrivateKey + "\",\n" +
                        "  \"client_email\": \"" + clientEmail + "\"\n" +
                        "}";

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(
                                new ByteArrayInputStream(simulatedJsonFile.getBytes(StandardCharsets.UTF_8))
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