package com.b101.butterfly.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 공통 해시 유틸리티.
 *
 * <p>API Key 저장/검증 등 결정적 단방향 해시가 필요한 곳에서 사용한다.
 * 패스워드처럼 느린 해시가 필요한 경우 BCrypt를 사용할 것.
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * 입력 문자열의 SHA-256 해시를 64자 hex 문자열로 반환한다.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
