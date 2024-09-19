package com.sersoluciones.flutter_pos_printer_platform

import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.plugins.FlutterPlugin

class BfLogger private constructor(private val flutterPluginBinding: FlutterPlugin.FlutterPluginBinding?) {

    private val CHANNEL = "flutter_bugfender"

    private val methodChannel: MethodChannel? by lazy {
        flutterPluginBinding?.binaryMessenger?.let {
            MethodChannel(it, CHANNEL)
        }
    }

    // Static companion object for global access
    companion object {
        private var instance: BfLogger? = null

        // Initialize the BugfenderLogger instance
        fun initialize(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
            if (instance == null) {
                instance = BfLogger(flutterPluginBinding)
            }
        }

        // Get the instance of BugfenderLogger
        fun getInstance(): BfLogger? {
            return instance
        }
    }

    // Send log to Bugfender
    fun sendLog(logMessage: String) {
        methodChannel?.let {
            it.invokeMethod("log", logMessage)
        } ?: run {
            println("MethodChannel for Bugfender is not available")
        }
    }

    // Send info log to Bugfender
    fun sendInfo(infoMessage: String) {
        methodChannel?.let {
            it.invokeMethod("info", infoMessage)
        } ?: run {
            println("MethodChannel for Bugfender is not available")
        }
    }

    // Send warning log to Bugfender
    fun sendWarning(warningMessage: String) {
        methodChannel?.let {
            it.invokeMethod("warn", warningMessage)
        } ?: run {
            println("MethodChannel for Bugfender is not available")
        }
    }
}