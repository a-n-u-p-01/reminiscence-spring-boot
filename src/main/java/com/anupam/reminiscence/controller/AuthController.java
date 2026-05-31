package com.anupam.reminiscence.controller;
import com.anupam.reminiscence.dto.auth.AuthResponse;
import com.anupam.reminiscence.dto.auth.LoginRequest;
import com.anupam.reminiscence.dto.auth.RegisterRequest;
import com.anupam.reminiscence.service.AuthService;
import com.anupam.reminiscence.service.impl.DailyEntryProcessingScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DailyEntryProcessingScheduler dailyEntryProcessingScheduler;


    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("Otp sent to you email please verify");
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/dailyentry-process")
    public ResponseEntity<String> dailyEntryProcess() {
        dailyEntryProcessingScheduler.processPendingEntries();
        return ResponseEntity.ok("Success");
    }
    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Success");
    }


    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody LoginRequest loginRequest){

        return ResponseEntity.ok(authService.verifyOtp(loginRequest));
    }

}