package com.testtask.authapi.service;

import com.testtask.authapi.dto.AuthRequest;
import com.testtask.authapi.dto.AuthResponse;
import com.testtask.authapi.entity.User;
import com.testtask.authapi.exception.EmailAlreadyExistsException;
import com.testtask.authapi.exception.InvalidCredentialsException;
import com.testtask.authapi.repository.UserRepository;
import com.testtask.authapi.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public void register(AuthRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateAuthToken(user.getEmail());
        return new AuthResponse(token);
    }
}
