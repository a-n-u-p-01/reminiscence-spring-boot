package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.AccountStatus;
import com.anupam.reminiscence.dto.auth.AuthResponse;
import com.anupam.reminiscence.dto.auth.LoginRequest;
import com.anupam.reminiscence.dto.auth.RegisterRequest;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.UserRepository;
import com.anupam.reminiscence.service.AuthService;
import com.anupam.reminiscence.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public AuthResponse register(RegisterRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        LocalDateTime now = LocalDateTime.now();

        UserEntity user = UserEntity.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .timezone("Asia/Kolkata")
                .dailyRevisionGoal(5)
                .onboardingCompleted(false)
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserEntity savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(
                token,
                savedUser.getEmail(),
                savedUser.getFullName()
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        String email = request.getEmail().trim().toLowerCase();

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid credentials")
                );

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName()
        );
    }
}