package com.uci.pkbe.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resolved RP identity for clients and ops. Unauthenticated.")
public record PublicConfigResponse(
        String rpId,
        String rpName,
        String publicBaseUrl,
        List<String> origins,
        String iosBundleId,
        List<String> aasaApps,
        String androidPackageName,
        boolean assetLinksConfigured,
        boolean requireDeviceBound,
        long challengeTtlSeconds) {}
