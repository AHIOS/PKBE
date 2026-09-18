import AuthenticationServices
import Foundation
import OSLog

enum PasskeyLog {
    static let logger = Logger(subsystem: "com.uci.pkbe", category: "Passkey")

    static func info(_ message: String) {
        logger.info("\(message, privacy: .public)")
        print("[PKBE] \(message)")
    }

    static func error(_ message: String) {
        logger.error("\(message, privacy: .public)")
        print("[PKBE][ERROR] \(message)")
    }
}

struct PublicConfigDTO: Decodable {
    let rpId: String
    let rpName: String
    let publicBaseUrl: String
    let origins: [String]
    let iosBundleId: String
    let aasaApps: [String]
}

struct AASADocument: Decodable {
    let webcredentials: WebCredentials?

    struct WebCredentials: Decodable {
        let apps: [String]?
    }
}

@MainActor
enum PasskeyDiagnostics {
    /// Collects local + remote association facts and returns a human-readable report.
    static func runPreflight(expectedRpId: String) async -> String {
        var lines: [String] = []
        let bundleId = Bundle.main.bundleIdentifier ?? "(nil)"
        let teamPrefix = teamIdPrefix()
        let appId = teamPrefix.map { "\($0).\(bundleId)" } ?? "TEAMID.\(bundleId)"

        lines.append("=== PKBE passkey preflight ===")
        lines.append("bundleId=\(bundleId)")
        lines.append("appId(guess)=\(appId)")
        lines.append("Config.rpId=\(Config.rpId)")
        lines.append("Config.baseURL=\(Config.baseURL.absoluteString)")
        lines.append("requestRpId=\(expectedRpId)")
        lines.append("associatedDomains=\(associatedDomainEntries().joined(separator: ", "))")

        if Config.rpId != expectedRpId {
            lines.append("MISMATCH: Config.rpId != server/request rpId")
        }
        if let host = Config.baseURL.host, host != expectedRpId, expectedRpId != "localhost" {
            lines.append("WARN: baseURL host (\(host)) != rpId (\(expectedRpId))")
        }

        do {
            let publicConfig = try await fetchPublicConfig()
            lines.append("server.rpId=\(publicConfig.rpId)")
            lines.append("server.publicBaseUrl=\(publicConfig.publicBaseUrl)")
            lines.append("server.iosBundleId=\(publicConfig.iosBundleId)")
            lines.append("server.aasaApps=\(publicConfig.aasaApps.joined(separator: ", "))")
            lines.append("server.origins=\(publicConfig.origins.joined(separator: ", "))")

            if publicConfig.rpId != expectedRpId {
                lines.append("MISMATCH: server.rpId != request rpId")
            }
            if publicConfig.iosBundleId != bundleId {
                lines.append("MISMATCH: server.iosBundleId (\(publicConfig.iosBundleId)) != app bundleId (\(bundleId))")
            }
            if !publicConfig.aasaApps.contains(where: { $0.hasSuffix(".\(bundleId)") || $0 == appId }) {
                lines.append("MISMATCH: AASA apps list does not contain this app's TEAMID.bundleId")
            }
        } catch {
            lines.append("public-config fetch FAILED: \(error.localizedDescription)")
        }

        do {
            let aasa = try await fetchAASA(rpId: expectedRpId)
            let apps = aasa.webcredentials?.apps ?? []
            lines.append("aasa.url=https://\(expectedRpId)/.well-known/apple-app-site-association")
            lines.append("aasa.apps=\(apps.joined(separator: ", "))")
            if apps.isEmpty {
                lines.append("MISMATCH: AASA webcredentials.apps is empty")
            } else if !apps.contains(where: { $0.hasSuffix(".\(bundleId)") }) {
                lines.append("MISMATCH: AASA apps do not include *.\(bundleId)")
            }
        } catch {
            lines.append("AASA fetch FAILED: \(error.localizedDescription)")
            lines.append("HINT: Code 1004 often means Associated Domains / AASA failed to validate")
        }

        do {
            let cdn = try await fetchAppleCDN(rpId: expectedRpId)
            lines.append("appleCDN=\(cdn)")
        } catch {
            lines.append("appleCDN check FAILED: \(error.localizedDescription)")
        }

        lines.append("HINT: If Apple CDN shows Timeout/SWCERR00301, use Associated Domains webcredentials:<host>?mode=developer (Debug) and keep the Render service warm.")

        let report = lines.joined(separator: "\n")
        PasskeyLog.info(report)
        return report
    }

