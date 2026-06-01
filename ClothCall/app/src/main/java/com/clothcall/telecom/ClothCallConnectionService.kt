package com.clothcall.telecom

import android.net.Uri
import android.telecom.*
import android.util.Log

private const val TAG = "ClothCall_Telecom"

class ClothCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val caregiverName = request?.extras?.getString(TelecomHelper.EXTRA_CAREGIVER_NAME) ?: "ClothCall"
        Log.d(TAG, "Creating incoming connection — caller: $caregiverName")

        val connection = ClothCallConnection(caregiverName)
        TelecomHelper.activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Incoming connection failed — falling back to in-app audio only")
    }
}

class ClothCallConnection(val caregiverName: String) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = false          // route to earpiece, not VoIP speaker
        setCallerDisplayName(caregiverName, TelecomManager.PRESENTATION_ALLOWED)
        setAddress(Uri.parse("tel:0000000000"), TelecomManager.PRESENTATION_ALLOWED)
        setRinging()
    }

    override fun onAnswer() {
        setActive()
        Log.d(TAG, "Connection answered — audio active")
    }

    override fun onReject() {
        disconnect()
    }

    override fun onDisconnect() {
        disconnect()
    }

    fun disconnect() {
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        TelecomHelper.activeConnection = null
        Log.d(TAG, "Connection disconnected")
    }
}
