package com.uci.pkbe.domain;

import com.yubico.webauthn.data.ByteArray;

public record DeviceCredential(
        String deviceId,
        ByteArray credentialId,
        ByteArray publicKeyCose,
        long signatureCount,
        String aaguid,
        boolean backupEligible,
        boolean backupState,
        CredentialStatus status) {

    public DeviceCredential withStatus(CredentialStatus newStatus) {
        return new DeviceCredential(
                deviceId,
                credentialId,
                publicKeyCose,
                signatureCount,
                aaguid,
                backupEligible,
                backupState,
                newStatus);
    }

    public String credentialIdPrefix() {
        String b64 = credentialId.getBase64Url();
        return b64.substring(0, Math.min(8, b64.length()));
    }
}
