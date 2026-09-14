package com.uci.pkbe.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialVerifyRequest(String deviceId, JsonNode credential) {}
