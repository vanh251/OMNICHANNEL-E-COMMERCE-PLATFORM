package com.example.identityservice.controller;

import com.example.identityservice.entity.Role;
import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.security.CustomUserDetails;
import com.example.identityservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityAndAuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        userRepository.deleteAll();

        User user = User.builder()
                .firstName("User")
                .lastName("Regular")
                .email("user@example.com")
                .password(passwordEncoder.encode("12345678"))
                .phone("0912345678")
                .address("Ha Noi")
                .role(Role.USER)
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);
        userToken = jwtService.generateAccessToken(new CustomUserDetails(savedUser), "USER", savedUser.getId());

        User admin = User.builder()
                .firstName("Admin")
                .lastName("Super")
                .email("admin@example.com")
                .password(passwordEncoder.encode("admin1234"))
                .phone("0987654321")
                .address("Da Nang")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        User savedAdmin = userRepository.save(admin);
        adminToken = jwtService.generateAccessToken(new CustomUserDetails(savedAdmin), "ADMIN", savedAdmin.getId());
    }

    @Test
    @DisplayName("11. USER truy cập user endpoint (/api/users/me) → 200 OK")
    void testUserAccessUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.firstName").value("User"))
                .andExpect(jsonPath("$.lastName").value("Regular"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("12. Không có token truy cập user endpoint (/api/users/me) → 401 Unauthorized")
    void testNoTokenAccessUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("13. USER truy cập admin endpoint (/api/admin/test) → 403 Forbidden")
    void testUserAccessAdminEndpointForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("14. ADMIN truy cập admin endpoint (/api/admin/test) → 200 OK")
    void testAdminAccessAdminEndpointSuccess() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Admin access granted"));
    }

    @Test
    @DisplayName("Không có token truy cập admin endpoint → 401 Unauthorized")
    void testNoTokenAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
