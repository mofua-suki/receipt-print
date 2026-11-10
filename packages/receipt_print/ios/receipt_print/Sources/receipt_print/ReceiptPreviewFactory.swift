import Flutter
import WebKit

final class ReceiptPreviewFactory: NSObject, FlutterPlatformViewFactory {
    func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol { FlutterStandardMessageCodec.sharedInstance() }
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        ReceiptPreviewPlatformView(frame: frame, svg: (args as? [String: Any])?["svg"] as? String ?? "")
    }
}

final class ReceiptPreviewPlatformView: NSObject, FlutterPlatformView, WKNavigationDelegate {
    private let webView: WKWebView
    private var hasLoadedDocument = false

    init(frame: CGRect, svg: String) {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        configuration.websiteDataStore = .nonPersistent()
        webView = WKWebView(frame: frame, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.isOpaque = false
        webView.backgroundColor = .white
        webView.loadHTMLString("""
          <!doctype html><html><head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; base-uri 'none'; form-action 'none'; style-src 'unsafe-inline'; img-src data:">
          <style>body{margin:0;padding:16px;background:#f2f2f2}svg{width:100%;max-width:576px;height:auto;display:block;margin:0 auto;padding:12px;box-sizing:border-box;background:white;box-shadow:0 1px 6px #ccc}</style>
          </head><body>\(svg)</body></html>
          """, baseURL: nil)
    }
    func view() -> UIView { webView }
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if !hasLoadedDocument, action.navigationType == .other,
           action.request.url?.absoluteString == "about:blank" {
            hasLoadedDocument = true
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
        }
    }
}
