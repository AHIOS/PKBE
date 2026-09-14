import Foundation

enum DeviceIdentity {
    private static let key = "pkbe.deviceId"

    static var deviceId: String {
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let created = UUID().uuidString.lowercased()
        UserDefaults.standard.set(created, forKey: key)
        return created
    }
}

enum SessionStore {
    private static let key = "pkbe.token"

    static var token: String? {
        get { UserDefaults.standard.string(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }

    static var username: String? {
        get { UserDefaults.standard.string(forKey: "pkbe.username") }
        set { UserDefaults.standard.set(newValue, forKey: "pkbe.username") }
    }

    static func clear() {
        token = nil
        username = nil
    }
}
