package com.uci.pkbe.web;

import com.uci.pkbe.service.ActivationService;
import com.uci.pkbe.store.SessionStore;
import com.uci.pkbe.web.dto.CredentialVerifyRequest;
import com.uci.pkbe.web.dto.DeviceRequest;
import com.uci.pkbe.web.dto.ErrorResponse;
import com.uci.pkbe.web.dto.LoginRequest;
import com.uci.pkbe.web.dto.LoginResponse;
import com.uci.pkbe.web.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Activation", description = "Stub login, passkey enroll, and proximity handover")
@ApiResponses({
    @ApiResponse(
            responseCode = "400",
            description = "BAD_REQUEST",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
            responseCode = "401",
            description = "UNAUTHORIZED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
            responseCode = "409",
            description = "ALREADY_ACTIVE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
            responseCode = "422",
            description =
                    "NO_ACTIVE_DEVICE | NO_PENDING_ENROLL | CHALLENGE_EXPIRED | WEBAUTHN_FAILED | DEVICE_BOUND_REQUIRED | NO_ENROLLMENT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ActivationController {

    private final SessionStore sessionStore;
    private final ActivationService activationService;

    public ActivationController(SessionStore sessionStore, ActivationService activationService) {
        this.sessionStore = sessionStore;
        this.activationService = activationService;
    }

    @SecurityRequirements
    @Operation(summary = "Stub login", description = "Returns a bearer token. No password. Real IdP is out of scope.")
    @ApiResponse(responseCode = "200", description = "Session created")
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

    @Operation(summary = "Logout", description = "Invalidates the current bearer token.")
    @ApiResponse(responseCode = "204", description = "Session cleared")
    @PostMapping("/v1/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute(BearerAuthFilter.TOKEN_ATTR);
        sessionStore.invalidate(token);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current device status")
    @ApiResponse(responseCode = "200", description = "Registry view for this device")
    @GetMapping("/v1/me")
    public MeResponse me(
            HttpServletRequest request,
            @Parameter(required = true, description = "Client-generated device UUID")
                    @RequestHeader(name = "X-Device-Id", required = false)
                    String deviceId) {
        return activationService.me(username(request), requireDevice(deviceId));
    }

    @Operation(
            summary = "Unenroll this device",
            description = "Clears ACTIVE and/or PENDING enrollment for this deviceId only.")
    @ApiResponse(responseCode = "200", description = "Updated status")
    @PostMapping("/v1/unenroll")
    public MeResponse unenroll(HttpServletRequest request, @RequestBody DeviceRequest body) {
        return activationService.unenroll(username(request), body.deviceId());
    }

    @Operation(
            summary = "WebAuthn create options",
            description = "Yubico PublicKeyCredentialCreationOptions JSON (base64url binaries).")
    @ApiResponse(
            responseCode = "200",
            description = "Creation options",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object")))
    @PostMapping(path = "/v1/register/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> registerOptions(HttpServletRequest request, @RequestBody DeviceRequest body) {
        String json = activationService.startRegistration(username(request), body.deviceId(), token(request));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @Operation(summary = "Finish passkey registration", description = "First device becomes ACTIVE; later devices PENDING.")
    @ApiResponse(responseCode = "200", description = "Updated status")
    @PostMapping("/v1/register/verify")
    public MeResponse registerVerify(HttpServletRequest request, @RequestBody CredentialVerifyRequest body) {
        if (body.credential() == null) {
            throw ApiException.badRequest("credential is required");
        }
        return activationService.finishRegistration(
                username(request), body.deviceId(), token(request), body.credential().toString());
    }

    @Operation(
            summary = "Start proximity handover",
            description =
                    "Yubico assertion request JSON (`hints: hybrid`). Device B shows the system FIDO QR; A scans and signs.")
    @ApiResponse(
            responseCode = "200",
            description = "Assertion options",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object")))
    @PostMapping(path = "/v1/handover/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handoverOptions(HttpServletRequest request, @RequestBody DeviceRequest body) {
        String json = activationService.startHandover(username(request), body.deviceId(), token(request));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @Operation(
            summary = "Finish handover",
            description = "Device B posts A's assertion. PENDING(B) becomes ACTIVE; A's credential is revoked.")
    @ApiResponse(responseCode = "200", description = "Updated status")
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
