package com.uci.pkbe.store;

import com.uci.pkbe.domain.AssertionCeremony;
import com.uci.pkbe.domain.RegistrationCeremony;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CeremonyStore {

    private final ConcurrentHashMap<String, RegistrationCeremony> registrations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AssertionCeremony> assertions = new ConcurrentHashMap<>();

    public void putRegistration(RegistrationCeremony ceremony) {
        registrations.put(ceremony.sessionToken(), ceremony);
    }

    public Optional<RegistrationCeremony> takeRegistration(String sessionToken) {
        return Optional.ofNullable(registrations.remove(sessionToken));
    }

    public void putAssertion(AssertionCeremony ceremony) {
        assertions.put(ceremony.sessionToken(), ceremony);
    }

    public Optional<AssertionCeremony> takeAssertion(String sessionToken) {
        return Optional.ofNullable(assertions.remove(sessionToken));
    }

    @Scheduled(fixedDelay = 30_000)
    public void evictExpired() {
        Instant now = Instant.now();
        registrations.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        assertions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    public void clear() {
        registrations.clear();
        assertions.clear();
    }
}
