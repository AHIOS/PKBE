package com.uci.pkbe.web.dto;

public record MeResponse(
        String username,
        String thisDeviceId,
        String thisDeviceStatus,
        String activeDeviceId,
        String pendingDeviceId,
        CredentialView activeCredential) {

    public record CredentialView(
            String credentialIdPrefix, String aaguid, boolean backupEligible, boolean backupState) {}
}
