package com.anupam.reminiscence.service;


import com.anupam.reminiscence.dto.auth.AuthResponse;
import com.anupam.reminiscence.dto.auth.LoginRequest;
import com.anupam.reminiscence.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}