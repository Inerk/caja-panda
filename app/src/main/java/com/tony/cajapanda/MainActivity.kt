package com.tony.cajapanda

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())
    private var pendingApk: File? = null

    private val btPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        js(if (ok) "window.__nativePermOk&&window.__nativePermOk()" else "window.__nativePermNo&&window.__nativePermNo()")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(0xFFEDF3FC.toInt())
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "CajaNative")
        setContentView(web)
        web.loadUrl("https://$HOST/assets/www/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__nativeBack&&window.__nativeBack())?'1':'0'") { r ->
                    if (r?.contains("1") != true) finish()
                }
            }
        })

        main.postDelayed({ if (!isFinishing) Updater.check(this, silent = true) }, 5000)
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingApk
        if (apk != null && Updater.canInstall(this)) {
            pendingApk = null
            Updater.install(this, apk)
        }
    }

    fun setPendingApk(f: File) {
        pendingApk = f
    }

    fun js(code: String) {
        main.post { if (::web.isInitialized) web.evaluateJavascript(code, null) }
    }

    fun toast(msg: String) {
        main.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    inner class Bridge {
        @JavascriptInterface
        fun version(): String = Updater.versionName(this@MainActivity)

        @JavascriptInterface
        fun scan() {
            main.post {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .enableAutoZoom()
                    .build()
                GmsBarcodeScanning.getClient(this@MainActivity, options).startScan()
                    .addOnSuccessListener { b ->
                        js("window.__nativeScan&&window.__nativeScan(${JSONObject.quote(b.rawValue ?: "")})")
                    }
                    .addOnCanceledListener { }
                    .addOnFailureListener { e ->
                        js("window.__nativeScanErr&&window.__nativeScanErr(${JSONObject.quote(e.message ?: "error")})")
                    }
            }
        }

        @JavascriptInterface
        fun printers(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                main.post { btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                return "PERM"
            }
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return "NOBT"
            if (!adapter.isEnabled) return "OFF"
            val arr = JSONArray()
            try {
                adapter.bondedDevices.forEach { d ->
                    arr.put(JSONObject().put("name", d.name ?: d.address).put("address", d.address))
                }
            } catch (e: SecurityException) {
                return "PERM"
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun print(address: String, b64: String) {
            thread {
                val err = Printer.send(this@MainActivity, address, Base64.decode(b64, Base64.DEFAULT))
                js("window.__nativePrint&&window.__nativePrint(${err == null},${JSONObject.quote(err ?: "")})")
            }
        }

        @JavascriptInterface
        fun share(name: String, b64: String, mime: String) {
            thread {
                try {
                    val dir = File(cacheDir, "compartir").apply { mkdirs() }
                    val f = File(dir, name.replace(Regex("[^\\w.\\-]"), "_"))
                    f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(mime.ifBlank { "application/octet-stream" })
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    main.post { startActivity(Intent.createChooser(send, "Guardar o compartir")) }
                } catch (e: Exception) {
                    toast("No se pudo compartir: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun checkUpdate() {
            main.post { Updater.check(this@MainActivity, silent = false) }
        }
    }

    companion object {
        const val HOST = "appassets.androidplatform.net"
    }
}
