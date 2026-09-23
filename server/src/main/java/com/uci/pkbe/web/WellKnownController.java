package com.uci.pkbe.web;

import com.uci.pkbe.config.PkbeProperties;
import com.uci.pkbe.web.dto.HealthResponse;
import com.uci.pkbe.web.dto.PublicConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirements
@Tag(name = "Well-known", description = "Unauthenticated RP bootstrap, health, AASA, and Digital Asset Links")
public class WellKnownController {

    private final PkbeProperties properties;

    public WellKnownController(PkbeProperties properties) {
        this.properties = properties;
    }

    @Operation(summary = "Apple App Site Association")
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
     * Digital Asset Links for Android. Package + SHA-256 default in application.yml; override with
     * {@code PKBE_ANDROID_PACKAGE_NAME} / {@code PKBE_ANDROID_SHA256_FINGERPRINTS}.
     */
    @Operation(summary = "Android Digital Asset Links")
    @GetMapping(path = "/.well-known/assetlinks.json")
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (properties.assetLinksConfigured()) {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("namespace", "android_app");
            target.put("package_name", properties.getAndroidPackageName());
            target.put("sha256_cert_fingerprints", properties.assetLinksSha256Fingerprints());

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

    @Operation(summary = "Health")
    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @Operation(summary = "Public RP config")
    @GetMapping(path = "/v1/public-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicConfigResponse publicConfig() {
        return new PublicConfigResponse(
                properties.resolvedRpId(),
                properties.getRpName(),
                properties.resolvedPublicBaseUrl(),
                properties.resolvedOrigins(),
                properties.getIosBundleId(),
                properties.resolvedAasaApps(),
                properties.getAndroidPackageName(),
                properties.assetLinksConfigured(),
                properties.isRequireDeviceBound(),
                properties.getChallengeTtlSeconds());
    }
}
