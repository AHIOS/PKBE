package com.uci.pkbe.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceRequest(
        @Schema(example = "11111111-1111-1111-1111-111111111111", description = "Client-generated device UUID")
                String deviceId) {}

