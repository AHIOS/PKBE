import AuthenticationServices
import Foundation
import UIKit

@MainActor
final class PasskeyService: NSObject {
    func createPasskey(options: CreationOptions) async throws -> [String: Any] {
        let challenge = Base64URL.decode(options.challenge)
        let userID = Base64URL.decode(options.user.id)
        let rpId = options.rp.id ?? Config.rpId

        PasskeyLog.info("createPasskey start rpId=\(rpId) challengeBytes=\(challenge.count) userBytes=\(userID.count) userName=\(options.user.name)")
        let report = await PasskeyDiagnostics.runPreflight(expectedRpId: rpId)
        PasskeyLog.info("createPasskey preflight done\n\(report)")

        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge,
            name: options.user.name,
            userID: userID
        )
        request.userVerificationPreference = .required

        do {
            let credential = try await perform(request, operation: "registration", rpId: rpId)
            guard let registration = credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration else {
                throw APIError(error: "WEBAUTHN_FAILED", message: "Unexpected registration credential type")
            }
            PasskeyLog.info("createPasskey success credentialIdPrefix=\(Base64URL.encode(registration.credentialID).prefix(12))")
            return [
                "id": Base64URL.encode(registration.credentialID),
                "rawId": Base64URL.encode(registration.credentialID),
                "type": "public-key",
                "response": [
                    "clientDataJSON": Base64URL.encode(registration.rawClientDataJSON),
                    "attestationObject": Base64URL.encode(registration.rawAttestationObject ?? Data())
                ],
                "clientExtensionResults": [:] as [String: Any]
            ]
        } catch {
            let detail = PasskeyDiagnostics.describeAuthorizationError(error)
            PasskeyLog.error("createPasskey failed: \(detail)")
            throw APIError(
                error: "WEBAUTHN_FAILED",
                message: "Registration failed: \(detail)\n\nPreflight:\n\(report)"
            )
        }
    }

    func assertHandover(options: AssertionOptions) async throws -> [String: Any] {
        let challenge = Base64URL.decode(options.challenge)
        let rpId = options.rpId ?? Config.rpId
        let allowCount = options.allowCredentials?.count ?? 0

        PasskeyLog.info("assertHandover start rpId=\(rpId) challengeBytes=\(challenge.count) allowCredentials=\(allowCount) hints=\(options.hints ?? [])")
        let report = await PasskeyDiagnostics.runPreflight(expectedRpId: rpId)

        let allowed = (options.allowCredentials ?? []).map {
            ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: Base64URL.decode($0.id))
        }

        let platformProvider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let platformRequest = platformProvider.createCredentialAssertionRequest(challenge: challenge)
        platformRequest.userVerificationPreference = .required
        platformRequest.allowedCredentials = allowed

        let securityProvider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let securityRequest = securityProvider.createCredentialAssertionRequest(challenge: challenge)
        securityRequest.userVerificationPreference = .required
        securityRequest.allowedCredentials = (options.allowCredentials ?? []).map {
            ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(
                credentialID: Base64URL.decode($0.id),
                transports: [.bluetooth, .usb, .nfc]
            )
        }

        do {
            let credential = try await perform(platformRequest, securityRequest, operation: "assertion", rpId: rpId)
            if let assertion = credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion {
                PasskeyLog.info("assertHandover success (platform)")
                return assertionDictionary(id: assertion.credentialID, assertion: assertion)
            }
            if let assertion = credential as? ASAuthorizationSecurityKeyPublicKeyCredentialAssertion {
                PasskeyLog.info("assertHandover success (security-key/hybrid)")
                return assertionDictionary(id: assertion.credentialID, assertion: assertion)
            }
            throw APIError(error: "WEBAUTHN_FAILED", message: "Unexpected assertion credential type")
        } catch {
            let detail = PasskeyDiagnostics.describeAuthorizationError(error)
            PasskeyLog.error("assertHandover failed: \(detail)")
            throw APIError(
                error: "WEBAUTHN_FAILED",
                message: "Assertion failed: \(detail)\n\nPreflight:\n\(report)"
            )
        }
    }

    /// Whether Passwords still has this credential for our RP ID (local / iCloud, not hybrid).
    /// Deleted passkeys fail quickly with `preferImmediatelyAvailableCredentials` and no UI.
    /// If the key still exists, the system may prompt Face ID once.
    func localPasskeyPresence(credentialIdBase64Url: String) async -> LocalPasskeyPresence {
        let credentialId = Base64URL.decode(credentialIdBase64Url)
        guard !credentialId.isEmpty else {
            PasskeyLog.error("localPasskeyPresence: credential id failed to decode")
            return .absent
        }
        let challenge = Data((0..<32).map { _ in UInt8.random(in: 0...255) })
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: Config.rpId)
        let request = provider.createCredentialAssertionRequest(challenge: challenge)
        request.userVerificationPreference = .discouraged
        request.allowedCredentials = [
            ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: credentialId)
        ]
        PasskeyLog.info("localPasskeyPresence probe credentialPrefix=\(credentialIdBase64Url.prefix(12)) bytes=\(credentialId.count)")
        do {
            let credential = try await perform(
                request,
                operation: "local-presence",
                rpId: Config.rpId,
                preferImmediatelyAvailable: true
            )
            if let assertion = credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion {
                let matched = assertion.credentialID == credentialId
                PasskeyLog.info("localPasskeyPresence => present matchedId=\(matched)")
                return matched ? .present : .absent
            }
            PasskeyLog.info("localPasskeyPresence => unexpected credential type, treating absent")
            return .absent
        } catch {
            let detail = PasskeyDiagnostics.describeAuthorizationError(error)
            PasskeyLog.info("localPasskeyPresence => absent/failed: \(detail)")
            // preferImmediatelyAvailable: missing local key fails with canceled/failed and no QR sheet.
            // Canceling Face ID while the key still exists also lands here — POC treats that as absent.
            return .absent
        }
    }

    private func assertionDictionary(
        id: Data,
        assertion: ASAuthorizationPublicKeyCredentialAssertion
    ) -> [String: Any] {
        var response: [String: Any] = [
            "clientDataJSON": Base64URL.encode(assertion.rawClientDataJSON),
            "authenticatorData": Base64URL.encode(assertion.rawAuthenticatorData),
            "signature": Base64URL.encode(assertion.signature)
        ]
        if let userHandle = assertion.userID {
            response["userHandle"] = Base64URL.encode(userHandle)
        }
        return [
            "id": Base64URL.encode(id),
            "rawId": Base64URL.encode(id),
            "type": "public-key",
            "response": response,
            "clientExtensionResults": [:] as [String: Any]
        ]
    }

    private func perform(
        _ requests: ASAuthorizationRequest...,
        operation: String,
        rpId: String,
        preferImmediatelyAvailable: Bool = false
    ) async throws -> ASAuthorizationCredential {
        PasskeyLog.info("ASAuthorizationController.performRequests op=\(operation) rpId=\(rpId) requestCount=\(requests.count) preferImmediate=\(preferImmediatelyAvailable)")
        return try await withCheckedThrowingContinuation { continuation in
            let controller = ASAuthorizationController(authorizationRequests: requests)
            let delegate = AuthDelegate(controller: controller, continuation: continuation, operation: operation)
            objc_setAssociatedObject(controller, "pkbe.delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            if preferImmediatelyAvailable {
                controller.performRequests(options: .preferImmediatelyAvailableCredentials)
            } else {
                controller.performRequests()
            }
        }
    }
}

private final class AuthDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let continuation: CheckedContinuation<ASAuthorizationCredential, Error>
    private var resumed = false
    private let controller: ASAuthorizationController
    private let operation: String

    init(
        controller: ASAuthorizationController,
        continuation: CheckedContinuation<ASAuthorizationCredential, Error>,
        operation: String
    ) {
        self.controller = controller
        self.continuation = continuation
        self.operation = operation
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        PasskeyLog.info("ASAuthorization success op=\(operation) type=\(type(of: authorization.credential))")
        resume(.success(authorization.credential))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        PasskeyLog.error("ASAuthorization failure op=\(operation): \(PasskeyDiagnostics.describeAuthorizationError(error))")
        resume(.failure(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let anchor = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        if anchor == nil {
            PasskeyLog.error("No key window for ASAuthorization presentation anchor")
        }
        return anchor ?? ASPresentationAnchor()
    }

    private func resume(_ result: Result<ASAuthorizationCredential, Error>) {
        guard !resumed else { return }
        resumed = true
        objc_setAssociatedObject(controller, "pkbe.delegate", nil, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        continuation.resume(with: result)
    }
}
