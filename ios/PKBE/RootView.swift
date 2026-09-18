import SwiftUI

struct RootView: View {
    @State private var loggedIn = SessionStore.token != nil

    var body: some View {
        NavigationStack {
            if loggedIn {
                HomeView(loggedIn: $loggedIn)
            } else {
                LoginView(loggedIn: $loggedIn)
            }
        }
    }
}

struct LoginView: View {
    @Binding var loggedIn: Bool
    @State private var username = "alice"
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        Form {
            Section("Stub login") {
                TextField("username", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Continue") {
                    Task { await login() }
                }
                .disabled(busy || username.isEmpty)
            }
            if let error {
                Section {
                    Text(error).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("PKBE")
    }

    private func login() async {
        busy = true
        defer { busy = false }
        do {
            let response = try await APIClient.shared.login(username: username)
            SessionStore.token = response.token
            SessionStore.username = response.username
            loggedIn = true
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct HomeView: View {
    @Binding var loggedIn: Bool
    @State private var me: MeResponse?
    @State private var error: String?
    @State private var diagnostics: String?
    @State private var busy = false
    private let passkeys = PasskeyService()

    var body: some View {
        List {
            Section("Client config") {
                LabeledContent("Bundle ID", value: Bundle.main.bundleIdentifier ?? "—")
                LabeledContent("RP ID", value: Config.rpId)
                LabeledContent("API", value: Config.baseURL.host ?? Config.baseURL.absoluteString)
            }
            Section("This device") {
                LabeledContent("Device ID", value: short(DeviceIdentity.deviceId))
                LabeledContent("User", value: me?.username ?? SessionStore.username ?? "—")
                LabeledContent("Status", value: me?.thisDeviceStatus ?? "—")
                LabeledContent("Active device", value: short(me?.activeDeviceId))
                LabeledContent("Pending device", value: short(me?.pendingDeviceId))
            }
            if let cred = me?.activeCredential {
                Section("Active passkey") {
                    LabeledContent("Credential", value: cred.credentialIdPrefix)
                    LabeledContent("AAGUID", value: cred.aaguid)
                    LabeledContent("Backup eligible (BE)", value: cred.backupEligible ? "yes" : "no")
                    LabeledContent("Backup state (BS)", value: cred.backupState ? "yes" : "no")
                }
            }
            Section("Actions") {
                Button("Refresh status") {
                    Task { await refresh() }
                }
                Button("Run association diagnostics") {
                    Task { await runDiagnostics() }
                }
                .disabled(busy)
                Button("Enroll this device") {
                    Task { await enroll() }
                }
                .disabled(busy)
                Button("Activate via nearby device") {
                    Task { await handover() }
                }
                .disabled(busy)
                Button("Log out", role: .destructive) {
                    Task {
                        await APIClient.shared.logout()
                        loggedIn = false
                    }
                }
            }
            if let error {
                Section("Error") {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .textSelection(.enabled)
                }
            }
            if let diagnostics {
                Section("Diagnostics (also in Xcode console as [PKBE])") {
                    Text(diagnostics)
                        .font(.system(.caption2, design: .monospaced))
                        .textSelection(.enabled)
                }
            }
            Section {
                Text("Handover uses the system FIDO QR and caBLE. Code 1004 usually means Associated Domains / AASA / RP ID mismatch. Use two iPhones with different Apple IDs so iCloud does not copy the passkey.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Activation")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private func refresh() async {
        do {
            me = try await APIClient.shared.me()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func runDiagnostics() async {
        busy = true
        defer { busy = false }
        diagnostics = await PasskeyDiagnostics.runPreflight(expectedRpId: Config.rpId)
    }

    private func enroll() async {
        busy = true
        defer { busy = false }
        do {
            let options = try await APIClient.shared.registerOptions()
            let credential = try await passkeys.createPasskey(options: options)
            me = try await APIClient.shared.registerVerify(credential: credential)
            error = nil
        } catch {
            self.error = (error as? APIError)?.message ?? error.localizedDescription
        }
    }

    private func handover() async {
        busy = true
        defer { busy = false }
        do {
            if me?.thisDeviceStatus != "PENDING" {
                let options = try await APIClient.shared.registerOptions()
                let credential = try await passkeys.createPasskey(options: options)
                me = try await APIClient.shared.registerVerify(credential: credential)
            }
            let assertionOptions = try await APIClient.shared.handoverOptions()
            let assertion = try await passkeys.assertHandover(options: assertionOptions)
            me = try await APIClient.shared.handoverVerify(credential: assertion)
            error = nil
        } catch {
            self.error = (error as? APIError)?.message ?? error.localizedDescription
        }
    }

    private func short(_ value: String?) -> String {
        guard let value, !value.isEmpty else { return "—" }
        return String(value.prefix(8))
    }
}
