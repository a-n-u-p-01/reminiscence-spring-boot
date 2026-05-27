package com.anupam.reminiscence.utils;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Utility to provide the current timezone for the request thread.
 * JwtAuthenticationFilter sets the current zone based on the X-Timezone header.
 */
public class TimezoneProvider {
    private static final ThreadLocal<ZoneId> currentZone = new ThreadLocal<>();

    public static void setCurrentZone(ZoneId zone) {
        currentZone.set(zone);
    }

    public static void clear() {
        currentZone.remove();
    }

    public static ZoneId getCurrentZone() {
        ZoneId zone = currentZone.get();
        return zone != null ? zone : ZoneId.systemDefault();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(getCurrentZone());
    }

    public static LocalDate today() {
        return LocalDate.now(getCurrentZone());
    }
}
