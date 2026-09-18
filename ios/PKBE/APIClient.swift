import Foundation

final class APIClient {
    static let shared = APIClient()

    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        return decoder
    }()

    func login(username: String) async throws -> LoginResponse {
        let body = try JSONSerialization.data(withJSONObject: ["username": username])
        return try await post("/v1/login", body: body, authenticated: false)
    }

    func logout() async {
        _ = try? await send(path: "/v1/logout", method: "POST", body: nil, authenticated: true)
        SessionStore.clear()
    }

    func me() async throws -> MeResponse {
        try await get("/v1/me")
    }

    func unenroll() async throws -> MeResponse {
        let body = try JSONSerialization.data(withJSONObject: ["deviceId": DeviceIdentity.deviceId])
        return try await post("/v1/unenroll", body: body, authenticated: true)
    }

    func registerOptions() async throws -> CreationOptions {
        let body = try JSONSerialization.data(withJSONObject: ["deviceId": DeviceIdentity.deviceId])
        return try await post("/v1/register/options", body: body, authenticated: true)
    }

    func registerVerify(credential: [String: Any]) async throws -> MeResponse {
        let payload: [String: Any] = ["deviceId": DeviceIdentity.deviceId, "credential": credential]
        let body = try JSONSerialization.data(withJSONObject: payload)
        return try await post("/v1/register/verify", body: body, authenticated: true)
    }

    func handoverOptions() async throws -> AssertionOptions {
        let body = try JSONSerialization.data(withJSONObject: ["deviceId": DeviceIdentity.deviceId])
        return try await post("/v1/handover/options", body: body, authenticated: true)
    }

    func handoverVerify(credential: [String: Any]) async throws -> MeResponse {
        let payload: [String: Any] = ["deviceId": DeviceIdentity.deviceId, "credential": credential]
        let body = try JSONSerialization.data(withJSONObject: payload)
        return try await post("/v1/handover/verify", body: body, authenticated: true)
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        let data = try await send(path: path, method: "GET", body: nil, authenticated: true)
        return try decoder.decode(T.self, from: data)
    }

    private func post<T: Decodable>(_ path: String, body: Data, authenticated: Bool) async throws -> T {
        let data = try await send(path: path, method: "POST", body: body, authenticated: authenticated)
        return try decoder.decode(T.self, from: data)
    }

    private func send(path: String, method: String, body: Data?, authenticated: Bool) async throws -> Data {
        var url = Config.baseURL
        url.append(path: path)
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(DeviceIdentity.deviceId, forHTTPHeaderField: "X-Device-Id")
        if authenticated, let token = SessionStore.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 204 {
            return Data()
        }
        if status >= 400 {
            if let apiError = try? decoder.decode(APIError.self, from: data) {
                throw apiError
            }
            throw APIError(error: "HTTP_\(status)", message: String(data: data, encoding: .utf8) ?? "Request failed")
        }
        return data
    }
}
