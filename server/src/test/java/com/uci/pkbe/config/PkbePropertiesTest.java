package com.uci.pkbe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkbePropertiesTest {

    @Test
    void derivesRpIdAndOriginsFromPublicBaseUrl() {
        PkbeProperties properties = new PkbeProperties();
        properties.setPublicBaseUrl("https://pkbe-demo.onrender.com/");
        properties.normalize();

        assertThat(properties.resolvedRpId()).isEqualTo("pkbe-demo.onrender.com");
        assertThat(properties.resolvedOrigins()).contains("https://pkbe-demo.onrender.com");
        assertThat(properties.resolvedPublicBaseUrl()).isEqualTo("https://pkbe-demo.onrender.com");
        assertThat(properties.resolvedAasaApps()).containsExactly("TEAMID.com.uci.pkbe");
    }

    @Test
    void aasaAppsOverrideAndAssetLinks() {
        PkbeProperties properties = new PkbeProperties();
        properties.setPublicBaseUrl("https://tunnel.example");
        properties.setAasaApps("ABCD.com.uci.pkbe,ABCD.com.uci.pkbe.dev");
        properties.setAndroidPackageName("com.uci.pkbe");
        properties.setAndroidSha256Fingerprints("AA:BB:CC,ddeeff");
        properties.normalize();

        assertThat(properties.resolvedAasaApps()).containsExactly("ABCD.com.uci.pkbe", "ABCD.com.uci.pkbe.dev");
        assertThat(properties.assetLinksConfigured()).isTrue();
        assertThat(properties.resolvedAndroidSha256Fingerprints()).containsExactly("aabbcc", "ddeeff");
    }

    @Test
    void localDefaultsWhenNothingSet() {
        PkbeProperties properties = new PkbeProperties();
        properties.normalize();
        assertThat(properties.resolvedRpId()).isEqualTo("localhost");
        assertThat(properties.resolvedOrigins()).contains("http://localhost:8080");
    }
}
