package com.example.identityservice.service;

import com.example.identityservice.dto.AuthResponse;
import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.dto.UserResponse;
import com.example.identityservice.entity.Role;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password("12345678")
                .phone("0912345678")
                .address("Ha Noi")
                .build();

        loginRequest = LoginRequest.builder()
                .email("vietanh@gmail.com")
                .password("12345678")
                .build();

        user = User.builder()
                .id(1L)
                .firstName("Viet")
                .lastName("Anh")
                .email("vietanh@gmail.com")
                .password("$2a$10$hashedPassword")
                .phone("0912345678")
                .address("Ha Noi")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("1. Register thành công")
    void testRegisterSuccess() {
        when(userRepository.existsByEmail("vietanh@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("vietanh@gmail.com");
        assertThat(response.getFirstName()).isEqualTo("Viet");
        assertThat(response.getLastName()).isEqualTo("Anh");
        assertThat(response.getRole()).isEqualTo("USER");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("2. Register email đã tồn tại")
    void testRegisterDuplicateEmail() {
        when(userRepository.existsByEmail("vietanh@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already exists: vietanh@gmail.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("4. Password được hash trước khi lưu")
    void testRegisterPasswordIsHashed() {
        when(userRepository.existsByEmail("vietanh@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        authService.register(registerRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashedPassword");
        assertThat(saved.getPassword()).isNotEqualTo("12345678");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("5. Login đúng credentials")
    void testLoginSuccess() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(user.getEmail(), "12345678"));
        when(userRepository.findByEmail("vietanh@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(), eq("USER"), eq(1L))).thenReturn("mock-access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("mock-refresh-token");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("vietanh@gmail.com");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("6. Login sai password")
    void testLoginWrongPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad credentials");

        verify(jwtService, never()).generateAccessToken(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("7. Login user không tồn tại")
    void testLoginUserNotFound() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("vietanh@gmail.com", "12345678"));
        when(userRepository.findByEmail("vietanh@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found with email: vietanh@gmail.com");
    }
}
