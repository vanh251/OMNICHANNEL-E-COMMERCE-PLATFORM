package com.example.identityservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin only management APIs")
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

    @GetMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin test endpoint", description = "Verifies ROLE_ADMIN access")
    public ResponseEntity<String> adminTest() {
        return ResponseEntity.ok("Admin access granted");
    }
}
