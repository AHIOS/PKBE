package com.uci.pkbe.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stub login. Username is normalized to lowercase.")
public record LoginRequest(
        @Schema(example = "alice", description = "1-64 chars matching [a-z0-9._-]+") String username) {}

