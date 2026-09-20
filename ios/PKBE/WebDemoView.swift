import SwiftUI
import WebKit

struct WebDemoView: View {
    @State private var reloadToken = 0
    @State private var lastError: String?

    var body: some View {
        VStack(spacing: 0) {
            if let lastError {
                Text(lastError)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.red.opacity(0.08))
            }
            WebDemoWebView(url: pageURL, reloadToken: reloadToken, lastError: $lastError)
        }
        .navigationTitle("WebView")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Reload") {
                    lastError = nil
                    reloadToken += 1
                }
            }
        }
    }

    private var pageURL: URL {
        var url = Config.baseURL
        url.append(path: "/webview/index.html")
        return url
    }
}

private struct WebDemoWebView: UIViewRepresentable {
    let url: URL
    let reloadToken: Int
    @Binding var lastError: String?

    func makeCoordinator() -> Coordinator {
        Coordinator(lastError: $lastError)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.lastError = $lastError
        if context.coordinator.loadedToken != reloadToken {
            context.coordinator.loadedToken = reloadToken
            webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var loadedToken: Int = -1
        var lastError: Binding<String?>

        init(lastError: Binding<String?>) {
            self.lastError = lastError
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            lastError.wrappedValue = error.localizedDescription
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            lastError.wrappedValue = error.localizedDescription
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            lastError.wrappedValue = nil
        }
    }
}
