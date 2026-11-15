package com.tony.cajapanda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.UUID

/** Manda bytes ESC/POS a una impresora térmica Bluetooth clásica (perfil SPP). */
object Printer {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val lock = Any()

    @SuppressLint("MissingPermission")
    fun send(ctx: Context, address: String, data: ByteArray): String? = synchronized(lock) {
        try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                ?: return "este celular no tiene Bluetooth"
            if (!adapter.isEnabled) return "el Bluetooth está apagado"
            runCatching { adapter.cancelDiscovery() }
            val device = adapter.getRemoteDevice(address)

            var socket: BluetoothSocket? = null
            var last: Exception? = null
            for (insecure in listOf(false, true)) {
                try {
                    val s = if (insecure) device.createInsecureRfcommSocketToServiceRecord(SPP)
                    else device.createRfcommSocketToServiceRecord(SPP)
                    s.connect()
                    socket = s
                    break
                } catch (e: Exception) {
                    last = e
                }
            }
            val s = socket ?: return "no se pudo conectar. ¿Está prendida y cerca? (${last?.message ?: ""})"
            s.use {
                val out = it.outputStream
                var i = 0
                while (i < data.size) {
                    val n = minOf(1024, data.size - i)
                    out.write(data, i, n)
                    out.flush()
                    i += n
                    Thread.sleep(25)
                }
                Thread.sleep(500)
            }
            null
        } catch (e: SecurityException) {
            "falta el permiso de Bluetooth"
        } catch (e: Exception) {
            e.message ?: "error desconocido"
        }
    }
}
