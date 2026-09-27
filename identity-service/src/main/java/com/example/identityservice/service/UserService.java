package com.example.identityservice.service;

import com.example.identityservice.dto.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserResponse getCurrentUser(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));
        return authService.mapToUserResponse(user);
    }
}
