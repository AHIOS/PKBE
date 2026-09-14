package com.uci.pkbe.service;

import com.uci.pkbe.domain.CredentialStatus;
import com.uci.pkbe.domain.DeviceCredential;
import com.yubico.webauthn.RegistrationResult;

public final class RegistrationMapper {

    private RegistrationMapper() {}

    public static DeviceCredential toPending(String deviceId, RegistrationResult result) {
        return new DeviceCredential(
                deviceId,
                result.getKeyId().getId(),
                result.getPublicKeyCose(),
                result.getSignatureCount(),
                "unknown",
                result.isBackupEligible(),
                result.isBackedUp(),
                CredentialStatus.PENDING);
    }
}
