package com.uci.pkbe.web;

import com.uci.pkbe.service.ActivationService;
import com.uci.pkbe.store.SessionStore;
import com.uci.pkbe.web.dto.CredentialVerifyRequest;
import com.uci.pkbe.web.dto.DeviceRequest;
import com.uci.pkbe.web.dto.LoginRequest;
import com.uci.pkbe.web.dto.LoginResponse;
import com.uci.pkbe.web.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivationController {

    private final SessionStore sessionStore;
    private final ActivationService activationService;

    public ActivationController(SessionStore sessionStore, ActivationService activationService) {
        this.sessionStore = sessionStore;
        this.activationService = activationService;
    }

    @PostMapping("/v1/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw ApiException.badRequest("username is required");
        }
        String username = request.username().trim().toLowerCase();
        if (username.length() > 64 || !username.matches("[a-z0-9._-]+")) {
            throw ApiException.badRequest("username must be 1-64 chars [a-z0-9._-]");
        }
        String token = sessionStore.create(username);
        return new LoginResponse(token, username);
    }

    @PostMapping("/v1/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute(BearerAuthFilter.TOKEN_ATTR);
        sessionStore.invalidate(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/me")
    public MeResponse me(
            HttpServletRequest request, @RequestHeader(name = "X-Device-Id", required = false) String deviceId) {
        return activationService.me(username(request), requireDevice(deviceId));
    }

    @PostMapping("/v1/unenroll")
    public MeResponse unenroll(HttpServletRequest request, @RequestBody DeviceRequest body) {
        return activationService.unenroll(username(request), body.deviceId());
    }

    @PostMapping(path = "/v1/register/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> registerOptions(HttpServletRequest request, @RequestBody DeviceRequest body) {
        String json = activationService.startRegistration(username(request), body.deviceId(), token(request));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @PostMapping("/v1/register/verify")
    public MeResponse registerVerify(HttpServletRequest request, @RequestBody CredentialVerifyRequest body) {
        if (body.credential() == null) {
            throw ApiException.badRequest("credential is required");
        }
        return activationService.finishRegistration(
                username(request), body.deviceId(), token(request), body.credential().toString());
    }

    @PostMapping(path = "/v1/handover/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handoverOptions(HttpServletRequest request, @RequestBody DeviceRequest body) {
        String json = activationService.startHandover(username(request), body.deviceId(), token(request));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @PostMapping("/v1/handover/verify")
    public MeResponse handoverVerify(HttpServletRequest request, @RequestBody CredentialVerifyRequest body) {
        if (body.credential() == null) {
            throw ApiException.badRequest("credential is required");
        }
        return activationService.finishHandover(
                username(request), body.deviceId(), token(request), body.credential().toString());
    }

    private static String username(HttpServletRequest request) {
        return (String) request.getAttribute(BearerAuthFilter.USERNAME_ATTR);
    }

    private static String token(HttpServletRequest request) {
        return (String) request.getAttribute(BearerAuthFilter.TOKEN_ATTR);
    }

    private static String requireDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw ApiException.badRequest("X-Device-Id header is required");
        }
        return deviceId;
    }
}
