import AuthenticationServices
import Foundation
import UIKit

@MainActor
final class PasskeyService: NSObject {
    func createPasskey(options: CreationOptions) async throws -> [String: Any] {
        let challenge = Base64URL.decode(options.challenge)
        let userID = Base64URL.decode(options.user.id)
        let rpId = options.rp.id ?? Config.rpId
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge,
            name: options.user.name,
            userID: userID
        )
        request.userVerificationPreference = .required
        let credential = try await perform(request)
        guard let registration = credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration else {
            throw APIError(error: "WEBAUTHN_FAILED", message: "Unexpected registration credential type")
        }
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
    }

    func assertHandover(options: AssertionOptions) async throws -> [String: Any] {
        let challenge = Base64URL.decode(options.challenge)
        let rpId = options.rpId ?? Config.rpId
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

        let credential = try await perform(platformRequest, securityRequest)
        if let assertion = credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion {
            return assertionDictionary(id: assertion.credentialID, assertion: assertion)
        }
        if let assertion = credential as? ASAuthorizationSecurityKeyPublicKeyCredentialAssertion {
            return assertionDictionary(id: assertion.credentialID, assertion: assertion)
        }
        throw APIError(error: "WEBAUTHN_FAILED", message: "Unexpected assertion credential type")
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

    private func perform(_ requests: ASAuthorizationRequest...) async throws -> ASAuthorizationCredential {
        try await withCheckedThrowingContinuation { continuation in
            let controller = ASAuthorizationController(authorizationRequests: requests)
            let delegate = AuthDelegate(controller: controller, continuation: continuation)
            objc_setAssociatedObject(controller, "pkbe.delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            controller.performRequests()
        }
    }
}

private final class AuthDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let continuation: CheckedContinuation<ASAuthorizationCredential, Error>
    private var resumed = false
    private let controller: ASAuthorizationController

    init(controller: ASAuthorizationController, continuation: CheckedContinuation<ASAuthorizationCredential, Error>) {
        self.controller = controller
        self.continuation = continuation
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        resume(.success(authorization.credential))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        resume(.failure(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    private func resume(_ result: Result<ASAuthorizationCredential, Error>) {
        guard !resumed else { return }
        resumed = true
        objc_setAssociatedObject(controller, "pkbe.delegate", nil, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        continuation.resume(with: result)
    }
}
