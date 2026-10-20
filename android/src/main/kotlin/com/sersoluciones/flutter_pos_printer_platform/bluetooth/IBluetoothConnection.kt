package com.sersoluciones.flutter_pos_printer_platform.bluetooth
import io.flutter.plugin.common.MethodChannel.Result

interface IBluetoothConnection {
    fun connect(address: String, result: Result)
    fun stop()
    /** true when every byte was handed to the link; false when the link is gone. */
    fun write(out: ByteArray?): Boolean
    var state: Int
}