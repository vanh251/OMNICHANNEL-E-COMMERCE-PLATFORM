package com.example.notificationservice.service;

import com.example.notificationservice.model.EmailDelivery;
import com.example.notificationservice.model.OrderRecipient;
import com.example.notificationservice.repository.OrderRecipientRepository;
import com.example.notificationservice.repository.VerifiedEmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "email.worker.enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryWorker {
    private final MongoTemplate mongo;
    private final OrderRecipientRepository recipients;
    private final VerifiedEmailRepository verifiedEmails;
    private final JavaMailSender mailSender;

    @Value("${email.from}") private String from;

    @Scheduled(fixedDelayString = "${email.worker.poll-ms:1000}")
    public void run() {
        for (int i = 0; i < 20; i++) {
            EmailDelivery job = claim();
            if (job == null) break;
            deliver(job);
        }
    }

    private EmailDelivery claim() {
        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(new Criteria().orOperator(
                new Criteria().andOperator(Criteria.where("status").in("PENDING", "WAITING", "RETRY"),
                        Criteria.where("nextAttemptAt").lte(now)),
                new Criteria().andOperator(Criteria.where("status").is("SENDING"),
                        Criteria.where("lockedUntil").lte(now))));
        query.with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        String leaseToken = UUID.randomUUID().toString();
        return mongo.findAndModify(query, new Update().set("status", "SENDING")
                        .set("lockedUntil", now.plusMinutes(2)).set("leaseToken", leaseToken),
                FindAndModifyOptions.options().returnNew(true), EmailDelivery.class);
    }

    private void deliver(EmailDelivery job) {
        LocalDateTime now = LocalDateTime.now();
        String address;
        if ("EmailVerificationRequestedEvent".equals(job.getEventType())) {
            if (!now.isBefore(job.getVerificationExpiresAt())) {
                finish(job, "SKIPPED", null, job.getAttempts(), "Verification link expired");
                return;
            }
            address = job.getEmail();
        } else {
            OrderRecipient recipient = recipients.findById(job.getOrderId()).orElse(null);
            if (recipient == null) {
                if (job.getCreatedAt().isBefore(now.minusHours(24))) {
                    finish(job, "UNDELIVERABLE", null, job.getAttempts(), "Order recipient missing");
                } else waitFor(job, now.plusSeconds(30));
                return;
            }
            if (!verifiedEmails.existsById(recipient.getCustomerId() + ":" + recipient.getEmail())) {
                if (job.getCreatedAt().isBefore(now.minusDays(7))) {
                    finish(job, "SUPPRESSED", null, job.getAttempts(), "Email not verified within seven days");
                } else waitFor(job, now.plusMinutes(1));
                return;
            }
            address = recipient.getEmail();
        }
        int attempts = job.getAttempts() + 1;
        try {
            MailText content = content(job);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(address);
            helper.setSubject(content.subject());
            helper.setText(content.body(), false);
            message.setHeader("X-Notification-Event-ID", job.getEventId());
            mailSender.send(message);
            finish(job, "SENT", null, attempts, null);
            log.info("SMTP accepted notification eventId={} orderId={}", job.getEventId(), job.getOrderId());
        } catch (MailAuthenticationException exception) {
            finish(job, "PAUSED_CONFIG", null, attempts, "SMTP authentication failed");
            log.error("SMTP authentication failed; email delivery paused", exception);
        } catch (MailParseException | MessagingException exception) {
            finish(job, "DEAD", null, attempts, exception.getClass().getSimpleName());
        } catch (MailException exception) {
            if (attempts >= 5) finish(job, "DEAD", null, attempts, exception.getClass().getSimpleName());
            else finish(job, "RETRY", now.plusSeconds(backoffSeconds(attempts)), attempts,
                    exception.getClass().getSimpleName());
        }
    }

    private void waitFor(EmailDelivery job, LocalDateTime next) {
        finish(job, "WAITING", next, job.getAttempts(), null);
    }

    private void finish(EmailDelivery job, String status, LocalDateTime next, int attempts, String error) {
        Update update = new Update().set("status", status).set("attempts", attempts)
                .set("nextAttemptAt", next).set("lastError", error)
                .unset("lockedUntil").unset("leaseToken");
        if ("SENT".equals(status) || "SKIPPED".equals(status)) update.unset("verificationUrl");
        mongo.updateFirst(new Query(Criteria.where("id").is(job.getId())
                .and("leaseToken").is(job.getLeaseToken())), update, EmailDelivery.class);
    }

    private long backoffSeconds(int attempts) {
        return switch (attempts) { case 1 -> 60; case 2 -> 300; case 3 -> 1800; default -> 7200; };
    }

    private MailText content(EmailDelivery job) {
        String order = job.getOrderId();
        return switch (job.getEventType()) {
            case "EmailVerificationRequestedEvent" -> new MailText("Xác minh địa chỉ email",
                    "Vui lòng xác minh email của bạn trong 24 giờ: " + job.getVerificationUrl());
            case "OrderCreatedEvent" -> new MailText("Đã nhận đơn hàng #" + order,
                    "Đơn hàng #" + order + " đã được tạo.");
            case "PaymentSucceededEvent" -> new MailText("Thanh toán thành công cho đơn #" + order,
                    "Thanh toán cho đơn hàng #" + order + " đã thành công.");
            case "PaymentFailedEvent" -> new MailText("Thanh toán thất bại cho đơn #" + order,
                    "Thanh toán cho đơn hàng #" + order + " đã thất bại.");
            case "PaymentRefundedEvent" -> new MailText("Đã hoàn tiền cho đơn #" + order,
                    "Khoản thanh toán của đơn hàng #" + order + " đã được hoàn.");
            case "OrderCancelledEvent" -> new MailText("Đơn hàng #" + order + " đã hủy",
                    "Đơn hàng #" + order + " đã được hủy.");
            case "OrderShippedEvent" -> new MailText("Đơn hàng #" + order + " đang giao",
                    "Đơn hàng #" + order + " đang được giao.");
            case "OrderCompletedEvent" -> new MailText("Đơn hàng #" + order + " đã hoàn tất",
                    "Bạn đã xác nhận nhận đơn hàng #" + order + ".");
            default -> throw new IllegalArgumentException("Unsupported email event type");
        };
    }

    private record MailText(String subject, String body) {}
}
