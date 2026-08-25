/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PasswordHasher {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        String saltHex = bytesToHex(salt);
        return saltHex + ":" + sha256(saltHex + password);
    }

    public static boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || password == null) {
            return false;
        }
        int separator = storedHash.indexOf(':');
        if (separator < 0) {
            return sha256(password).equals(storedHash);
        }
        String saltHex = storedHash.substring(0, separator);
        String expectedHash = storedHash.substring(separator + 1);
        return sha256(saltHex + password).equals(expectedHash);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ошибка получения хэша пароля", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
