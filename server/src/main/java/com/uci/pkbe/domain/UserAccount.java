package com.uci.pkbe.domain;

import com.yubico.webauthn.data.ByteArray;
import java.security.SecureRandom;

public final class UserAccount {

    private final String username;
    private final ByteArray userHandle;
    private DeviceCredential active;
    private DeviceCredential pending;

    public UserAccount(String username, ByteArray userHandle) {
        this.username = username;
        this.userHandle = userHandle;
    }

    public static UserAccount create(String username) {
        byte[] handle = new byte[32];
        new SecureRandom().nextBytes(handle);
        return new UserAccount(username, new ByteArray(handle));
    }

    public String username() {
        return username;
    }

    public ByteArray userHandle() {
        return userHandle;
    }

    public DeviceCredential active() {
        return active;
    }

    public DeviceCredential pending() {
        return pending;
    }

    public DeviceStatus statusFor(String deviceId) {
        if (active != null && active.deviceId().equals(deviceId)) {
            return DeviceStatus.ACTIVE;
        }
        if (pending != null && pending.deviceId().equals(deviceId)) {
            return DeviceStatus.PENDING;
        }
        return DeviceStatus.NONE;
    }

    public void setActive(DeviceCredential credential) {
        this.active = credential;
    }

    public void setPending(DeviceCredential credential) {
        this.pending = credential;
    }

    public DeviceCredential swapPendingToActive(String deviceId) {
        if (pending == null || !pending.deviceId().equals(deviceId)) {
            return null;
        }
        this.active = pending.withStatus(CredentialStatus.ACTIVE);
        this.pending = null;
        return this.active;
    }
}
