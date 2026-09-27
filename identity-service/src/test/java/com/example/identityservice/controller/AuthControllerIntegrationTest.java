package com.example.identityservice.controller;

import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.entity.Role;
import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Register thành công (201 Created)")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password("12345678")
                .phone("0912345678")
                .address("Ha Noi")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("vietanh@gmail.com"))
                .andExpect(jsonPath("$.firstName").value("Viet"))
                .andExpect(jsonPath("$.lastName").value("Anh"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("2. Register email đã tồn tại (400 Bad Request)")
    void testRegisterDuplicateEmail() throws Exception {
        User existingUser = User.builder()
                .firstName("Existing")
                .lastName("User")
                .email("vietanh@gmail.com")
                .password(passwordEncoder.encode("12345678"))
                .role(Role.USER)
                .enabled(true)
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = RegisterRequest.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password("12345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Email already exists")));
    }

    @Test
    @DisplayName("3. Register email không hợp lệ (400 Bad Request)")
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("invalid-email-format")
                .password("12345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("5. Login đúng credentials (200 OK + tokens)")
    void testLoginSuccess() throws Exception {
        User user = User.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password(passwordEncoder.encode("12345678"))
                .role(Role.USER)
                .enabled(true)
                .build();
        userRepository.save(user);

        LoginRequest request = LoginRequest.builder()
                .email("vietanh@gmail.com")
                .password("12345678")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("vietanh@gmail.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("6. Login sai password (401 Unauthorized)")
    void testLoginWrongPassword() throws Exception {
        User user = User.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password(passwordEncoder.encode("12345678"))
                .role(Role.USER)
                .enabled(true)
                .build();
        userRepository.save(user);

        LoginRequest request = LoginRequest.builder()
                .email("vietanh@gmail.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
