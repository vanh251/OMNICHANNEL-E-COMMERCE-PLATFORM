package com.example.identityservice.repository;

import com.example.identityservice.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(String tokenHash);
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
    Optional<EmailVerificationToken> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
