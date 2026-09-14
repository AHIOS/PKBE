package com.uci.pkbe.domain;

import com.yubico.webauthn.AssertionRequest;
import java.time.Instant;

public record AssertionCeremony(
        String sessionToken,
        String username,
        String deviceId,
        String pendingCredentialId,
        AssertionRequest request,
        Instant expiresAt) {

    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
