package bg.esp8266.report

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : android.app.Activity() {
    private lateinit var ip: EditText
    private lateinit var ssid: EditText
    private lateinit var wifiPass: EditText
    private lateinit var appPass: EditText
    private lateinit var status: TextView
    private lateinit var dataView: TextView
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(box)

        fun title(t: String, size: Float = 20f) {
            box.addView(TextView(this).apply {
                text = t
                textSize = size
                setPadding(0, 14, 0, 8)
            })
        }
        fun field(hint: String, value: String = "", password: Boolean = false): EditText {
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                textSize = 16f
                if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                box.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            }
        }
        fun button(text: String, action: () -> Unit): Button {
            return Button(this).apply {
                this.text = text
                setOnClickListener { action() }
                box.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            }
        }

        title("ESP8266 REPORT SYSTEM", 24f)

        ip = field("IP адрес на ESP8266", prefs.getString("ip", "192.168.100.3") ?: "")
        button("СВЪРЖИ") { loadPage() }

        title("WiFi настройки", 19f)
        ssid = field("SSID", prefs.getString("ssid", "") ?: "")
        wifiPass = field("WiFi парола", prefs.getString("wifiPass", "") ?: "", true)
        button("ЗАПИШИ НАСТРОЙКИТЕ") {
            val params = "ssid=${enc(ssid.text.toString())}&pass=${enc(wifiPass.text.toString())}"
            request("/save?$params") { showStatus(it) }
        }

        title("Gmail", 19f)
        appPass = field("Gmail App Password", "", true)
        button("ЗАПИШИ APP PASSWORD") {
            val params = "apppassword=${enc(appPass.text.toString())}"
            request("/saveemail?$params") { showStatus(it) }
        }
        button("ТЕСТОВ EMAIL") {
            request("/testemail") { showStatus(if (it.contains("send ok", true)) "send ok" else it) }
        }

        title("Последни UART данни", 19f)
        dataView = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8, 0, 20)
        }
        box.addView(dataView)

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        box.addView(status)

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun base(): String = "http://${ip.text.toString().trim()}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(path: String, done: (String) -> Unit) {
        prefs.edit().putString("ip", ip.text.toString().trim())
            .putString("ssid", ssid.text.toString())
            .putString("wifiPass", wifiPass.text.toString()).apply()

        thread {
            val result = try {
                val c = URL(base() + path).openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 7000
                c.requestMethod = "GET"
                val text = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                text
            } catch (e: Exception) {
                "fall: ${e.message ?: "Няма връзка"}"
            }
            runOnUiThread { done(result) }
        }
    }

    private fun loadPage() {
        status.text = "Свързване..."
        request("/") { html ->
            if (html.startsWith("fall:")) {
                status.text = html
                return@request
            }
            status.text = "Свързано"
            // Показваме наличния HTML като текстов статус; настройките се запълват,
            // когато ESP8266 върне стойностите в своята страница.
            dataView.text = "ESP8266 е достъпен.\n\nУеб страницата е получена успешно."
        }
    }

    private fun showStatus(s: String) {
        status.text = if (s.startsWith("fall:")) "fall" else if (s.contains("send ok", true)) "send ok" else "Готово"
    }
}
