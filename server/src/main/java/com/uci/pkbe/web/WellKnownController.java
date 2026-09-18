package com.uci.pkbe.web;

import com.uci.pkbe.config.PkbeProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownController {

    private final PkbeProperties properties;

    public WellKnownController(PkbeProperties properties) {
        this.properties = properties;
    }

    @GetMapping(path = {"/.well-known/apple-app-site-association", "/apple-app-site-association"})
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        Map<String, Object> body = Map.of("webcredentials", Map.of("apps", properties.resolvedAasaApps()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    /**
     * Digital Asset Links for Android (phase 2). Returns an empty array until package + fingerprints
     * are configured via env.
     */
    @GetMapping(path = "/.well-known/assetlinks.json")
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (properties.assetLinksConfigured()) {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("namespace", "android_app");
            target.put("package_name", properties.getAndroidPackageName());
            target.put("sha256_cert_fingerprints", properties.resolvedAndroidSha256Fingerprints());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(
                    "relation",
                    List.of(
                            "delegate_permission/common.handle_all_urls",
                            "delegate_permission/common.get_login_creds"));
            entry.put("target", target);
            list.add(entry);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .body(list);
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** Unauthenticated bootstrap for clients / ops: what RP this instance thinks it is. */
    @GetMapping(path = "/v1/public-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> publicConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rpId", properties.resolvedRpId());
        body.put("rpName", properties.getRpName());
        body.put("publicBaseUrl", properties.resolvedPublicBaseUrl());
        body.put("origins", properties.resolvedOrigins());
        body.put("iosBundleId", properties.getIosBundleId());
        body.put("aasaApps", properties.resolvedAasaApps());
        body.put("androidPackageName", properties.getAndroidPackageName());
        body.put("assetLinksConfigured", properties.assetLinksConfigured());
        body.put("requireDeviceBound", properties.isRequireDeviceBound());
        body.put("challengeTtlSeconds", properties.getChallengeTtlSeconds());
        return ResponseEntity.ok(body);
    }
}
