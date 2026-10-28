package com.udb.controlgiro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Estado(val distance: Double, val mode: String, val motor: String)

class ApiError(message: String) : Exception(message)

/** Cliente HTTP mínimo para la API del Worker (sin librerías extra). */
object Api {
    var baseUrl: String = "https://mi-control-giro-api.girogiro.workers.dev"

    private suspend fun request(path: String, method: String, body: JSONObject? = null): Estado =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = JSONObject(text)
                if (code !in 200..299) throw ApiError(json.optString("error", "Error $code"))
                Estado(
                    distance = json.getDouble("distance"),
                    mode = json.getString("mode"),
                    motor = json.getString("motor")
                )
            } finally {
                conn.disconnect()
            }
        }

    suspend fun estado(): Estado = request("/api/status", "GET")

    suspend fun setModo(auto: Boolean): Estado =
        request("/api/mode", "POST", JSONObject().put("mode", if (auto) "auto" else "manual"))

    /** direccion: "left", "right" o "stop" */
    suspend fun mover(direccion: String): Estado =
        request("/api/motor", "POST", JSONObject().put("direction", direccion))
}
