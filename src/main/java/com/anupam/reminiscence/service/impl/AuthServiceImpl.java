package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.config.AuthException;
import com.anupam.reminiscence.constants.AccountStatus;
import com.anupam.reminiscence.dto.auth.AuthResponse;
import com.anupam.reminiscence.dto.auth.LoginRequest;
import com.anupam.reminiscence.dto.auth.RegisterRequest;
import com.anupam.reminiscence.entity.PendingRegistrationEntity;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.PendingRegistrationRepository;
import com.anupam.reminiscence.repo.UserRepository;
import com.anupam.reminiscence.service.AuthService;
import com.anupam.reminiscence.service.EmailService;
import com.anupam.reminiscence.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final EmailService emailService;

    @Override
    public void register(RegisterRequest request) {

        try {
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
                throw new AuthException("Email already registered");
            }

            String otp = String.format("%06d", new Random().nextInt(1000000));

            PendingRegistrationEntity pendingRegistration =
                    PendingRegistrationEntity.builder()
                            .email(email)
                            .fullName(request.getFullName().trim())
                            .passwordHash(
                                    passwordEncoder.encode(request.getPassword())
                            )
                            .otp(otp)
                            .expiryTime(Instant.now().plus(10, ChronoUnit.MINUTES))
                            .createdAt(Instant.now())
                            .build();

            pendingRegistrationRepository.save(pendingRegistration);

            emailService.sendOtpEmail(email, otp);
        }catch (AuthException e) {
            throw  e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        if(user.getIsEmailVerified() == false){
            throw new AuthException("Please verify email");
        }

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



    @Override
    public AuthResponse verifyOtp(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        PendingRegistrationEntity pending =
                pendingRegistrationRepository.findById(email)
                        .orElseThrow(() ->
                                new IllegalArgumentException("No OTP request found"));

        if (pending.getExpiryTime().isBefore(Instant.now())) {
            pendingRegistrationRepository.delete(pending);
            throw new IllegalArgumentException("OTP expired");
        }

        if (!pending.getOtp().equals(request.getOtp())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        Instant now = Instant.now();

        UserEntity user = UserEntity.builder()
                .fullName(pending.getFullName())
                .email(pending.getEmail())
                .passwordHash(pending.getPasswordHash())
                .timezone("Asia/Kolkata")
                .dailyRevisionGoal(5)
                .onboardingCompleted(false)
                .isEmailVerified(true)
                .algorithmPreference("reminiscenceFsrsV5")
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserEntity savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        pendingRegistrationRepository.delete(pending);

        return new AuthResponse(
                token,
                savedUser.getEmail(),
                savedUser.getFullName()
        );
    }
}