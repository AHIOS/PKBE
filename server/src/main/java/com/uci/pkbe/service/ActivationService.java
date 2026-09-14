package com.uci.pkbe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.pkbe.config.PkbeProperties;
import com.uci.pkbe.domain.AssertionCeremony;
import com.uci.pkbe.domain.CredentialStatus;
import com.uci.pkbe.domain.DeviceCredential;
import com.uci.pkbe.domain.RegistrationCeremony;
import com.uci.pkbe.domain.UserAccount;
import com.uci.pkbe.store.CeremonyStore;
import com.uci.pkbe.store.UserStore;
import com.uci.pkbe.web.ApiException;
import com.uci.pkbe.web.dto.MeResponse;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttachment;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialHint;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActivationService {

    private final RelyingParty relyingParty;
    private final UserStore userStore;
    private final CeremonyStore ceremonyStore;
    private final PkbeProperties properties;
    private final ObjectMapper objectMapper;

    public ActivationService(
            RelyingParty relyingParty,
            UserStore userStore,
            CeremonyStore ceremonyStore,
            PkbeProperties properties,
            ObjectMapper objectMapper) {
        this.relyingParty = relyingParty;
        this.userStore = userStore;
        this.ceremonyStore = ceremonyStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MeResponse me(String username, String deviceId) {
        UserAccount account = userStore.getOrCreate(username);
        synchronized (account) {
            DeviceCredential active = account.active();
            MeResponse.CredentialView view = null;
            if (active != null) {
                view = new MeResponse.CredentialView(
                        active.credentialIdPrefix(),
                        active.aaguid(),
                        active.backupEligible(),
                        active.backupState());
            }
            String activeDeviceId = active == null ? null : active.deviceId();
            String pendingDeviceId = account.pending() == null ? null : account.pending().deviceId();
            return new MeResponse(
                    account.username(),
                    deviceId,
                    account.statusFor(deviceId).name(),
                    activeDeviceId,
                    pendingDeviceId,
                    view);
        }
    }

    public String startRegistration(String username, String deviceId, String sessionToken) {
        requireDeviceId(deviceId);
        UserAccount account = userStore.getOrCreate(username);
        synchronized (account) {
            if (account.active() != null && account.active().deviceId().equals(deviceId)) {
                throw ApiException.conflict("ALREADY_ACTIVE", "This device is already active");
            }
        }
        long timeoutMs = properties.getChallengeTtlSeconds() * 1000L;
        UserIdentity user = UserIdentity.builder()
                .name(username)
                .displayName(username)
                .id(account.userHandle())
                .build();
        AuthenticatorSelectionCriteria selection = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();
        StartRegistrationOptions startOptions = StartRegistrationOptions.builder()
                .user(user)
                .timeout(timeoutMs)
                .authenticatorSelection(selection)
                .hints(PublicKeyCredentialHint.CLIENT_DEVICE)
                .build();
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(startOptions);
        ceremonyStore.putRegistration(new RegistrationCeremony(
                sessionToken, username, deviceId, options, Instant.now().plusSeconds(properties.getChallengeTtlSeconds())));
        try {
            return unwrapPublicKey(options.toCredentialsCreateJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public MeResponse finishRegistration(String username, String deviceId, String sessionToken, String credentialJson) {
        requireDeviceId(deviceId);
        Optional<RegistrationCeremony> maybeCeremony = ceremonyStore.takeRegistration(sessionToken);
        if (maybeCeremony.isEmpty()) {
            throw ApiException.unprocessable("CHALLENGE_EXPIRED", "No registration ceremony in progress");
        }
        RegistrationCeremony ceremony = maybeCeremony.get();
        if (ceremony.expired() || !ceremony.username().equals(username) || !ceremony.deviceId().equals(deviceId)) {
            throw ApiException.unprocessable("CHALLENGE_EXPIRED", "Registration ceremony expired or mismatched");
        }
        RegistrationResult result;
        try {
            FinishRegistrationOptions finishOptions = FinishRegistrationOptions.builder()
                    .request(ceremony.options())
                    .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .build();
            result = relyingParty.finishRegistration(finishOptions);
        } catch (IOException e) {
            throw ApiException.badRequest("Invalid registration credential JSON");
        } catch (RegistrationFailedException e) {
            throw ApiException.unprocessable("WEBAUTHN_FAILED", e.getMessage());
        }
        if (properties.isRequireDeviceBound() && result.isBackupEligible()) {
            throw ApiException.unprocessable(
                    "DEVICE_BOUND_REQUIRED", "Authenticator created a backup-eligible credential");
        }
        DeviceCredential created = RegistrationMapper.toPending(deviceId, result);
        UserAccount account = userStore.getOrCreate(username);
        synchronized (account) {
            if (account.active() != null && account.active().deviceId().equals(deviceId)) {
                throw ApiException.conflict("ALREADY_ACTIVE", "This device is already active");
            }
            if (account.active() == null) {
                account.setActive(created.withStatus(CredentialStatus.ACTIVE));
            } else {
                account.setPending(created.withStatus(CredentialStatus.PENDING));
            }
        }
        return me(username, deviceId);
    }

    public String startHandover(String username, String deviceId, String sessionToken) {
        requireDeviceId(deviceId);
        UserAccount account = userStore.getOrCreate(username);
        synchronized (account) {
            if (account.active() == null) {
                throw ApiException.unprocessable("NO_ACTIVE_DEVICE", "No active device to authorize handover");
            }
            if (account.active().deviceId().equals(deviceId)) {
                throw ApiException.conflict("ALREADY_ACTIVE", "This device is already active");
            }
            if (account.pending() == null || !account.pending().deviceId().equals(deviceId)) {
                throw ApiException.unprocessable(
                        "NO_PENDING_ENROLL", "Register a pending passkey on this device before handover");
            }
            long timeoutMs = properties.getChallengeTtlSeconds() * 1000L;
            StartAssertionOptions startOptions = StartAssertionOptions.builder()
                    .username(username)
                    .timeout(timeoutMs)
                    .userVerification(UserVerificationRequirement.REQUIRED)
                    .hints(PublicKeyCredentialHint.HYBRID)
                    .build();
            AssertionRequest request = relyingParty.startAssertion(startOptions);
            ceremonyStore.putAssertion(new AssertionCeremony(
                    sessionToken,
                    username,
                    deviceId,
                    account.pending().credentialId().getBase64Url(),
                    request,
                    Instant.now().plusSeconds(properties.getChallengeTtlSeconds())));
            try {
                return unwrapPublicKey(request.getPublicKeyCredentialRequestOptions().toCredentialsGetJson());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public MeResponse finishHandover(String username, String deviceId, String sessionToken, String credentialJson) {
        requireDeviceId(deviceId);
        Optional<AssertionCeremony> maybeCeremony = ceremonyStore.takeAssertion(sessionToken);
        if (maybeCeremony.isEmpty()) {
            throw ApiException.unprocessable("CHALLENGE_EXPIRED", "No handover ceremony in progress");
        }
        AssertionCeremony ceremony = maybeCeremony.get();
        if (ceremony.expired() || !ceremony.username().equals(username) || !ceremony.deviceId().equals(deviceId)) {
            throw ApiException.unprocessable("CHALLENGE_EXPIRED", "Handover ceremony expired or mismatched");
        }
        AssertionResult result;
        try {
            FinishAssertionOptions finishOptions = FinishAssertionOptions.builder()
                    .request(ceremony.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .build();
            result = relyingParty.finishAssertion(finishOptions);
        } catch (IOException e) {
            throw ApiException.badRequest("Invalid assertion credential JSON");
        } catch (AssertionFailedException e) {
            throw ApiException.unprocessable("WEBAUTHN_FAILED", e.getMessage());
        }
        if (!result.isSuccess()) {
            throw ApiException.unprocessable("WEBAUTHN_FAILED", "Assertion was not successful");
        }
        UserAccount account = userStore.getOrCreate(username);
        synchronized (account) {
            boolean pendingOk = account.pending() != null
                    && account.pending().deviceId().equals(deviceId)
                    && account.pending().credentialId().getBase64Url().equals(ceremony.pendingCredentialId());
            if (!pendingOk) {
                throw ApiException.unprocessable("NO_PENDING_ENROLL", "Pending credential missing or changed");
            }
            boolean signedByActive = account.active() != null
                    && account.active().credentialId().equals(result.getCredentialId());
            if (!signedByActive) {
                throw ApiException.unprocessable("WEBAUTHN_FAILED", "Assertion was not signed by the active device");
            }
            account.swapPendingToActive(deviceId);
        }
        return me(username, deviceId);
    }

    private String unwrapPublicKey(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        if (node.has("publicKey")) {
            return objectMapper.writeValueAsString(node.get("publicKey"));
        }
        return json;
    }

    private static void requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 64) {
            throw ApiException.badRequest("deviceId is required");
        }
        try {
            UUID.fromString(deviceId);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("deviceId must be a UUID");
        }
    }
}
