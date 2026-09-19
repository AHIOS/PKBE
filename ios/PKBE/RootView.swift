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
    @State private var localPasskey: String = "—"
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
                LabeledContent("Server status", value: me?.thisDeviceStatus ?? "—")
                LabeledContent("Local passkey", value: localPasskey)
                LabeledContent("Active device", value: short(me?.activeDeviceId))
                LabeledContent("Pending device", value: short(me?.pendingDeviceId))
            }
            if let cred = me?.activeCredential {
                Section("Server registry (not Passwords)") {
                    LabeledContent("Credential", value: cred.credentialIdPrefix)
                    LabeledContent("AAGUID", value: cred.aaguid)
                    LabeledContent("Backup eligible (BE)", value: cred.backupEligible ? "yes" : "no")
                    LabeledContent("Backup state (BS)", value: cred.backupState ? "yes" : "no")
                    Text("This is the RP database row. Deleting in Passwords does not remove it until Refresh + reconcile runs.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Section("Actions") {
                Button("Refresh + reconcile Passwords") {
                    Task { await refresh(reconcileLocal: true) }
                }
                .disabled(busy)
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
                Button("Clear server enrollment", role: .destructive) {
                    Task { await clearEnrollment() }
                }
                .disabled(busy || me?.thisDeviceStatus == "NONE")
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
                Text("Enroll this device asks iOS for a local platform passkey only. Nearby QR is used later by Activate via nearby device, not during enroll. Refresh loads server state, then checks whether this device still has the passkey in Passwords. If you deleted it there, enrollment is cleared on the server.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Activation")
        .task { await refresh(reconcileLocal: true) }
        .refreshable { await refresh(reconcileLocal: true) }
    }

    private func refresh(reconcileLocal: Bool) async {
        busy = true
        defer { busy = false }
        do {
            var latest = try await APIClient.shared.me()
            localPasskey = "—"

            guard reconcileLocal else {
                me = latest
                return
            }

            if latest.thisDeviceStatus == "NONE" {
                localPasskey = "none (not enrolled on server)"
                me = latest
                if error?.contains("Cleared server enrollment") != true {
                    error = nil
                }
                return
            }

            guard let credId = latest.thisDeviceCredentialId, !credId.isEmpty else {
                localPasskey = "unknown (server missing credential id — redeploy RP)"
                me = latest
                error = "Server did not return thisDeviceCredentialId. Redeploy the RP so refresh can reconcile Passwords deletions."
                return
            }

            switch await passkeys.localPasskeyPresence(credentialIdBase64Url: credId) {
            case .present:
                localPasskey = "present in Passwords"
                me = latest
                error = nil
            case .absent:
                localPasskey = "missing in Passwords"
                PasskeyLog.info("Local passkey missing — clearing server enrollment for this device")
                latest = try await APIClient.shared.unenroll()
                me = latest
                localPasskey = "none (cleared after Passwords delete)"
                error = "Passkey not found in Passwords on this device. Cleared server enrollment."
            }
        } catch {
            self.error = (error as? APIError)?.message ?? error.localizedDescription
        }
    }

    private func clearEnrollment() async {
        busy = true
        defer { busy = false }
        do {
            me = try await APIClient.shared.unenroll()
            localPasskey = "none"
            error = "Server enrollment cleared for this device."
        } catch {
            self.error = (error as? APIError)?.message ?? error.localizedDescription
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
            localPasskey = "present"
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
            localPasskey = "present"
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
