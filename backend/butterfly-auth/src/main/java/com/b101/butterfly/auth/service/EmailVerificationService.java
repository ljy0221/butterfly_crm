package com.b101.butterfly.auth.service;

import com.b101.butterfly.auth.exception.AuthErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EmailVerificationService {

    private static final int MAX_SEND_COUNT = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COUNT_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private static final String CODE_KEY_PREFIX = "auth:email:verify:";
    private static final String COUNT_KEY_PREFIX = "auth:email:count:";
    private static final String VERIFIED_KEY_PREFIX = "auth:email:verified:";

    private final RedisService redisService;
    private final EmailService emailService;

    public void sendCode(String email) {
        String countKey = COUNT_KEY_PREFIX + email;
        long count = redisService.get(countKey)
                .map(Long::parseLong)
                .orElse(0L);

        if (count >= MAX_SEND_COUNT) {
            throw new BusinessException(AuthErrorCode.EMAIL_CODE_LIMIT);
        }

        String code = generateCode();
        redisService.set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        redisService.set(countKey, String.valueOf(count + 1), COUNT_TTL);

        emailService.sendVerificationCode(email, code);
    }

    public void verifyCode(String email, String code) {
        String stored = redisService.get(CODE_KEY_PREFIX + email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_CODE_EXPIRED));

        if (!stored.equals(code)) {
            throw new BusinessException(AuthErrorCode.EMAIL_CODE_INVALID);
        }

        redisService.delete(CODE_KEY_PREFIX + email);
        redisService.delete(COUNT_KEY_PREFIX + email);
        redisService.set(VERIFIED_KEY_PREFIX + email, "1", VERIFIED_TTL);
    }

    public boolean isVerified(String email) {
        return redisService.hasKey(VERIFIED_KEY_PREFIX + email);
    }

    public void clearVerified(String email) {
        redisService.delete(VERIFIED_KEY_PREFIX + email);
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }
}
