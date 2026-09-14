package com.uci.pkbe.config;

import com.uci.pkbe.store.UserStore;
import com.uci.pkbe.webauthn.ActiveOnlyCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PkbeProperties.class)
public class WebAuthnConfig {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnConfig.class);

    @Bean
    ActiveOnlyCredentialRepository credentialRepository(UserStore userStore) {
        return new ActiveOnlyCredentialRepository(userStore);
    }

    @Bean
    RelyingParty relyingParty(PkbeProperties properties, ActiveOnlyCredentialRepository credentialRepository) {
        properties.normalize();
        log.info(
                "PKBE RP id={} name={} publicBaseUrl={} origins={} aasaApps={} assetLinks={}",
                properties.resolvedRpId(),
                properties.getRpName(),
                properties.resolvedPublicBaseUrl(),
                properties.resolvedOrigins(),
                properties.resolvedAasaApps(),
                properties.assetLinksConfigured());
        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(properties.resolvedRpId())
                .name(properties.getRpName())
                .build();
        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialRepository)
                .origins(new HashSet<>(properties.resolvedOrigins()))
                .allowOriginPort(true)
                .build();
    }
}
