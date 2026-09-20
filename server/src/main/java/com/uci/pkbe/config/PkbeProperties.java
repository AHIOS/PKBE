package com.uci.pkbe.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All deployment knobs for Render, tunnels, and local runs.
 *
 * <p>Preferred env vars (Render / shell):
 *
 * <ul>
 *   <li>{@code PKBE_PUBLIC_BASE_URL} – e.g. {@code https://pkbe.onrender.com} or tunnel URL
 *   <li>{@code PKBE_RP_ID} – hostname only; defaults from public base URL host
 *   <li>{@code PKBE_ORIGINS} – comma-separated; defaults to public base URL
 *   <li>{@code PKBE_APPLE_TEAM_ID}, {@code PKBE_IOS_BUNDLE_ID}
 *   <li>{@code PKBE_AASA_APPS} – optional full override {@code TEAMID.bundle[,...]}
 *   <li>{@code PKBE_ANDROID_PACKAGE_NAME}, {@code PKBE_ANDROID_SHA256_FINGERPRINTS}
 *   <li>{@code PORT} – Render sets this; mapped to {@code server.port}
 * </ul>
 */
@ConfigurationProperties(prefix = "pkbe")
public class PkbeProperties {

    /** Public HTTPS base URL of this RP (Render or tunnel). Used to derive rp-id and origins. */
    private String publicBaseUrl = "";

    private String rpId = "";

    private String rpName = "PKBE";

    /** Comma-separated allowed WebAuthn origins, or empty to derive from public-base-url. */
    private String origins = "";

    private String appleTeamId = "TEAMID";

    private String iosBundleId = "com.uci.pkbe";

    /**
     * Optional comma-separated AASA app IDs ({@code TEAMID.bundle}). When empty, built from
     * apple-team-id + ios-bundle-id.
     */
    private String aasaApps = "";

    private String androidPackageName = "";

    /** Comma-separated cert SHA-256 fingerprints for Digital Asset Links (phase 2). */
    private String androidSha256Fingerprints = "";

    private boolean requireDeviceBound = false;

    private long challengeTtlSeconds = 120;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = blankToEmpty(publicBaseUrl);
    }

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = blankToEmpty(rpId);
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName == null || rpName.isBlank() ? "PKBE" : rpName.trim();
    }

    public String getOrigins() {
        return origins;
    }

    public void setOrigins(String origins) {
        this.origins = blankToEmpty(origins);
    }

    public String getAppleTeamId() {
        return appleTeamId;
    }

    public void setAppleTeamId(String appleTeamId) {
        this.appleTeamId = blankToEmpty(appleTeamId).isEmpty() ? "TEAMID" : appleTeamId.trim();
    }

    public String getIosBundleId() {
        return iosBundleId;
    }

    public void setIosBundleId(String iosBundleId) {
        this.iosBundleId =
                blankToEmpty(iosBundleId).isEmpty() ? "com.uci.pkbe" : iosBundleId.trim();
    }

    public String getAasaApps() {
        return aasaApps;
    }

    public void setAasaApps(String aasaApps) {
        this.aasaApps = blankToEmpty(aasaApps);
    }

    public String getAndroidPackageName() {
        return androidPackageName;
    }

    public void setAndroidPackageName(String androidPackageName) {
        this.androidPackageName = blankToEmpty(androidPackageName);
    }

    public String getAndroidSha256Fingerprints() {
        return androidSha256Fingerprints;
    }

    public void setAndroidSha256Fingerprints(String androidSha256Fingerprints) {
        this.androidSha256Fingerprints = blankToEmpty(androidSha256Fingerprints);
    }

    public boolean isRequireDeviceBound() {
        return requireDeviceBound;
    }

    public void setRequireDeviceBound(boolean requireDeviceBound) {
        this.requireDeviceBound = requireDeviceBound;
    }

    public long getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(long challengeTtlSeconds) {
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    /** Resolved RP ID (hostname). Never blank after {@link #normalize()}. */
    public String resolvedRpId() {
        if (!rpId.isBlank()) {
            return stripHost(rpId);
        }
        String fromUrl = hostFrom(publicBaseUrl);
        if (!fromUrl.isBlank()) {
            return fromUrl;
        }
        return "localhost";
    }

    /** Resolved WebAuthn origins; always at least one entry after {@link #normalize()}. */
    public List<String> resolvedOrigins() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : splitCsv(origins)) {
            set.add(trimTrailingSlash(part));
        }
        if (set.isEmpty() && !publicBaseUrl.isBlank()) {
            set.add(trimTrailingSlash(publicBaseUrl));
        }
        if (set.isEmpty()) {
            set.add("http://localhost:8080");
            set.add("https://localhost");
        }
        String rp = resolvedRpId();
        if (!"localhost".equals(rp)) {
            set.add("https://" + rp);
        }
        return List.copyOf(set);
    }

    public List<String> resolvedAasaApps() {
        List<String> fromEnv = splitCsv(aasaApps);
        if (!fromEnv.isEmpty()) {
            return fromEnv;
        }
        return List.of(appleTeamId + "." + iosBundleId);
    }

    public List<String> resolvedAndroidSha256Fingerprints() {
        return splitCsv(androidSha256Fingerprints).stream()
                .map(fp -> fp.replace(":", "").toLowerCase(Locale.ROOT))
                .toList();
    }

    /** Digital Asset Links require colon-separated uppercase SHA-256. */
    public List<String> assetLinksSha256Fingerprints() {
        return resolvedAndroidSha256Fingerprints().stream().map(PkbeProperties::colonHex).toList();
    }

    static String colonHex(String hex) {
        String compact = hex.replace(":", "").toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < compact.length(); i += 2) {
            if (out.length() > 0) {
                out.append(':');
            }
            out.append(compact, i, i + 2);
        }
        return out.toString();
    }

    public boolean assetLinksConfigured() {
        return !androidPackageName.isBlank() && !resolvedAndroidSha256Fingerprints().isEmpty();
    }

    public String resolvedPublicBaseUrl() {
        if (!publicBaseUrl.isBlank()) {
            return trimTrailingSlash(publicBaseUrl);
        }
        String rp = resolvedRpId();
        if ("localhost".equals(rp)) {
            return "http://localhost:8080";
        }
        return "https://" + rp;
    }

    /**
     * Fill blanks from {@code public-base-url} and validate. Call once at startup before building
     * the RelyingParty bean.
     */
    public void normalize() {
        if (rpId.isBlank()) {
            String host = hostFrom(publicBaseUrl);
            if (!host.isBlank()) {
                rpId = host;
            }
        } else {
            rpId = stripHost(rpId);
        }
        if (origins.isBlank() && !publicBaseUrl.isBlank()) {
            origins = trimTrailingSlash(publicBaseUrl);
        }
        if (rpId.isBlank()) {
            rpId = "localhost";
        }
        if (challengeTtlSeconds < 30 || challengeTtlSeconds > 600) {
            throw new IllegalStateException("pkbe.challenge-ttl-seconds must be between 30 and 600");
        }
        if (resolvedOrigins().isEmpty()) {
            throw new IllegalStateException("pkbe.origins resolved to empty");
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String hostFrom(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() != null) {
                return uri.getHost().toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return stripHost(url);
    }

    private static String stripHost(String value) {
        String v = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("https://")) {
            v = v.substring("https://".length());
        } else if (v.startsWith("http://")) {
            v = v.substring("http://".length());
        }
        int slash = v.indexOf('/');
        if (slash >= 0) {
            v = v.substring(0, slash);
        }
        int colon = v.indexOf(':');
        if (colon >= 0) {
            v = v.substring(0, colon);
        }
        return v;
    }
}
