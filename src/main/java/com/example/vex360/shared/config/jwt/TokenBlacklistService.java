package com.example.vex360.shared.config.jwt;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TokenBlacklistService {

    // Key: JWT token signature/payload, Value: Expiration Date
    private final Map<String, Date> blacklist = new ConcurrentHashMap<>();

    public void blacklistToken(String token, Date expirationDate) {
        if (token == null || expirationDate == null) {
            return;
        }
        blacklist.put(token, expirationDate);
        log.info("Access token blacklisted until: {}", expirationDate);
    }

    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        Date expirationDate = blacklist.get(token);
        if (expirationDate == null) {
            return false;
        }
        if (expirationDate.before(new Date())) {
            blacklist.remove(token); // Lazy cleanup
            return false;
        }
        return true;
    }
}
