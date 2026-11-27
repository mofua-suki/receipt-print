package dev.receiptprint.receipt_print

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.json.JSONTokener

// Rendering uses a separate, off-screen WebView. The preview WebView never runs scripts.
internal class ReceiptSvgRenderer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val active = mutableMapOf<WebView, () -> Unit>()
    private var closed = false
    private val script by lazy {
        context.assets.open("receiptline.js").bufferedReader().use { it.readText() }
    }

    fun render(text: String, columns: Int, result: MethodChannel.Result) {
        // Receipt text is a JavaScript string argument, never executable source.
        val invocation = """
            $script
            ;(() => {
                if (!window.receiptline || typeof window.receiptline.transform !== "function") return null;
                try {
                    return {svg: window.receiptline.transform(${JSONObject.quote(text)}, {
                        cpl: $columns, encoding: "cp932"
                    })};
                } catch (error) {
                    return {error: String(error)};
                }
            })()
        """.trimIndent()
        main.post {
            if (closed) {
                result.error("engine_error", "Receipt renderer was detached", null)
            } else {
                start(invocation, result)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // Only the bundled script runs on an offline blank page.
    private fun start(invocation: String, result: MethodChannel.Result) {
        val webView = try {
            WebView(context)
        } catch (error: RuntimeException) {
            result.error("engine_error", error.message, null)
            return
        }
        lateinit var timeout: Runnable

        fun finish(response: () -> Unit) {
            if (active.remove(webView) == null) return
            main.removeCallbacks(timeout)
            webView.destroy()
            response()
        }

        fun fail(message: String) = finish { result.error("engine_error", message, null) }

        timeout = Runnable { fail("ReceiptLine SVG generation timed out") }
        active[webView] = { fail("Receipt renderer was detached") }
        main.postDelayed(timeout, 15_000)
        try {
            webView.restrictToLocalContent()
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                private var started = false

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun onPageFinished(view: WebView, url: String) {
                    if (started || url != "about:blank") return
                    started = true
                    view.evaluateJavascript(invocation) { encoded ->
                        val response = runCatching { JSONTokener(encoded ?: "null").nextValue() }
                            .getOrNull() as? JSONObject
                        if (response == null) {
                            fail("ReceiptLine renderer returned no response")
                            return@evaluateJavascript
                        }
                        val svg = response.optString("svg")
                        if (!svg.contains("<svg")) {
                            val message = response.optString("error").ifEmpty { "ReceiptLine SVG generation failed" }
                            finish { result.error("invalid_arguments", message, null) }
                        } else {
                            finish { result.success(svg.replace(REMOTE_FONT_STYLE, "")) }
                        }
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    fail("ReceiptLine rendering process ended")
                    return true
                }
            }
            webView.loadUrl("about:blank")
        } catch (error: RuntimeException) {
            fail(error.message ?: "Unable to initialize the receipt renderer")
        }
    }

    fun close() {
        main.post {
            closed = true
            active.values.toList().forEach { it() }
        }
    }

    private companion object {
        // ReceiptLine includes an external font import. Keep generated SVGs self-contained.
        val REMOTE_FONT_STYLE = Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL)
    }
}
