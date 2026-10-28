package com.udb.controlgiro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

/**
 * Cliente Bluetooth clásico (SPP) para hablar directo con el ESP32.
 * Protocolo:
 *   App -> ESP32 : LEFT | RIGHT | STOP | AUTO | MANUAL   (una línea terminada en \n)
 *   ESP32 -> App : <cm>;<modo>;<motor>    ej. 152;auto;right
 *
 * Requiere que el celular ya esté emparejado con "ESP32_Giro" (Ajustes > Bluetooth)
 * y el permiso BLUETOOTH_CONNECT concedido en Android 12+.
 */
object Bt {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var salida: OutputStream? = null

    val conectado: Boolean get() = socket?.isConnected == true

    /** Dispositivos ya emparejados con el celular. */
    @SuppressLint("MissingPermission")
    fun emparejados(ctx: Context): List<BluetoothDevice> = try {
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        adapter?.bondedDevices?.toList() ?: emptyList()
    } catch (e: SecurityException) {
        emptyList() // falta el permiso BLUETOOTH_CONNECT
    }

    @SuppressLint("MissingPermission")
    suspend fun conectar(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        desconectar()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect() // lanza IOException si falla
        socket = s
        salida = s.outputStream
    }

    /** Lee líneas hasta que se cierre la conexión. Llamar desde una corrutina propia. */
    suspend fun leerLineas(onLinea: (String) -> Unit) = withContext(Dispatchers.IO) {
        val s = socket ?: return@withContext
        try {
            val lector = s.inputStream.bufferedReader()
            while (true) {
                val linea = lector.readLine() ?: break
                onLinea(linea.trim())
            }
        } catch (_: Exception) {
            // conexión cerrada o perdida
        } finally {
            desconectar()
        }
    }

    suspend fun enviar(comando: String) = withContext(Dispatchers.IO) {
        try {
            salida?.write((comando + "\n").toByteArray())
            salida?.flush()
        } catch (_: Exception) {
            desconectar()
        }
    }

    fun desconectar() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        salida = null
    }

    /** Convierte "152;auto;right" en Estado; devuelve null si la línea no es válida. */
    fun parsear(linea: String): Estado? {
        val p = linea.split(";")
        if (p.size != 3) return null
        val cm = p[0].toDoubleOrNull() ?: return null
        return Estado(distance = cm, mode = p[1], motor = p[2])
    }
}
