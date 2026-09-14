package com.uci.pkbe.domain;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.AssertionRequest;
import java.time.Instant;

public record RegistrationCeremony(
        String sessionToken,
        String username,
        String deviceId,
        PublicKeyCredentialCreationOptions options,
        Instant expiresAt) {

    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
