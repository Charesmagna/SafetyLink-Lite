package com.example

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.net.Uri
import android.telephony.SmsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Common UUIDs for iTags and standard keys
val SIMPLE_KEY_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
val SIMPLE_KEY_CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
val IMMEDIATE_ALERT_SERVICE_UUID: UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
val ALERT_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

data class SafetyLinkUser(
    val email: String,
    val orgId: String,
    val role: String,
    val timestamp: String
)

data class EmergencyContact(
    val name: String,
    val phone: String
)

data class OrganizationAlert(
    val email: String,
    val role: String,
    val reason: String,
    val timestamp: String,
    val active: Boolean
)

enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERED_SERVICES
}

data class BleDeviceItem(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    WARN,
    DANGER
}

class BleLabViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Authentication & Organization Gate State
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _registeredUsers = mutableStateListOf<SafetyLinkUser>(
        SafetyLinkUser("Tshilidzi.mukwevho54@gmail.com", "SL-CORE-FIREBASE-2026", "ADMIN", "2026-06-28 10:00:00")
    )
    val registeredUsers: List<SafetyLinkUser> = _registeredUsers

    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    private val _currentUserOrgId = MutableStateFlow("")
    val currentUserOrgId: StateFlow<String> = _currentUserOrgId.asStateFlow()

    private val _currentUserRole = MutableStateFlow("USER") // "USER", "RESPONDER", "ADMIN"
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _firestoreUsers = MutableStateFlow<List<SafetyLinkUser>>(emptyList())
    val firestoreUsers: StateFlow<List<SafetyLinkUser>> = _firestoreUsers.asStateFlow()

    private val _firestoreLogs = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val firestoreLogs: StateFlow<List<Map<String, Any>>> = _firestoreLogs.asStateFlow()

    private val _emergencyContacts = mutableStateListOf<EmergencyContact>(
        EmergencyContact("Police Emergency Dispatch", "911"),
        EmergencyContact("Red Cross Rescue Ops", "112"),
        EmergencyContact("SafetyLink Central Command", "+1-800-555-0199"),
        EmergencyContact("Neighborhood Guard Team", "555-0144"),
        EmergencyContact("Family Emergency Circle", "555-0188")
    )
    val emergencyContacts: List<EmergencyContact> = _emergencyContacts

    private val _cloudRegistrants = mutableStateListOf<SafetyLinkUser>()
    val cloudRegistrants: List<SafetyLinkUser> = _cloudRegistrants

    private val _orgAlerts = mutableStateListOf<OrganizationAlert>()
    val orgAlerts: List<OrganizationAlert> = _orgAlerts

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // App state flows
    private val _isSimulatorMode = MutableStateFlow(true)
    val isSimulatorMode: StateFlow<Boolean> = _isSimulatorMode.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    // Telemetry flows
    private val _currentRssi = MutableStateFlow(-65)
    val currentRssi: StateFlow<Int> = _currentRssi.asStateFlow()

    private val _batteryLevel = MutableStateFlow(92)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _txPower = MutableStateFlow(-59)
    val txPower: StateFlow<Int> = _txPower.asStateFlow()

    private val _packetSuccessRate = MutableStateFlow(100.0)
    val packetSuccessRate: StateFlow<Double> = _packetSuccessRate.asStateFlow()

    private val _latencyMs = MutableStateFlow(45)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    // Alarm & SOS States
    private val _isSosAlarmActive = MutableStateFlow(false)
    val isSosAlarmActive: StateFlow<Boolean> = _isSosAlarmActive.asStateFlow()

    private val _sosTriggerReason = MutableStateFlow("")
    val sosTriggerReason: StateFlow<String> = _sosTriggerReason.asStateFlow()

    private val _isSoundActive = MutableStateFlow(false)
    val isSoundActive: StateFlow<Boolean> = _isSoundActive.asStateFlow()

    private val _isVibrationActive = MutableStateFlow(false)
    val isVibrationActive: StateFlow<Boolean> = _isVibrationActive.asStateFlow()

    // RSSI History (for charts, thread-safe access)
    val rssiHistory = mutableStateListOf<Int>()

    // Scanned Devices List (thread-safe, Compose-friendly)
    val scannedDevices = mutableStateListOf<BleDeviceItem>()

    // Logs (thread-safe, Compose-friendly)
    val logs = mutableStateListOf<LogEntry>()

    // Active connection references
    private var activeGatt: BluetoothGatt? = null

    // Audio synthesizer & Vibrator managers
    private var synthJob: Job? = null
    private var vibrationJob: Job? = null
    private var simulationTimerJob: Job? = null

    // For simulator state
    private var simulatedRssi = -65
    private var simulatedBattery = 92
    private var totalPacketsSimulated = 0
    private var successPacketsSimulated = 0

    init {
        // Initialize with default history points
        for (i in 1..25) {
            rssiHistory.add(-65)
        }
        addLog(LogLevel.INFO, "SafetyLink BLE Lab Initialized in SIMULATOR mode.")
        addLog(LogLevel.INFO, "Press 'Toggle Mode' to switch to physical BLE hardware.")
        
        // Start simulation loop (running continuously in background, active if simulator mode is true)
        startSimulationLoop()
    }

    fun submitGatekeeperDetails(email: String, orgId: String, chosenRole: String): Boolean {
        var finalEmail = email.trim()
        var finalOrgId = orgId.trim()
        var finalRole = chosenRole.uppercase()

        if (finalEmail.isBlank()) {
            val rand = (1000..9999).random()
            finalEmail = "operator_$rand@safetylink.org"
        }
        if (finalOrgId.isBlank()) {
            val rand = (100..999).random()
            finalOrgId = "SL-ORG-$rand"
        }

        // Special Admin master code override
        if (finalOrgId.equals("SL-ADMIN-000", ignoreCase = true)) {
            finalRole = "ADMIN"
            addLog(LogLevel.INFO, "⭐ ADMIN MASTER CONTROL ENGAGED: Bypassing gate rules.")
        }

        _currentUserEmail.value = finalEmail
        _currentUserOrgId.value = finalOrgId
        _currentUserRole.value = finalRole

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val userProfile = SafetyLinkUser(finalEmail, finalOrgId, finalRole, timeStr)

        // Local cache sync
        _registeredUsers.removeAll { it.email.lowercase() == finalEmail.lowercase() }
        _registeredUsers.add(userProfile)

        // Sync asynchronously to public cloud database (kvdb.io)
        syncUserToCloud(userProfile)

        _isUnlocked.value = true
        _loginError.value = null
        addLog(LogLevel.INFO, "🔓 Gate unlocked: $finalEmail as $finalRole (Org: $finalOrgId)")

        // Instantly poll cloud database for other users and alerts
        fetchCloudData()

        return true
    }

    fun syncUserToCloud(user: SafetyLinkUser) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val getRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_users")
                    .get()
                    .build()
                
                var currentList = mutableListOf<SafetyLinkUser>()
                client.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        if (json.isNotBlank() && json.startsWith("[")) {
                            currentList = parseUsersList(json)
                        }
                    }
                }
                
                currentList.removeAll { it.email.lowercase() == user.email.lowercase() }
                currentList.add(user)
                
                if (currentList.size > 100) {
                    currentList.removeAt(0)
                }

                val jsonBody = serializeUsersList(currentList)
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                val putRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_users")
                    .put(body)
                    .build()
                
                client.newCall(putRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        addLog(LogLevel.INFO, "☁️ Cloud profile sync completed.")
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.WARN, "☁️ Cloud profile sync offline: ${e.localizedMessage}")
            }
        }
    }

    fun syncAlertToCloud(email: String, role: String, reason: String, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val getRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_alerts")
                    .get()
                    .build()
                
                var currentList = mutableListOf<OrganizationAlert>()
                client.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        if (json.isNotBlank() && json.startsWith("[")) {
                            currentList = parseAlertsList(json)
                        }
                    }
                }
                
                currentList.removeAll { it.email.lowercase() == email.lowercase() }
                if (active) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date())
                    currentList.add(OrganizationAlert(email, role, reason, timeStr, true))
                }
                
                val jsonBody = serializeAlertsList(currentList)
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                val putRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_alerts")
                    .put(body)
                    .build()
                
                client.newCall(putRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        addLog(LogLevel.INFO, "☁️ Cloud Alert state synced.")
                    }
                }
            } catch (e: Exception) {
                // Ignore silent sync failures
            }
        }
    }

    fun fetchCloudData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                
                // Fetch Users
                val usersRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_users")
                    .get()
                    .build()
                
                client.newCall(usersRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        if (json.isNotBlank() && json.startsWith("[")) {
                            val list = parseUsersList(json)
                            withContext(Dispatchers.Main) {
                                _cloudRegistrants.clear()
                                _cloudRegistrants.addAll(list)
                            }
                        }
                    }
                }

                // Fetch Active Alerts
                val alertsRequest = okhttp3.Request.Builder()
                    .url("https://kvdb.io/8xXy8Xm6Y4bWv2RpHw3u2L/safetylink_alerts")
                    .get()
                    .build()
                
                client.newCall(alertsRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        if (json.isNotBlank() && json.startsWith("[")) {
                            val list = parseAlertsList(json)
                            withContext(Dispatchers.Main) {
                                _orgAlerts.clear()
                                _orgAlerts.addAll(list)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore silent errors
            }
        }
    }

    fun updateEmergencyContact(index: Int, name: String, phone: String) {
        if (index in 0 until _emergencyContacts.size) {
            _emergencyContacts[index] = EmergencyContact(name, phone)
            addLog(LogLevel.INFO, "Saved emergency contact #${index + 1}: $name ($phone)")
            saveContactsToFirestore()
        }
    }

    fun getSafeAuth(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun getSafeFirestore(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun registerWithFirebase(email: String, password: String, orgId: String, role: String, onResult: (Boolean, String?) -> Unit) {
        val auth = getSafeAuth()
        val db = getSafeFirestore()
        
        var finalEmail = email.trim()
        var finalOrgId = orgId.trim()
        var finalRole = role.uppercase()

        if (finalEmail.isBlank()) {
            onResult(false, "Email address cannot be blank")
            return
        }
        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters")
            return
        }

        // Special Admin master code override
        if (finalOrgId.equals("SL-ADMIN-000", ignoreCase = true)) {
            finalRole = "ADMIN"
        }

        if (auth != null) {
            auth.createUserWithEmailAndPassword(finalEmail, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: ""
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date())
                    val userProfile = SafetyLinkUser(finalEmail, finalOrgId, finalRole, timeStr)
                    
                    if (db != null) {
                        val profileData = mapOf(
                            "email" to finalEmail,
                            "orgId" to finalOrgId,
                            "role" to finalRole,
                            "timestamp" to timeStr
                        )
                        db.collection("users").document(uid).set(profileData)
                            .addOnSuccessListener {
                                addLog(LogLevel.INFO, "☁️ Registered Firebase profile for $finalEmail")
                                logStatusEventToFirestore(uid, finalEmail, "REGISTERED", "User created account")
                            }
                    }
                    
                    _currentUserEmail.value = finalEmail
                    _currentUserOrgId.value = finalOrgId
                    _currentUserRole.value = finalRole
                    
                    _registeredUsers.removeAll { it.email.lowercase() == finalEmail.lowercase() }
                    _registeredUsers.add(userProfile)
                    syncUserToCloud(userProfile)
                    
                    _isUnlocked.value = true
                    _loginError.value = null
                    
                    loadContactsFromFirestore()
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    onResult(false, e.localizedMessage)
                }
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timeStr = sdf.format(Date())
            val userProfile = SafetyLinkUser(finalEmail, finalOrgId, finalRole, timeStr)
            
            _currentUserEmail.value = finalEmail
            _currentUserOrgId.value = finalOrgId
            _currentUserRole.value = finalRole
            
            _registeredUsers.removeAll { it.email.lowercase() == finalEmail.lowercase() }
            _registeredUsers.add(userProfile)
            
            _isUnlocked.value = true
            _loginError.value = null
            addLog(LogLevel.INFO, "🔓 Local registration fallback active (Offline).")
            onResult(true, null)
        }
    }

    fun loginWithFirebase(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val auth = getSafeAuth()
        val db = getSafeFirestore()

        val finalEmail = email.trim()
        if (finalEmail.isBlank()) {
            onResult(false, "Email address cannot be blank")
            return
        }
        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters")
            return
        }

        if (auth != null) {
            auth.signInWithEmailAndPassword(finalEmail, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: ""
                    if (db != null) {
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    val orgId = doc.getString("orgId") ?: ""
                                    val role = doc.getString("role") ?: "USER"
                                    
                                    _currentUserEmail.value = finalEmail
                                    _currentUserOrgId.value = orgId
                                    _currentUserRole.value = role
                                    
                                    addLog(LogLevel.INFO, "🔓 Logged in: $finalEmail as $role")
                                    logStatusEventToFirestore(uid, finalEmail, "LOGIN", "Successfully logged in")
                                    
                                    _isUnlocked.value = true
                                    _loginError.value = null
                                    
                                    loadContactsFromFirestore()
                                    onResult(true, null)
                                } else {
                                    _currentUserEmail.value = finalEmail
                                    _currentUserOrgId.value = "SL-ORG-DEFAULT"
                                    _currentUserRole.value = "USER"
                                    _isUnlocked.value = true
                                    _loginError.value = null
                                    onResult(true, null)
                                }
                            }
                            .addOnFailureListener {
                                _currentUserEmail.value = finalEmail
                                _currentUserOrgId.value = "SL-ORG-DEFAULT"
                                _currentUserRole.value = "USER"
                                _isUnlocked.value = true
                                _loginError.value = null
                                onResult(true, null)
                            }
                    } else {
                        _currentUserEmail.value = finalEmail
                        _currentUserOrgId.value = "SL-ORG-DEFAULT"
                        _currentUserRole.value = "USER"
                        _isUnlocked.value = true
                        _loginError.value = null
                        onResult(true, null)
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, e.localizedMessage)
                }
        } else {
            _currentUserEmail.value = finalEmail
            _currentUserOrgId.value = "SL-ORG-DEFAULT"
            _currentUserRole.value = "USER"
            _isUnlocked.value = true
            _loginError.value = null
            addLog(LogLevel.INFO, "🔓 Local login fallback active (Offline).")
            onResult(true, null)
        }
    }

    fun saveContactsToFirestore() {
        val auth = getSafeAuth()
        val db = getSafeFirestore()
        val currentUser = auth?.currentUser
        if (currentUser != null && db != null) {
            val contactsList = _emergencyContacts.map { mapOf("name" to it.name, "phone" to it.phone) }
            val data = mapOf(
                "email" to currentUser.email,
                "contacts" to contactsList
            )
            db.collection("emergency_contacts").document(currentUser.uid)
                .set(data)
                .addOnSuccessListener {
                    addLog(LogLevel.INFO, "☁️ Saved emergency contacts to Firestore successfully.")
                }
                .addOnFailureListener { e ->
                    addLog(LogLevel.WARN, "⚠️ Firestore contact sync failed: ${e.message}")
                }
        } else {
            addLog(LogLevel.INFO, "💾 Saved emergency contacts locally (Offline Mode).")
        }
    }

    fun loadContactsFromFirestore() {
        val auth = getSafeAuth()
        val db = getSafeFirestore()
        val currentUser = auth?.currentUser
        if (currentUser != null && db != null) {
            db.collection("emergency_contacts").document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val list = doc.get("contacts") as? List<Map<String, String>>
                        if (list != null) {
                            _emergencyContacts.clear()
                            list.forEach { item ->
                                val name = item["name"] ?: ""
                                val phone = item["phone"] ?: ""
                                if (name.isNotBlank() || phone.isNotBlank()) {
                                    _emergencyContacts.add(EmergencyContact(name, phone))
                                }
                            }
                            addLog(LogLevel.INFO, "☁️ Loaded emergency contacts from Firestore.")
                        }
                    }
                }
        }
    }

    fun logStatusEventToFirestore(uid: String, email: String, status: String, details: String) {
        val db = getSafeFirestore() ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val logData = mapOf(
            "email" to email,
            "status" to status,
            "details" to details,
            "timestamp" to timeStr
        )
        db.collection("status_logs").add(logData)
    }

    fun fetchAdminData() {
        val db = getSafeFirestore() ?: return
        
        db.collection("users").get()
            .addOnSuccessListener { result ->
                val list = mutableListOf<SafetyLinkUser>()
                for (doc in result) {
                    val email = doc.getString("email") ?: ""
                    val orgId = doc.getString("orgId") ?: ""
                    val role = doc.getString("role") ?: "USER"
                    val ts = doc.getString("timestamp") ?: ""
                    list.add(SafetyLinkUser(email, orgId, role, ts))
                }
                _firestoreUsers.value = list
            }
        
        db.collection("status_logs").get()
            .addOnSuccessListener { result ->
                val list = mutableListOf<Map<String, Any>>()
                for (doc in result) {
                    val email = doc.getString("email") ?: ""
                    val status = doc.getString("status") ?: ""
                    val details = doc.getString("details") ?: ""
                    val ts = doc.getString("timestamp") ?: ""
                    list.add(mapOf(
                        "email" to email,
                        "status" to status,
                        "details" to details,
                        "timestamp" to ts
                    ))
                }
                list.sortByDescending { it["timestamp"] as? String ?: "" }
                _firestoreLogs.value = list
            }
    }

    fun sendSmsAlert(phone: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, message, null, null)
            addLog(LogLevel.INFO, "✉️ SMS successfully transmitted to $phone")
        } catch (e: Exception) {
            addLog(LogLevel.WARN, "⚠️ SMS dispatch failed for $phone: ${e.message}")
        }
    }

    fun initiateCall(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            addLog(LogLevel.INFO, "📞 Automatically initiated call to $phone")
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                addLog(LogLevel.INFO, "📞 Opened system dialer for $phone")
            } catch (ex: Exception) {
                addLog(LogLevel.WARN, "⚠️ Call trigger failed: ${ex.message}")
            }
        }
    }

    fun openWhatsAppAlert(phone: String, message: String) {
        try {
            val url = "https://api.whatsapp.com/send?phone=${phone.replace("+", "")}&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            addLog(LogLevel.INFO, "💬 Redirected to WhatsApp dispatch for $phone")
        } catch (e: Exception) {
            addLog(LogLevel.WARN, "⚠️ WhatsApp dispatch redirect failed: ${e.message}")
        }
    }

    fun executeAutotextSOSDispatches(reason: String) {
        val message = "🚨 SAFETYLINK SOS ALERT: Operator ${_currentUserEmail.value} triggered distress! Reason: $reason"
        
        viewModelScope.launch(Dispatchers.Main) {
            _emergencyContacts.forEachIndexed { index, contact ->
                if (contact.phone.isNotBlank()) {
                    addLog(LogLevel.INFO, "Executing Autotext dispatcher for contact #${index + 1}: ${contact.name}")
                    
                    // 1. Send SMS
                    sendSmsAlert(contact.phone, message)
                    delay(200)
                    
                    // 2. Open WhatsApp for the first/primary contact
                    if (index == 0) {
                        openWhatsAppAlert(contact.phone, message)
                    }
                    
                    // 3. Initiate call automatically for the first contact
                    if (index == 0) {
                        initiateCall(contact.phone)
                    }
                }
            }
        }
    }

    fun serializeUsersList(list: List<SafetyLinkUser>): String {
        val sb = StringBuilder()
        sb.append("[")
        list.forEachIndexed { index, user ->
            sb.append("{")
            sb.append("\"email\":\"${user.email.replace("\"", "\\\"")}\",")
            sb.append("\"orgId\":\"${user.orgId.replace("\"", "\\\"")}\",")
            sb.append("\"role\":\"${user.role}\",")
            sb.append("\"timestamp\":\"${user.timestamp}\"")
            sb.append("}")
            if (index < list.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    fun parseUsersList(json: String): MutableList<SafetyLinkUser> {
        val list = mutableListOf<SafetyLinkUser>()
        val pattern = java.util.regex.Pattern.compile("\\{\\s*\"email\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"orgId\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"role\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"timestamp\"\\s*:\\s*\"([^\"]*)\"\\s*\\}")
        val matcher = pattern.matcher(json)
        while (matcher.find()) {
            val email = matcher.group(1) ?: ""
            val orgId = matcher.group(2) ?: ""
            val role = matcher.group(3) ?: ""
            val timestamp = matcher.group(4) ?: ""
            list.add(SafetyLinkUser(email, orgId, role, timestamp))
        }
        return list
    }

    fun serializeAlertsList(list: List<OrganizationAlert>): String {
        val sb = StringBuilder()
        sb.append("[")
        list.forEachIndexed { index, alert ->
            sb.append("{")
            sb.append("\"email\":\"${alert.email.replace("\"", "\\\"")}\",")
            sb.append("\"role\":\"${alert.role}\",")
            sb.append("\"reason\":\"${alert.reason.replace("\"", "\\\"")}\",")
            sb.append("\"timestamp\":\"${alert.timestamp}\",")
            sb.append("\"active\":${alert.active}")
            sb.append("}")
            if (index < list.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    fun parseAlertsList(json: String): MutableList<OrganizationAlert> {
        val list = mutableListOf<OrganizationAlert>()
        val pattern = java.util.regex.Pattern.compile("\\{\\s*\"email\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"role\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"reason\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"timestamp\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"active\"\\s*:\\s*(true|false)\\s*\\}")
        val matcher = pattern.matcher(json)
        while (matcher.find()) {
            val email = matcher.group(1) ?: ""
            val role = matcher.group(2) ?: ""
            val reason = matcher.group(3) ?: ""
            val timestamp = matcher.group(4) ?: ""
            val active = matcher.group(5)?.toBoolean() ?: false
            list.add(OrganizationAlert(email, role, reason, timestamp, active))
        }
        return list
    }

    // Keep legacy register and login methods to avoid any build failures
    fun registerNewUser(email: String, orgId: String): Boolean {
        return submitGatekeeperDetails(email, orgId, "USER")
    }

    fun verifyAndLogin(email: String, orgId: String): Boolean {
        return submitGatekeeperDetails(email, orgId, "USER")
    }

    fun logOut() {
        _isUnlocked.value = false
        addLog(LogLevel.INFO, "🔒 Session closed.")
    }

    fun linkDeviceManually(address: String, name: String) {
        if (address.isBlank() || name.isBlank()) {
            addLog(LogLevel.WARN, "Manual link failed: address or name cannot be empty.")
            return
        }
        
        val formattedAddress = address.trim().uppercase()
        val formattedName = name.trim()

        addLog(LogLevel.INFO, "Manually linking device: $formattedName [$formattedAddress]")
        
        if (_isSimulatorMode.value) {
            _connectedDeviceName.value = formattedName
            _connectedDeviceAddress.value = formattedAddress
            _connectionState.value = DeviceConnectionState.CONNECTED
            addLog(LogLevel.INFO, "Simulated Device manually linked: $formattedName [$formattedAddress]")
            
            // Simulate GATT Services Discovery
            viewModelScope.launch {
                delay(800)
                _connectionState.value = DeviceConnectionState.DISCOVERED_SERVICES
                addLog(LogLevel.INFO, "Discovered Simulator services for manually linked device:")
                addLog(LogLevel.INFO, " -> Immediate Alert Service (0x1802)")
                addLog(LogLevel.INFO, " -> Simple Key Press Service (0xFFE0)")
                addLog(LogLevel.INFO, "Successfully subscribed to simple key click listener.")
            }
        } else {
            connectPhysicalDevice(formattedAddress, formattedName)
        }
    }

    fun setSimulatorMode(enabled: Boolean) {
        _isSimulatorMode.value = enabled
        if (enabled) {
            disconnectActiveGatt()
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            _connectedDeviceName.value = "SIMULATED_TAG"
            _connectedDeviceAddress.value = "AA:BB:CC:DD:EE:FF"
            addLog(LogLevel.INFO, "Switched to Simulator Mode.")
        } else {
            _connectedDeviceName.value = null
            _connectedDeviceAddress.value = null
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            addLog(LogLevel.INFO, "Switched to Physical BLE hardware mode. Permissions and BLE scan required.")
        }
    }

    fun addLog(level: LogLevel, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timestamp = sdf.format(Date())
        viewModelScope.launch(Dispatchers.Main) {
            if (logs.size > 200) {
                logs.removeAt(0)
            }
            logs.add(LogEntry(timestamp, level, message))
        }
    }

    fun clearLogs() {
        logs.clear()
        addLog(LogLevel.INFO, "Logs cleared.")
    }

    fun exportLogsText(): String {
        val builder = StringBuilder()
        builder.append("=== SAFETYLINK BLE LAB LOGS ===\n")
        builder.append("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        logs.forEach {
            builder.append("[${it.timestamp}] [${it.level.name}] ${it.message}\n")
        }
        return builder.toString()
    }

    // --- SOUND SYNTHESIS LOOP ---
    private fun startAudioSynthesizer() {
        if (synthJob != null) return
        _isSoundActive.value = true
        synthJob = viewModelScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioTrack: AudioTrack
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .build()
            } catch (e: Exception) {
                addLog(LogLevel.DANGER, "Failed to build AudioTrack: ${e.message}")
                _isSoundActive.value = false
                return@launch
            }

            audioTrack.play()

            val duration = 0.25 // seconds duration of tone beep
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)
            val freqHigh = 950.0 // higher tactical tone
            val freqLow = 650.0

            while (this.isActive && _isSoundActive.value) {
                // Synthesize distress siren: high beep
                for (i in 0 until numSamples) {
                    sample[i] = (Math.sin(2 * Math.PI * i * freqHigh / sampleRate) * 16383).toInt().toShort()
                }
                audioTrack.write(sample, 0, sample.size)
                delay(120)

                if (!this.isActive || !_isSoundActive.value) break

                // Low beep
                for (i in 0 until numSamples) {
                    sample[i] = (Math.sin(2 * Math.PI * i * freqLow / sampleRate) * 16383).toInt().toShort()
                }
                audioTrack.write(sample, 0, sample.size)
                delay(400) // delay between chirps
            }

            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun stopAudioSynthesizer() {
        _isSoundActive.value = false
        synthJob?.cancel()
        synthJob = null
    }

    // --- VIBRATION CONTROLLER ---
    private fun startVibrationLoop() {
        if (vibrationJob != null) return
        _isVibrationActive.value = true
        vibrationJob = viewModelScope.launch(Dispatchers.Default) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                addLog(LogLevel.WARN, "Physical device vibration is not available.")
                _isVibrationActive.value = false
                return@launch
            }

            val pattern = longArrayOf(0, 300, 150, 300, 150, 500) // S-O-S-like short burst
            while (this.isActive && _isVibrationActive.value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
                delay(1500) // repeat interval
            }
        }
    }

    private fun stopVibrationLoop() {
        _isVibrationActive.value = false
        vibrationJob?.cancel()
        vibrationJob = null
    }

    // --- TRIGGERS FOR SOS / PANEL ---
    fun triggerSos(reason: String) {
        if (_isSosAlarmActive.value) return
        _isSosAlarmActive.value = true
        _sosTriggerReason.value = reason
        addLog(LogLevel.DANGER, "🔴 ALARM TRIGGERED: $reason")
        
        // Start physical sirens & vibration
        startAudioSynthesizer()
        startVibrationLoop()

        // Sync Alert State to Cloud
        syncAlertToCloud(_currentUserEmail.value, _currentUserRole.value, reason, active = true)

        // Save Status Event in Firestore
        val auth = getSafeAuth()
        val currentUser = auth?.currentUser
        if (currentUser != null) {
            logStatusEventToFirestore(currentUser.uid, _currentUserEmail.value, "DISTRESS", reason)
        }

        // Execute Autotext Dispatchers
        executeAutotextSOSDispatches(reason)
    }

    fun resetSosAlarm() {
        if (!_isSosAlarmActive.value) return
        _isSosAlarmActive.value = false
        _sosTriggerReason.value = ""
        addLog(LogLevel.INFO, "🟢 Emergency Alarm manually reset & cleared.")
        stopAudioSynthesizer()
        stopVibrationLoop()

        // Sync Alert State to Cloud
        syncAlertToCloud(_currentUserEmail.value, _currentUserRole.value, "", active = false)

        // Save Status Event in Firestore
        val auth = getSafeAuth()
        val currentUser = auth?.currentUser
        if (currentUser != null) {
            logStatusEventToFirestore(currentUser.uid, _currentUserEmail.value, "SAFE", "Alarm cleared")
        }
    }

    // --- SIMULATOR MODE ENGINE ---
    private fun startSimulationLoop() {
        simulationTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_isSimulatorMode.value) {
                    // Fluctuate RSSI slightly (-55 to -95)
                    val change = (-3..3).random()
                    simulatedRssi = (simulatedRssi + change).coerceIn(-100, -40)
                    _currentRssi.value = simulatedRssi

                    // Battery drops slowly
                    if (Math.random() < 0.05) {
                        simulatedBattery = (simulatedBattery - 1).coerceIn(1, 100)
                        _batteryLevel.value = simulatedBattery
                    }

                    // Simulated RSSI dropping below danger line trigger
                    if (simulatedRssi <= -90) {
                        triggerSos("Out-of-Range Simulation (RSSI: ${simulatedRssi}dBm)")
                    }

                    // Simulated packet calculation
                    totalPacketsSimulated++
                    val lossRate = when {
                        simulatedRssi > -70 -> 0.01
                        simulatedRssi > -80 -> 0.05
                        simulatedRssi > -90 -> 0.15
                        else -> 0.45
                    }
                    if (Math.random() > lossRate) {
                        successPacketsSimulated++
                    }
                    _packetSuccessRate.value = (successPacketsSimulated.toDouble() / totalPacketsSimulated.toDouble()) * 100.0

                    // Latency based on distance/RSSI
                    _latencyMs.value = (30 + (Math.abs(simulatedRssi + 40) * 1.5) + (-5..5).random()).toInt().coerceIn(15, 300)

                    // Add to RSSI History
                    withContext(Dispatchers.Main) {
                        if (rssiHistory.size >= 40) {
                            rssiHistory.removeAt(0)
                        }
                        rssiHistory.add(simulatedRssi)
                    }
                }
                delay(1000) // 1 second ticker
            }
        }
    }

    // Simulation Manual Controls
    fun simAdjustRssi(value: Int) {
        if (!_isSimulatorMode.value) return
        simulatedRssi = value
        _currentRssi.value = value
        addLog(LogLevel.INFO, "Simulated RSSI adjusted manually to: ${value} dBm")
        if (value <= -90) {
            triggerSos("Simulated Out-of-Range (RSSI threshold exceeded: ${value}dBm)")
        }
    }

    fun simAdjustBattery(value: Int) {
        if (!_isSimulatorMode.value) return
        simulatedBattery = value
        _batteryLevel.value = value
        addLog(LogLevel.INFO, "Simulated Battery adjusted manually to: ${value}%")
    }

    fun simTriggerHardwareButtonPress() {
        if (!_isSimulatorMode.value) return
        addLog(LogLevel.WARN, "Simulating physical button click on simulated iTag wearable!")
        triggerSos("Simulated Hardware Button Click")
    }

    fun simDisconnect() {
        if (!_isSimulatorMode.value) return
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        addLog(LogLevel.WARN, "Simulating physical drop/disconnection.")
        triggerSos("Simulated Link-Loss Disconnection")
    }

    fun simConnect() {
        if (!_isSimulatorMode.value) return
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        delayAndConnectSim()
    }

    private fun delayAndConnectSim() {
        viewModelScope.launch {
            _connectionState.value = DeviceConnectionState.CONNECTING
            addLog(LogLevel.INFO, "Connecting to simulated wearable tag...")
            delay(1200)
            _connectionState.value = DeviceConnectionState.CONNECTED
            addLog(LogLevel.INFO, "Connected successfully to: SIMULATED_TAG [AA:BB:CC:DD:EE:FF]")
            delay(800)
            _connectionState.value = DeviceConnectionState.DISCOVERED_SERVICES
            addLog(LogLevel.INFO, "Discovered Simulator services:")
            addLog(LogLevel.INFO, " -> Immediate Alert Service (0x1802)")
            addLog(LogLevel.INFO, " -> Simple Key Press Service (0xFFE0)")
            addLog(LogLevel.INFO, "Successfully subscribed to simple key click listener.")
        }
    }

    // --- PHYSICAL BLE HARDWARE ENGINE ---
    @SuppressLint("MissingPermission")
    fun startPhysicalScan() {
        if (_isSimulatorMode.value) {
            addLog(LogLevel.WARN, "Please disable Simulator Mode to perform a physical BLE scan.")
            return
        }

        if (bluetoothAdapter == null) {
            addLog(LogLevel.DANGER, "Bluetooth hardware adapter is not available on this device.")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            addLog(LogLevel.WARN, "Bluetooth is disabled. Please turn on Bluetooth in device settings.")
            return
        }

        if (_isScanning.value) return

        scannedDevices.clear()
        _isScanning.value = true
        addLog(LogLevel.INFO, "Started scanning for BLE Devices (looking for iTags/wearables)...")

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            addLog(LogLevel.DANGER, "Bluetooth LE Scanner could not be initialized.")
            _isScanning.value = false
            return
        }

        // Auto-stop scanning after 12 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (_isScanning.value) {
                stopPhysicalScan()
            }
        }, 12000)

        try {
            scanner.startScan(scanCallback)
        } catch (e: Exception) {
            addLog(LogLevel.DANGER, "Failed to start BLE Scan: ${e.localizedMessage}")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPhysicalScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        addLog(LogLevel.INFO, "BLE scan finished/stopped.")
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignore stop scanning errors
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result == null || result.device == null) return

            val deviceName = result.device.name ?: "Unknown Device"
            val address = result.device.address
            val rssi = result.rssi

            viewModelScope.launch(Dispatchers.Main) {
                val index = scannedDevices.indexOfFirst { it.address == address }
                if (index != -1) {
                    scannedDevices[index] = BleDeviceItem(address, deviceName, rssi, System.currentTimeMillis())
                } else {
                    scannedDevices.add(BleDeviceItem(address, deviceName, rssi))
                    addLog(LogLevel.INFO, "Discovered device: $deviceName [$address] RSSI: $rssi dBm")
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            addLog(LogLevel.DANGER, "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectPhysicalDevice(deviceAddress: String, deviceName: String) {
        if (bluetoothAdapter == null) return
        disconnectActiveGatt()

        addLog(LogLevel.INFO, "Attempting to connect to physical device $deviceName [$deviceAddress]...")
        _connectionState.value = DeviceConnectionState.CONNECTING
        _connectedDeviceAddress.value = deviceAddress
        _connectedDeviceName.value = deviceName

        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        activeGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnectActiveGatt() {
        activeGatt?.let { gatt ->
            try {
                @SuppressLint("MissingPermission")
                gatt.disconnect()
                @SuppressLint("MissingPermission")
                gatt.close()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
        activeGatt = null
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _connectedDeviceAddress.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (gatt == null) return

            val address = gatt.device.address
            val name = gatt.device.name ?: "iTag/Wearable"

            if (status != BluetoothGatt.GATT_SUCCESS) {
                addLog(LogLevel.DANGER, "GATT Connection error status: $status. Disconnecting...")
                disconnectActiveGatt()
                triggerSos("Wearable Connection Disconnected (GATT status: $status)")
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = DeviceConnectionState.CONNECTED
                addLog(LogLevel.INFO, "Successfully connected to $name [$address]. Discovering services...")
                // Discover services on connected device
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectActiveGatt()
                addLog(LogLevel.WARN, "Physical device disconnected.")
                triggerSos("Wearable Link-Loss Disconnection ($name)")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (gatt == null) return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = DeviceConnectionState.DISCOVERED_SERVICES
                addLog(LogLevel.INFO, "Services successfully discovered. Inspecting services:")

                var notificationSubscribed = false

                // Print services to live debug console and try to register simple key listener
                gatt.services.forEach { service ->
                    addLog(LogLevel.INFO, "Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        val properties = getCharPropertiesString(char.properties)
                        addLog(LogLevel.INFO, " -> Char: ${char.uuid} ($properties)")

                        // Let's listen to ANY characteristics that support NOTIFY or INDICATE.
                        // Or if the UUID matches standard iTag buttons: SIMPLE_KEY_CHAR_UUID or similar.
                        val isSimpleKey = char.uuid == SIMPLE_KEY_CHAR_UUID || 
                                          char.uuid.toString().lowercase().contains("ffe1") || 
                                          char.uuid.toString().lowercase().contains("ffe0")

                        val hasNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

                        if ((isSimpleKey || hasNotify) && !notificationSubscribed) {
                            // Subscribing to this notification!
                            gatt.setCharacteristicNotification(char, true)
                            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                                notificationSubscribed = true
                                addLog(LogLevel.WARN, "Locked simple key: Subscribed to notifications on characteristic ${char.uuid}")
                            }
                        }
                    }
                }

                if (!notificationSubscribed) {
                    addLog(LogLevel.WARN, "No compatible notify/key press service found. Looking for alert triggers.")
                }
            } else {
                addLog(LogLevel.DANGER, "Failed to discover device services (GATT code: $status)")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            @Suppress("DEPRECATION")
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic == null) return
            
            val value = characteristic.value
            val hexString = value?.joinToString("") { String.format("%02X", it) } ?: "Empty"
            addLog(LogLevel.WARN, "🚨 iTag Physical Button Pressed! Notification received on characteristic ${characteristic.uuid}. Payload: 0x$hexString")
            triggerSos("iTag Hardware Button Press [Payload: 0x$hexString]")
        }

        // Support for newer API 33+ method for callback stability
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val hexString = value.joinToString("") { String.format("%02X", it) }
            addLog(LogLevel.WARN, "🚨 iTag Physical Button Pressed! Notification received on characteristic ${characteristic.uuid}. Payload: 0x$hexString")
            triggerSos("iTag Hardware Button Press [Payload: 0x$hexString]")
        }

        @Deprecated("Deprecated in Java")
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            @Suppress("DEPRECATION")
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog(LogLevel.INFO, "Success writing configuration descriptor: Subscribed to button click broadcast.")
            } else {
                addLog(LogLevel.DANGER, "Failed to write descriptor to subscribe. Code: $status")
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _currentRssi.value = rssi
                // Add to history
                viewModelScope.launch(Dispatchers.Main) {
                    if (rssiHistory.size >= 40) {
                        rssiHistory.removeAt(0)
                    }
                    rssiHistory.add(rssi)
                }

                // If rssi is dangerous, trigger distress SOS
                if (rssi <= -90) {
                    triggerSos("Physical Out-of-Range (RSSI: ${rssi}dBm)")
                }
            }
        }
    }

    private fun getCharPropertiesString(properties: Int): String {
        val list = mutableListOf<String>()
        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) list.add("READ")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list.add("WRITE")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list.add("NOTIFY")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list.add("INDICATE")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) list.add("WRITE_NO_RESP")
        return list.joinToString("|")
    }

    // Helper for pulling RSSI on physical connections periodically
    fun pollPhysicalRssi() {
        activeGatt?.let { gatt ->
            try {
                @SuppressLint("MissingPermission")
                gatt.readRemoteRssi()
            } catch (e: Exception) {
                // Ignore RSSI read errors
            }
        }
    }

    // Clean up synth/vib/connections on cleared
    override fun onCleared() {
        super.onCleared()
        disconnectActiveGatt()
        stopAudioSynthesizer()
        stopVibrationLoop()
        simulationTimerJob?.cancel()
    }
}
