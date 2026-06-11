package com.anupam.reminiscence.service.impl;

import com.google.firebase.messaging.*;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    public void sendNotification(String targetToken, String title, String body, String targetRoute) {
        // 1. Android Specific Configs (Priority & Sounds)
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .setClickAction("FCM_PLUGIN_ACTIVITY") // Wakes up Capacitor wrapper
                        .build())
                .build();

        // 2. iOS Specific Configs (APNs payload)
        ApnsConfig apnsConfig = ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setSound("default")
                        .build())
                .build();

        // 3. Assemble unified message structure with explicit 'notification' metadata for closed states
        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("route", targetRoute) // Read by client-side application router
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("Successfully sent message: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("Failed to send push notification to token: " + targetToken + " | " + e.getMessage());
        }
    }
}