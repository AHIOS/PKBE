package com.uci.pkbe.store;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    private final ConcurrentHashMap<String, String> usernameByToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String create(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        usernameByToken.put(token, username);
        return token;
    }

    public Optional<String> findUsername(String token) {
        return Optional.ofNullable(usernameByToken.get(token));
    }

    public void invalidate(String token) {
        usernameByToken.remove(token);
    }

    public void clear() {
        usernameByToken.clear();
    }
}
