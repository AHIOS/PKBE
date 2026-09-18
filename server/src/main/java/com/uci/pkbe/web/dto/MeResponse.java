package com.uci.pkbe.web.dto;

public record MeResponse(
        String username,
        String thisDeviceId,
        String thisDeviceStatus,
        String activeDeviceId,
        String pendingDeviceId,
        /** Full base64url credential id for THIS device when ACTIVE or PENDING; used to probe Passwords app. */
        String thisDeviceCredentialId,
        CredentialView activeCredential) {

    public record CredentialView(
            String credentialIdPrefix, String aaguid, boolean backupEligible, boolean backupState) {}
}
