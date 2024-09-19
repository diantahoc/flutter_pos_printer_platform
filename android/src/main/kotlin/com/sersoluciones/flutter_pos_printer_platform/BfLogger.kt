import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.FlutterEngine

class BfLogger(private val flutterEngine: FlutterEngine?) {
    private val CHANNEL = "flutter_bugfender"

    private val methodChannel: MethodChannel? by lazy {
        flutterEngine?.dartExecutor?.binaryMessenger?.let {
            MethodChannel(it, CHANNEL)
        }
    }

    // Function to verify MethodChannel existence and send log
    fun sendLog(logMessage: String) {
        methodChannel?.let {
            it.invokeMethod("log", logMessage)
        } ?: run {
            // Handle the absence of the MethodChannel
            println("MethodChannel for Bugfender is not available")
        }
    }

    // Function to send info log
    fun sendInfo(infoMessage: String) {
        methodChannel?.let {
            it.invokeMethod("info", infoMessage)
        } ?: run {
            // Handle the absence of the MethodChannel
            println("MethodChannel for Bugfender is not available")
        }
    }

    // Function to send warning log
    fun sendWarning(warningMessage: String) {
        methodChannel?.let {
            it.invokeMethod("warn", warningMessage)
        } ?: run {
            // Handle the absence of the MethodChannel
            println("MethodChannel for Bugfender is not available")
        }
    }
}
