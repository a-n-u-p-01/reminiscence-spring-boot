package com.anupam.reminiscence.service;

import com.anupam.reminiscence.entity.UserEntity;

public interface JwtService {

    String generateToken(UserEntity user);

    String extractEmail(String token);

    boolean isTokenValid(String token, UserEntity user);
}