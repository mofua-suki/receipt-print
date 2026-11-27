package dev.receiptprint.receipt_print

import android.content.Context
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

internal class ReceiptPreviewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val svg = (args as? Map<*, *>)?.get("svg") as? String ?: ""
        val container = FrameLayout(context)
        val preview = WebView(context)
        container.addView(preview, FrameLayout.LayoutParams(-1, -1))

        fun disposePreview() {
            if (preview.parent === container) {
                container.removeView(preview)
                preview.destroy()
            }
        }

        preview.apply {
            restrictToLocalContent()
            settings.javaScriptEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    disposePreview()
                    return true
                }
            }
            loadDataWithBaseURL(null, """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; base-uri 'none'; form-action 'none'; style-src 'unsafe-inline'; img-src data:">
                <style>body{margin:0;padding:16px;background:#f2f2f2}svg{width:100%;max-width:576px;height:auto;display:block;margin:0 auto;padding:12px;box-sizing:border-box;background:white;box-shadow:0 1px 6px #ccc}</style>
                </head><body>$svg</body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        return object : PlatformView {
            override fun getView(): View = container
            override fun dispose() = disposePreview()
        }
    }
}

internal fun WebView.restrictToLocalContent() {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
}
