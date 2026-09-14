package com.uci.pkbe.webauthn;

import com.uci.pkbe.domain.DeviceCredential;
import com.uci.pkbe.domain.UserAccount;
import com.uci.pkbe.store.UserStore;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import java.util.Optional;
import java.util.Set;

/**
 * Only the ACTIVE credential is eligible for assertion. Pending keys cannot satisfy handover.
 */
public class ActiveOnlyCredentialRepository implements CredentialRepository {

    private final UserStore userStore;

    public ActiveOnlyCredentialRepository(UserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userStore.findByUsername(username)
                .map(UserAccount::active)
                .map(this::descriptor)
                .map(Set::of)
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userStore.findByUsername(username).map(UserAccount::userHandle);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userStore.findUsernameByHandle(userHandle);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return userStore.findUsernameByHandle(userHandle)
                .flatMap(userStore::findByUsername)
                .filter(account -> account.active() != null && account.active().credentialId().equals(credentialId))
                .map(account -> registered(account, account.active()));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return userStore.findByActiveCredentialId(credentialId)
                .map(account -> registered(account, account.active()))
                .map(Set::of)
                .orElse(Set.of());
    }

    private PublicKeyCredentialDescriptor descriptor(DeviceCredential credential) {
        return PublicKeyCredentialDescriptor.builder()
                .id(credential.credentialId())
                .build();
    }

    private RegisteredCredential registered(UserAccount account, DeviceCredential credential) {
        return RegisteredCredential.builder()
                .credentialId(credential.credentialId())
                .userHandle(account.userHandle())
                .publicKeyCose(credential.publicKeyCose())
                .signatureCount(credential.signatureCount())
                .build();
    }
}