    nonisolated static func describeAuthorizationError(_ error: Error) -> String {
        let ns = error as NSError
        var parts = [
            "domain=\(ns.domain)",
            "code=\(ns.code)",
            "desc=\(ns.localizedDescription)"
        ]
        if let auth = error as? ASAuthorizationError {
            let name: String
            switch auth.code {
            case .canceled: name = "canceled(1001)"
            case .failed: name = "failed(1004) — often Associated Domains / RP ID / AASA"
            case .invalidResponse: name = "invalidResponse(1002)"
            case .notHandled: name = "notHandled(1003)"
            case .notInteractive: name = "notInteractive(1005)"
            case .unknown: name = "unknown(1000)"
            @unknown default: name = "other(\(auth.code.rawValue))"
            }
            parts.append("asAuthorization=\(name)")
        }
        if let reason = ns.userInfo[NSLocalizedFailureReasonErrorKey] {
            parts.append("reason=\(reason)")
        }
        if let underlying = ns.userInfo[NSUnderlyingErrorKey] as? NSError {
            parts.append("underlying=\(underlying.domain)/\(underlying.code) \(underlying.localizedDescription)")
        }
        for (key, value) in ns.userInfo {
            if key == NSLocalizedDescriptionKey || key == NSLocalizedFailureReasonErrorKey || key == NSUnderlyingErrorKey {
                continue
            }
            parts.append("userInfo.\(key)=\(value)")
        }
        return parts.joined(separator: " | ")
    }

    private static func fetchPublicConfig() async throws -> PublicConfigDTO {
        var url = Config.baseURL
        url.append(path: "/v1/public-config")
        let (data, response) = try await URLSession.shared.data(from: url)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else {
            throw APIError(error: "HTTP_\(status)", message: "public-config failed")
        }
        return try JSONDecoder().decode(PublicConfigDTO.self, from: data)
    }

    private static func fetchAASA(rpId: String) async throws -> AASADocument {
        // Prefer the API host; AASA must be on the RP ID host with HTTPS and no redirect.
        let url = URL(string: "https://\(rpId)/.well-known/apple-app-site-association")!
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError(error: "AASA", message: "No HTTP response")
        }
        PasskeyLog.info("AASA status=\(http.statusCode) content-type=\(http.value(forHTTPHeaderField: "Content-Type") ?? "?") bytes=\(data.count)")
        if let finalURL = http.url, finalURL.host != rpId {
            PasskeyLog.error("AASA redirected to \(finalURL.absoluteString) — Apple requires no redirects")
        }
        guard (200..<300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw APIError(error: "AASA_HTTP_\(http.statusCode)", message: body)
        }
        return try JSONDecoder().decode(AASADocument.self, from: data)
    }

    private static func fetchAppleCDN(rpId: String) async throws -> String {
        let url = URL(string: "https://app-site-association.cdn-apple.com/a/v1/\(rpId)")!
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            return "no response"
        }
        let reason = http.value(forHTTPHeaderField: "Apple-Failure-Reason") ?? ""
        let details = http.value(forHTTPHeaderField: "Apple-Failure-Details") ?? ""
        let body = String(data: data, encoding: .utf8) ?? ""
        return "status=\(http.statusCode) reason=\(reason) details=\(details) body=\(body.prefix(120))"
    }

    private static func associatedDomainEntries() -> [String] {
        // Entitlements are not always readable at runtime; Info.plist won't have them.
        // Log what Config expects; Xcode Associated Domains must match Config.rpId.
        return ["webcredentials:\(Config.rpId) (from Config; verify in Xcode Signing)"]
    }

    private static func teamIdPrefix() -> String? {
        // Best-effort: embedded.mobileprovision is not available for all installs.
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let text = String(data: data, encoding: .ascii)
        else {
            return nil
        }
        // Crude parse of TeamIdentifier array
        if let range = text.range(of: "<key>TeamIdentifier</key>") {
            let after = text[range.upperBound...]
            if let start = after.range(of: "<string>"),
               let end = after.range(of: "</string>", range: start.upperBound..<after.endIndex) {
                return String(after[start.upperBound..<end.lowerBound])
            }
        }
        return nil
    }
}
