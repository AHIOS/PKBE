package com.uci.pkbe.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Liveness probe")
public record HealthResponse(@Schema(example = "ok") String status) {}
