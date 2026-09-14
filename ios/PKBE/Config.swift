import Foundation

enum Config {
    /// Host-only RP ID. Must match server `pkbe.rp-id` / Associated Domains `webcredentials:`.
    static var rpId: String {
        string(for: "PKBERpId", fallback: "localhost")
    }

    /// API base URL including scheme (Render or tunnel HTTPS, or local HTTP).
    static var baseURL: URL {
        URL(string: string(for: "PKBEBaseURL", fallback: "http://localhost:8080"))!
    }

    private static func string(for key: String, fallback: String) -> String {
        if let value = Bundle.main.object(forInfoDictionaryKey: key) as? String {
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, !trimmed.hasPrefix("$(") {
                return trimmed
            }
        }
        return fallback
    }
}
