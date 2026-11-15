package com.tony.cajapanda

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Revisa en GitHub si hay una versión nueva de la Caja, la descarga y la instala. */
object Updater {
    private const val LATEST_URL = "https://api.github.com/repos/Inerk/caja-panda/releases/latest"
    @Volatile private var busy = false

    fun versionCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun versionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    fun check(act: MainActivity, silent: Boolean) {
        if (busy) return
        busy = true
        thread {
            try {
                val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "CajaPanda")
                }
                val json = try {
                    if (conn.responseCode != 200) error("GitHub respondió ${conn.responseCode}")
                    JSONObject(conn.inputStream.bufferedReader().readText())
                } finally {
                    conn.disconnect()
                }
                val tag = json.getString("tag_name")
                val code = tag.substringAfterLast('.').toLongOrNull() ?: error("Versión no reconocida: $tag")
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
                }
                val current = versionCode(act)
                if (code <= current || url == null) {
                    busy = false
                    if (!silent) act.toast("Ya tienes la versión más nueva (v${versionName(act)})")
                    return@thread
                }
                val notes = json.optString("body", "").trim()
                act.runOnUiThread {
                    busy = false
                    if (act.isFinishing) return@runOnUiThread
                    AlertDialog.Builder(act)
                        .setTitle("Nueva versión $tag")
                        .setMessage("Tienes la v${versionName(act)}." + if (notes.isNotBlank()) "\n\nQué cambió:\n$notes" else "")
                        .setPositiveButton("Actualizar") { _, _ -> download(act, url, tag) }
                        .setNegativeButton("Después", null)
                        .show()
                }
            } catch (e: Exception) {
                busy = false
                if (!silent) act.toast("No se pudo revisar si hay actualización: ${e.message}")
            }
        }
    }

    private fun download(act: MainActivity, apkUrl: String, tag: String) {
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(act).apply { text = "Descargando…" }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
            addView(label)
            addView(bar)
        }
        val dlg = AlertDialog.Builder(act).setTitle("Actualizando a $tag").setView(box).setCancelable(false).show()
        thread {
            try {
                val dir = File(act.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "Caja-Panda-$tag.apk")
                var url = URL(apkUrl)
                var conn: HttpURLConnection
                var hops = 0
                while (true) {
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "CajaPanda")
                    }
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 5) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        hops++
                        continue
                    }
                    if (code != 200) error("la descarga falló ($code)")
                    break
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                act.runOnUiThread { bar.progress = pct; label.text = "Descargando… $pct%" }
                            }
                        }
                    }
                }
                conn.disconnect()
                act.runOnUiThread {
                    runCatching { dlg.dismiss() }
                    if (canInstall(act)) install(act, target)
                    else {
                        act.setPendingApk(target)
                        AlertDialog.Builder(act)
                            .setTitle("Falta un permiso")
                            .setMessage("La primera vez, Android pide permitir que Caja Panda instale actualizaciones. Activa \"Permitir de esta fuente\" en la pantalla que se abre y regresa.")
                            .setPositiveButton("Abrir ajustes") { _, _ ->
                                runCatching {
                                    act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                act.runOnUiThread { runCatching { dlg.dismiss() } }
                act.toast("No se pudo descargar la actualización: ${e.message}")
            }
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }
}
