package com.example.identityservice.controller;

import com.example.identityservice.dto.UserResponse;
import com.example.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile and details APIs")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current logged-in user", description = "Returns profile details of the authenticated user")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        UserResponse user = userService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(user);
    }
}
