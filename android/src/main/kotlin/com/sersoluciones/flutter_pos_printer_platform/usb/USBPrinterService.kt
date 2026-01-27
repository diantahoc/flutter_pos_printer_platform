package com.sersoluciones.flutter_pos_printer_platform.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.sersoluciones.flutter_pos_printer_platform.R
import java.nio.charset.Charset
import java.util.*

class USBPrinterService private constructor(private var mHandler: Handler?) {
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIndent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null
    var state: Int = STATE_USB_NONE

    fun setHandler(handler: Handler?) {
        mHandler = handler
    }

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ((ACTION_USB_PERMISSION == action)) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(LOG_TAG, "Success get permission for device ${usbDevice?.vendorId}")
                        mUsbDevice = usbDevice
                        state = STATE_USB_CONNECTED
                        mHandler?.obtainMessage(STATE_USB_CONNECTED)?.sendToTarget()
                    } else {
                        // FIX: Safe null check for usbDevice
                        val deviceName = usbDevice?.deviceName ?: "Unknown Device"
                        Toast.makeText(context, (mContext?.getString(R.string.user_refuse_perm) ?: "Permission refused") + ": $deviceName", Toast.LENGTH_LONG).show()
                        state = STATE_USB_NONE
                        mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                    }
                }
            } else if ((UsbManager.ACTION_USB_DEVICE_DETACHED == action)) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, mContext?.getString(R.string.device_off), Toast.LENGTH_LONG).show()
                    closeConnectionIfExists()
                    state = STATE_USB_NONE
                    mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                }
            }
        }
    }

    fun init(reactContext: Context?) {
        mContext = reactContext
        mUSBManager = mContext!!.getSystemService(Context.USB_SERVICE) as UsbManager

        // FIX: Explicit Intent for Android 14 security
        val intent = Intent(ACTION_USB_PERMISSION)
        intent.setPackage(mContext?.packageName)

        // FIX: FLAG_MUTABLE for USB Permission results
        mPermissionIndent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(mContext, 0, intent, 0)
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        // FIX: Use integer 2 for Context.RECEIVER_EXPORTED to avoid compilation errors
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter, 2) 
        } else {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        }
        
        Log.v(LOG_TAG, "ESC/POS Printer initialized with Android 14 fixes")
    }

    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            try {
                mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
                mUsbDeviceConnection!!.close()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error closing connection: ${e.message}")
            }
            mUsbInterface = null
            mEndPoint = null
            mUsbDevice = null
            mUsbDeviceConnection = null
        }
    }

    val deviceList: List<UsbDevice>
        get() {
            if (mUSBManager == null) return emptyList()
            return ArrayList(mUSBManager!!.deviceList.values)
        }

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
        if ((mUsbDevice == null) || (mUsbDevice!!.vendorId != vendorId) || (mUsbDevice!!.productId != productId)) {
            synchronized(printLock) {
                closeConnectionIfExists()
                val usbDevices: List<UsbDevice> = deviceList
                for (usbDevice: UsbDevice in usbDevices) {
                    if ((usbDevice.vendorId == vendorId) && (usbDevice.productId == productId)) {
                        mUSBManager!!.requestPermission(usbDevice, mPermissionIndent)
                        state = STATE_USB_CONNECTING
                        mHandler?.obtainMessage(STATE_USB_CONNECTING)?.sendToTarget()
                        return true
                    }
                }
                return false
            }
        } else {
            mHandler?.obtainMessage(state)?.sendToTarget()
        }
        return true
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null || mUSBManager == null) return false
        if (mUsbDeviceConnection != null) return true

        // FIX: Loop through ALL interfaces to find the printer bulk endpoint
        for (i in 0 until mUsbDevice!!.interfaceCount) {
            val usbInterface = mUsbDevice!!.getInterface(i)
            for (j in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    val connection = mUSBManager!!.openDevice(mUsbDevice)
                    if (connection != null) {
                        if (connection.claimInterface(usbInterface, true)) {
                            mEndPoint = ep
                            mUsbInterface = usbInterface
                            mUsbDeviceConnection = connection
                            return true
                        }
                        connection.close()
                    }
                }
            }
        }
        return false
    }

    fun printText(text: String): Boolean {
        val isConnected = openConnection()
        return if (isConnected) {
            Thread {
                synchronized(printLock) {
                    val bytes: ByteArray = text.toByteArray(Charset.forName("UTF-8"))
                    mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                }
            }.start()
            true
        } else false
    }

    fun printRawData(data: String): Boolean {
        val isConnected = openConnection()
        if (isConnected) {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            Thread {
                synchronized(printLock) {
                    mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                }
            }.start()
            return true
        }
        return false
    }

    fun printBytes(bytes: ByteArray): Boolean {
        val isConnected = openConnection()
        if (isConnected) {
            Thread {
                synchronized(printLock) {
                    mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                }
            }.start()
            return true
        }
        return false
    }

    companion object {
        private const val LOG_TAG = "USBPrinterService"
        private const val ACTION_USB_PERMISSION = "com.flutter_pos_printer.USB_PERMISSION"
        const val STATE_USB_NONE = 0
        const val STATE_USB_CONNECTING = 1
        const val STATE_USB_CONNECTED = 2
        private val printLock = Any()

        @SuppressLint("StaticFieldLeak")
        private var instance: USBPrinterService? = null

        fun getInstance(handler: Handler?): USBPrinterService {
            if (instance == null) instance = USBPrinterService(handler)
            return instance!!
        }
    }
}
