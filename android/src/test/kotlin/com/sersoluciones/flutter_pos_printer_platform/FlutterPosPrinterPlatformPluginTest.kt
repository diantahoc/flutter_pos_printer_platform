package com.sersoluciones.flutter_pos_printer_platform

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.BluetoothService
import com.sersoluciones.flutter_pos_printer_platform.usb.USBPrinterService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class FlutterPosPrinterPlatformPluginTest {
    private lateinit var plugin: FlutterPosPrinterPlatformPlugin
    private lateinit var engineBinding: FlutterPlugin.FlutterPluginBinding

    @Before
    fun setUp() {
        for (serviceClass in listOf(USBPrinterService::class.java, BluetoothService::class.java)) {
            serviceClass.getDeclaredField("mInstance").apply {
                isAccessible = true
                set(null, null)
            }
        }
        RuntimeEnvironment.getApplication().applicationInfo.targetSdkVersion = 34
        plugin = FlutterPosPrinterPlatformPlugin()
        engineBinding = mock(FlutterPlugin.FlutterPluginBinding::class.java)
        `when`(engineBinding.binaryMessenger).thenReturn(mock(BinaryMessenger::class.java))
        plugin.onAttachedToEngine(engineBinding)
    }

    @Test
    fun detachesFromActivityBeforeServicesAreInitialized() {
        plugin.onDetachedFromActivity()
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun detachesForConfigChangesBeforeServicesAreInitialized() {
        plugin.onDetachedFromActivityForConfigChanges()
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun detachesFromEngineWithoutAnActivity() {
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun detachesAfterUsbInitializationFails() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.USB_SERVICE) {
                    throw SecurityException("Simulated USB initialization failure")
                }
                return super.getSystemService(name)
            }
        }
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        assertThrows(SecurityException::class.java) {
            plugin.onAttachedToActivity(createActivityBinding(activity))
        }

        plugin.onDetachedFromActivityForConfigChanges()
        plugin.onDetachedFromActivity()
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun initializesAndReattachesActivity() {
        val firstActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        plugin.onAttachedToActivity(createActivityBinding(firstActivity))
        assertSame(firstActivity, getBluetoothActivity())

        plugin.onDetachedFromActivityForConfigChanges()
        assertNull(getBluetoothActivity())

        val secondActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        plugin.onReattachedToActivityForConfigChanges(createActivityBinding(secondActivity))
        assertSame(secondActivity, getBluetoothActivity())

        plugin.onDetachedFromActivity()
        assertNull(getBluetoothActivity())
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun reattachesActivityAfterIncompleteInitialization() {
        plugin.onDetachedFromActivityForConfigChanges()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        plugin.onReattachedToActivityForConfigChanges(createActivityBinding(activity))
        assertSame(activity, getBluetoothActivity())
        plugin.onDetachedFromActivity()
        plugin.onDetachedFromEngine(engineBinding)
    }

    @Test
    fun registersUsbReceiverWithoutExportingOnRecentAndroid() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            var receiverFlags: Int? = null

            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    throw SecurityException("Receiver export flags are required")
                }
                return null
            }

            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? {
                receiverFlags = flags
                return null
            }
        }
        USBPrinterService.getInstance(Handler(Looper.getMainLooper())).init(context)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            assertEquals(Context.RECEIVER_NOT_EXPORTED, context.receiverFlags)
        } else {
            assertNull(context.receiverFlags)
        }
    }

    @Test
    fun usbPermissionAllowsExtrasAndTargetsOwnPackage() {
        val context = RuntimeEnvironment.getApplication()
        val service = USBPrinterService.getInstance(Handler(Looper.getMainLooper()))
        service.init(context)
        val field = USBPrinterService::class.java.getDeclaredField("mPermissionIndent")
        field.isAccessible = true
        val permissionIntent = field.get(service) as PendingIntent
        val shadow = shadowOf(permissionIntent)

        assertEquals(context.packageName, shadow.savedIntent.`package`)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(0, shadow.flags and PendingIntent.FLAG_IMMUTABLE)
            assertNotEquals(0, shadow.flags and PendingIntent.FLAG_MUTABLE)
        }
    }

    private fun createActivityBinding(activity: Activity): ActivityPluginBinding {
        val binding = mock(ActivityPluginBinding::class.java)
        `when`(binding.activity).thenReturn(activity)
        return binding
    }

    private fun getBluetoothActivity(): Activity? {
        val service = BluetoothService.getInstance(Handler(Looper.getMainLooper()))
        val field = BluetoothService::class.java.getDeclaredField("currentActivity")
        field.isAccessible = true
        return field.get(service) as Activity?
    }
}
