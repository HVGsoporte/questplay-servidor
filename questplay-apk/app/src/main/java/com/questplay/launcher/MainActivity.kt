package com.questplay.launcher
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private var detectedPcIp: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(QuestBridge(), "QuestBridge")
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
        discoverPc()
    }
    private fun discoverPc() {
        thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val sendData = "QUESTPLAY_DISCOVER".toByteArray()
                val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), 8081)
                socket.send(packet)
                socket.soTimeout = 5000
                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                socket.receive(receivePacket)
                val response = String(receivePacket.data, 0, receivePacket.length)
                if (response == "QUESTPLAY_PC_ACK") {
                    detectedPcIp = receivePacket.address.hostAddress
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    inner class QuestBridge {
        @JavascriptInterface
        fun getInstalledApps(): String {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val jsonArray = JSONArray()
            for (appInfo in packages) {
                val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null && appInfo.packageName != packageName) {
                    val jsonObj = JSONObject()
                    jsonObj.put("title", pm.getApplicationLabel(appInfo).toString())
                    jsonObj.put("package", appInfo.packageName)
                    jsonObj.put("coverBase64", encodeIconToBase64(pm.getApplicationIcon(appInfo)))
                    jsonArray.put(jsonObj)
                }
            }
            return jsonArray.toString()
        }
        @JavascriptInterface
        fun launchStandalone(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            }
        }
        @JavascriptInterface
        fun launchPCVR(exePath: String) {
            val pcIp = detectedPcIp ?: return
            thread {
                try {
                    val url = URL("http://$pcIp:8080/launch?exe=${java.net.URLEncoder.encode(exePath, "UTF-8")}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.responseCode
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val vdIntent = packageManager.getLaunchIntentForPackage("com.vrg.virtualdesktop")
                if (vdIntent != null) {
                    startActivity(vdIntent)
                }
            }
        }
        private fun encodeIconToBase64(drawable: Drawable): String {
            val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
                val bmp = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            return "data:image/png;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }
}
