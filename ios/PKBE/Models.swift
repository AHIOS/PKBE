import Foundation

struct LoginResponse: Decodable {
    let token: String
    let username: String
}

struct MeResponse: Decodable {
    let username: String
    let thisDeviceId: String
    let thisDeviceStatus: String
    let activeDeviceId: String?
    let pendingDeviceId: String?
    let thisDeviceCredentialId: String?
    let activeCredential: CredentialView?

    struct CredentialView: Decodable {
        let credentialIdPrefix: String
        let aaguid: String
        let backupEligible: Bool
        let backupState: Bool
    }
}

struct APIError: Decodable, Error, LocalizedError {
    let error: String
    let message: String
    var errorDescription: String? { "\(error): \(message)" }
}

struct CreationOptions: Decodable {
    let rp: RelyingParty
    let user: User
    let challenge: String
    let timeout: Double?
    let authenticatorSelection: AuthenticatorSelection?
    let hints: [String]?

    struct RelyingParty: Decodable {
        let id: String?
        let name: String
    }

    struct User: Decodable {
        let id: String
        let name: String
        let displayName: String
    }

    struct AuthenticatorSelection: Decodable {
        let userVerification: String?
        let authenticatorAttachment: String?
    }
}

struct AssertionOptions: Decodable {
    let challenge: String
    let rpId: String?
    let timeout: Double?
    let userVerification: String?
    let allowCredentials: [AllowedCredential]?
    let hints: [String]?

    struct AllowedCredential: Decodable {
        let type: String
        let id: String
        let transports: [String]?
    }
}
