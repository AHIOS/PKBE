package com.uci.pkbe.store;

import com.uci.pkbe.domain.UserAccount;
import com.yubico.webauthn.data.ByteArray;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class UserStore {

    private final ConcurrentHashMap<String, UserAccount> byUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ByteArray, String> usernameByHandle = new ConcurrentHashMap<>();

    public UserAccount getOrCreate(String username) {
        return byUsername.computeIfAbsent(username, name -> {
            UserAccount created = UserAccount.create(name);
            usernameByHandle.put(created.userHandle(), name);
            return created;
        });
    }

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    public Optional<String> findUsernameByHandle(ByteArray userHandle) {
        return Optional.ofNullable(usernameByHandle.get(userHandle));
    }

    public Optional<UserAccount> findByActiveCredentialId(ByteArray credentialId) {
        return byUsername.values().stream()
                .filter(account -> account.active() != null && account.active().credentialId().equals(credentialId))
                .findFirst();
    }

    public void clear() {
        byUsername.clear();
        usernameByHandle.clear();
    }
}
