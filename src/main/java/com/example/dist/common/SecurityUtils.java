package com.example.dist.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(secret), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String issueCompactToken(String subject, List<String> roles, Instant now, long ttlSeconds, String secret) {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(Locale.ROOT,
                "{\"sub\":\"%s\",\"roles\":%s,\"iat\":%d,\"exp\":%d}",
                escape(subject), toJsonArray(roles), now.getEpochSecond(), now.plusSeconds(ttlSeconds).getEpochSecond());
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha256(secret, header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    public static boolean validateCompactToken(String token, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            String expected = hmacSha256(secret, parts[0] + "." + parts[1]);
            if (!expected.equals(parts[2])) return false;
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // naive exp check
            int expIdx = payloadJson.indexOf("\"exp\":");
            if (expIdx < 0) return false;
            String tail = payloadJson.substring(expIdx + 6);
            StringBuilder num = new StringBuilder();
            for (char c : tail.toCharArray()) {
                if (Character.isDigit(c)) num.append(c); else break;
            }
            long exp = Long.parseLong(num.toString());
            return Instant.now().getEpochSecond() < exp;
        } catch (Exception e) {
            return false;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonArray(List<String> roles) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(escape(roles.get(i))).append('"');
        }
        return b.append(']').toString();
    }
}
