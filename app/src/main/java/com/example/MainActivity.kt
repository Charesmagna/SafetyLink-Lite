package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                val viewModel: BleLabViewModel = viewModel()
                val isUnlocked by viewModel.isUnlocked.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0D1117)) // Deep slate tactical bg
                            .padding(innerPadding)
                    ) {
                        if (isUnlocked) {
                            SafetyLinkBleLabScreen(viewModel = viewModel)
                        } else {
                            SafetyLinkGatekeeperScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyLinkBleLabScreen(
    viewModel: BleLabViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isSimulatorMode by viewModel.isSimulatorMode.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()

    // Telemetry
    val currentRssi by viewModel.currentRssi.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val txPower by viewModel.txPower.collectAsState()
    val packetSuccessRate by viewModel.packetSuccessRate.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()

    // Alerts
    val isSosActive by viewModel.isSosAlarmActive.collectAsState()
    val sosTriggerReason by viewModel.sosTriggerReason.collectAsState()
    val isSoundActive by viewModel.isSoundActive.collectAsState()
    val isVibrationActive by viewModel.isVibrationActive.collectAsState()

    // Current Operator Profile
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()
    val currentUserOrgId by viewModel.currentUserOrgId.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val cloudRegistrants = viewModel.cloudRegistrants
    val orgAlerts = viewModel.orgAlerts

    var selectedTab by remember { mutableStateOf(0) } // 0 = HOME, 1 = SETTINGS

    // Scanned device permissions helper
    var hasBlePermissions by remember { mutableStateOf(checkPermissions(context)) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        hasBlePermissions = granted
        if (granted) {
            viewModel.addLog(LogLevel.INFO, "BLE permissions granted successfully by user.")
            Toast.makeText(context, "BLE Permissions Granted", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addLog(LogLevel.DANGER, "BLE Permissions denied. Switched to Simulator mode.")
            viewModel.setSimulatorMode(true)
        }
    }

    // Auto-update RSSI periodically if physically connected
    LaunchedEffect(connectionState) {
        if (connectionState == DeviceConnectionState.DISCOVERED_SERVICES) {
            while (true) {
                viewModel.pollPhysicalRssi()
                delay(1200)
            }
        }
    }

    // Periodic cloud poll for team updates
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.fetchCloudData()
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 1. TACTICAL OPERATOR PROFILE HEADER
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (currentUserRole) {
                            "ADMIN" -> "⭐"
                            "RESPONDER" -> "🚒"
                            else -> "👤"
                        },
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = currentUserEmail.ifBlank { "Unregistered Operator" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ROLE: $currentUserRole",
                                color = when (currentUserRole) {
                                    "ADMIN" -> Color(0xFFDA3633)
                                    "RESPONDER" -> Color(0xFF2EA44F)
                                    else -> Color(0xFF58A6FF)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "ORG: $currentUserOrgId",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                Button(
                    onClick = {
                        viewModel.fetchCloudData()
                        Toast.makeText(context, "Cloud status synchronized!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("🔄 SYNC", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. TAB SELECTION ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) Color(0xFF21262D) else Color.Transparent
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "🏠 HOME",
                    color = if (selectedTab == 0) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 1) Color(0xFF21262D) else Color.Transparent
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "⚙️ SETTINGS",
                    color = if (selectedTab == 1) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. TAB CONTENT VIEWS
        if (selectedTab == 0) {
            // ==================== HOME TAB ====================
            when (currentUserRole) {
                "USER" -> {
                    // ---- SIMPLE SENIOR/CHILD FRIENDLY USER HOME ----
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Flashing alert overlay if distress active
                        if (isSosActive) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDA3633)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🚨 DISTRESS BEACON ACTIVE 🚨", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Emergency services and responders have been alerted.", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.resetSosAlarm() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("CANCEL PANIC", color = Color(0xFFDA3633), fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Massive Panic button
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(Color(0xFFDA3633).copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                        .border(BorderStroke(2.dp, Color(0xFFDA3633)), RoundedCornerShape(14.dp))
                                        .clickable { viewModel.triggerSos("USER_PANIC_TRIGGER") }
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🚨", fontSize = 56.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "TAP TO CALL HELP",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Press in case of danger or medical emergency",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Connected Tracker Status Indicator
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (connectionState == DeviceConnectionState.DISCOVERED_SERVICES || (isSimulatorMode && connectionState == DeviceConnectionState.CONNECTED)) "🟢" else "🔴",
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (connectionState == DeviceConnectionState.DISCOVERED_SERVICES || (isSimulatorMode && connectionState == DeviceConnectionState.CONNECTED)) {
                                                "TRACKER IS SAFE & LINKED"
                                            } else {
                                                "TRACKER DISCONNECTED"
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (isSimulatorMode) "Simulator active (Simulating safe state)" else "Hardware: ${connectedDeviceName ?: "Scanning keys..."}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Large Emergency Contacts List
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "📞 5 EMERGENCY CONTACTS",
                                        color = Color(0xFF58A6FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    viewModel.emergencyContacts.forEach { contact ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    Toast.makeText(context, "Calling ${contact.name} (${contact.phone})...", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(contact.phone, color = Color.LightGray, fontSize = 12.sp)
                                            }
                                            Text("📞", fontSize = 20.sp)
                                        }
                                        HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        // Dispatch Simulator logs info
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("📡 LIVE SAFETYLINK DISPATCH STATUS", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isSosActive) {
                                            "🚨 TRANSMITTING: Distress broadcasting live packets to cloud logs..."
                                        } else {
                                            "🟢 ARMED: SOS alarm is quiet. System waiting for iTag hardware click."
                                        },
                                        color = if (isSosActive) Color(0xFFF85149) else Color(0xFF3FB950),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                "RESPONDER" -> {
                    // ---- RESPONDER HOME (DISTRESS MONITOR) ----
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "📡 INCOMING EMERGENCY BEACON DISPATCH",
                                color = Color(0xFF2EA44F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val activeAlerts = orgAlerts.filter { it.active }
                        if (activeAlerts.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E281F)),
                                    border = BorderStroke(1.dp, Color(0xFF2EA44F).copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🟢 ALL STATIONS CLEAR", color = Color(0xFF56D364), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("No active distress beacons registered in your organization.", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        } else {
                            items(activeAlerts) { alert ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDA3633).copy(alpha = 0.15f)),
                                    border = BorderStroke(1.5.dp, Color(0xFFDA3633)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("⚠️ ACTIVE DISTRESS ALERT", color = Color(0xFFF85149), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                            Text("🚨 RESPOND NOW", color = Color(0xFFF85149), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("User: ${alert.email}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Role: ${alert.role} | Org: $currentUserOrgId", color = Color.LightGray, fontSize = 12.sp)
                                        Text("Reason: ${alert.reason}", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        Text("Timestamp: ${alert.timestamp}", color = Color.Gray, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    Toast.makeText(context, "Responding dispatch logged. SMS routed to operator.", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                                            ) {
                                                Text("MARK RESPONDING 🏃‍♂️", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Broadcast responder telemetry test
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("🎛️ RESPONDER ALARM TEST CONTROLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.triggerSos("RESPONDER_SIREN_TEST") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633))
                                        ) {
                                            Text("TEST SIREN 🔊", fontSize = 11.sp, color = Color.White)
                                        }
                                        Button(
                                            onClick = { viewModel.resetSosAlarm() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                        ) {
                                            Text("MUTE SIREN 🔕", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "ADMIN" -> {
                    // ---- MASTER ADMIN MODE: FULL TECHNICAL TELETRIAL DIAGNOSTICS PANEL ----
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "⭐ ADMINISTRATIVE FULL APP CONTROL PANEL",
                                color = Color(0xFFF1E05A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Firestore Users & Status Logs Trace Component
                        item {
                            val firestoreUsers by viewModel.firestoreUsers.collectAsState()
                            val firestoreLogs by viewModel.firestoreLogs.collectAsState()
                            
                            LaunchedEffect(Unit) {
                                viewModel.fetchAdminData()
                            }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🔥 FIRESTORE REGISTERED USERS",
                                            color = Color(0xFFE56E24),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Button(
                                            onClick = { viewModel.fetchAdminData() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("🔄 REFRESH", color = Color.White, fontSize = 9.sp)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    if (firestoreUsers.isEmpty()) {
                                        Text("No users found in Firestore. Try registering or check connection.", color = Color.LightGray, fontSize = 11.sp)
                                    } else {
                                        firestoreUsers.forEach { user ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(user.email, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("Registered: ${user.timestamp}", color = Color.Gray, fontSize = 10.sp)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(user.role, color = Color(0xFF56D364), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text("Code: ${user.orgId}", color = Color.LightGray, fontSize = 10.sp)
                                                }
                                            }
                                            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "📋 FIRESTORE DISTRESS & STATUS LOGS",
                                        color = Color(0xFFE56E24),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    if (firestoreLogs.isEmpty()) {
                                        Text("No status logs recorded in Firestore yet.", color = Color.LightGray, fontSize = 11.sp)
                                    } else {
                                        firestoreLogs.forEach { log ->
                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(log["email"] as? String ?: "", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text(
                                                        text = log["status"] as? String ?: "",
                                                        color = if (log["status"] == "DISTRESS") Color(0xFFF85149) else Color(0xFF56D364),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Text("Details: ${log["details"] as? String ?: ""}", color = Color.White, fontSize = 11.sp)
                                                Text("Time: ${log["timestamp"] as? String ?: ""}", color = Color.Gray, fontSize = 9.sp)
                                            }
                                            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Live Sync cloud registrants database tracker
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "☁️ ORG USERS CLOUD REGISTRY",
                                            color = Color(0xFF58A6FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${cloudRegistrants.size} Registrants",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (cloudRegistrants.isEmpty()) {
                                        Text("No online registrants fetched yet. Sync to load database.", color = Color.LightGray, fontSize = 11.sp)
                                    } else {
                                        cloudRegistrants.forEach { user ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(user.email, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("Registered: ${user.timestamp}", color = Color.Gray, fontSize = 10.sp)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(user.role, color = Color(0xFF56D364), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text("Code: ${user.orgId}", color = Color.LightGray, fontSize = 10.sp)
                                                }
                                            }
                                            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Original Simulator control card or Physical Scanner Card
                        item {
                            if (isSimulatorMode) {
                                SimulatorControlCard(
                                    currentRssi = currentRssi,
                                    batteryLevel = batteryLevel,
                                    connectionState = connectionState,
                                    onRssiChange = { viewModel.simAdjustRssi(it) },
                                    onBatteryChange = { viewModel.simAdjustBattery(it) },
                                    onTriggerButton = { viewModel.simTriggerHardwareButtonPress() },
                                    onDisconnect = { viewModel.simDisconnect() },
                                    onConnect = { viewModel.simConnect() }
                                )
                            } else {
                                PhysicalScannerCard(
                                    hasPermissions = hasBlePermissions,
                                    isScanning = isScanning,
                                    connectionState = connectionState,
                                    connectedDeviceName = connectedDeviceName,
                                    connectedDeviceAddress = connectedDeviceAddress,
                                    scannedDevices = viewModel.scannedDevices,
                                    onScanStart = { viewModel.startPhysicalScan() },
                                    onScanStop = { viewModel.stopPhysicalScan() },
                                    onConnect = { addr, name -> viewModel.connectPhysicalDevice(addr, name) },
                                    onDisconnect = { viewModel.disconnectActiveGatt() },
                                    onRequestPermissions = {
                                        val needed = getRequiredPermissions()
                                        requestPermissionsLauncher.launch(needed)
                                    },
                                    onManualLink = { addr, name -> viewModel.linkDeviceManually(addr, name) }
                                )
                            }
                        }

                        // 3. Original Telemetry visualizer card
                        item {
                            TelemetryVisualizerCard(
                                currentRssi = currentRssi,
                                batteryLevel = batteryLevel,
                                txPower = txPower,
                                packetSuccessRate = packetSuccessRate,
                                latencyMs = latencyMs,
                                rssiHistory = viewModel.rssiHistory
                            )
                        }

                        // 4. Emergency dispatch and sound simulator
                        item {
                            EmergencySmsSimulatorCard(
                                isSosActive = isSosActive,
                                currentRssi = currentRssi,
                                batteryLevel = batteryLevel,
                                connectedDevice = connectedDeviceName ?: "Simulated_iTag"
                            )
                        }

                        // 5. System logs debug console
                        item {
                            LiveConsoleCard(
                                logs = viewModel.logs,
                                onClear = { viewModel.clearLogs() },
                                onExport = {
                                    val logsText = viewModel.exportLogsText()
                                    clipboardManager.setText(AnnotatedString(logsText))
                                    Toast.makeText(context, "Tactical Logs Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ==================== SETTINGS TAB ====================
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Profile & session logout card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("👤 CURRENT OPERATOR PROFILE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Email: $currentUserEmail", color = Color.LightGray, fontSize = 12.sp)
                            Text("Organization Code: $currentUserOrgId", color = Color.LightGray, fontSize = 12.sp)
                            Text("Role Profile: $currentUserRole", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.logOut() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("🔒 LOG OUT FROM SESSION", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 5 Emergency Contacts CRUD editor (Visible for kids/elders here!)
                item {
                    EmergencyContactsEditor(viewModel = viewModel)
                }

                // Hardware pairing & toggle mode controls
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📡 HARDWARE TRACKER CONFIUGURATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Simulator Mode Toggle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Enable virtual iTag tracker if no physical hardware is near", color = Color.Gray, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = isSimulatorMode,
                                    onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            if (!checkPermissions(context)) {
                                                val needed = getRequiredPermissions()
                                                requestPermissionsLauncher.launch(needed)
                                            } else {
                                                hasBlePermissions = true
                                                viewModel.setSimulatorMode(false)
                                            }
                                        } else {
                                            viewModel.setSimulatorMode(true)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF58A6FF),
                                        checkedTrackColor = Color(0xFF2188FF).copy(alpha = 0.5f)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            // Physical Scanner link module nested nicely inside settings
                            if (!isSimulatorMode) {
                                PhysicalScannerCard(
                                    hasPermissions = hasBlePermissions,
                                    isScanning = isScanning,
                                    connectionState = connectionState,
                                    connectedDeviceName = connectedDeviceName,
                                    connectedDeviceAddress = connectedDeviceAddress,
                                    scannedDevices = viewModel.scannedDevices,
                                    onScanStart = { viewModel.startPhysicalScan() },
                                    onScanStop = { viewModel.stopPhysicalScan() },
                                    onConnect = { addr, name -> viewModel.connectPhysicalDevice(addr, name) },
                                    onDisconnect = { viewModel.disconnectActiveGatt() },
                                    onRequestPermissions = {
                                        val needed = getRequiredPermissions()
                                        requestPermissionsLauncher.launch(needed)
                                    },
                                    onManualLink = { addr, name -> viewModel.linkDeviceManually(addr, name) }
                                )
                            } else {
                                SimulatorControlCard(
                                    currentRssi = currentRssi,
                                    batteryLevel = batteryLevel,
                                    connectionState = connectionState,
                                    onRssiChange = { viewModel.simAdjustRssi(it) },
                                    onBatteryChange = { viewModel.simAdjustBattery(it) },
                                    onTriggerButton = { viewModel.simTriggerHardwareButtonPress() },
                                    onDisconnect = { viewModel.simDisconnect() },
                                    onConnect = { viewModel.simConnect() }
                                )
                            }
                        }
                    }
                }

                // System debug console nested in settings
                item {
                    LiveConsoleCard(
                        logs = viewModel.logs,
                        onClear = { viewModel.clearLogs() },
                        onExport = {
                            val logsText = viewModel.exportLogsText()
                            clipboardManager.setText(AnnotatedString(logsText))
                            Toast.makeText(context, "Tactical Logs Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// --- SUB-COMPOSABLES ---

@Composable
fun TacticalHeader(
    isSimulatorMode: Boolean,
    isSosActive: Boolean,
    onModeToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SAFETYLINK // BLE LAB",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val statusDotColor = if (isSosActive) Color(0xFFDA3633) else Color(0xFF30A46C)
                    val statusText = if (isSosActive) "CRITICAL ALERT" else "MONITORING ACTIVE"
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusDotColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Mode Toggle Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isSimulatorMode) "SIMULATOR" else "PHYSICAL",
                    color = if (isSimulatorMode) Color(0xFFD29922) else Color(0xFF30A46C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Switch(
                    checked = isSimulatorMode,
                    onCheckedChange = onModeToggle,
                    modifier = Modifier.testTag("simulator_mode_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD29922),
                        checkedTrackColor = Color(0xFF1F242C),
                        uncheckedThumbColor = Color(0xFF30A46C),
                        uncheckedTrackColor = Color(0xFF1F242C)
                    )
                )
            }
        }
    }
}

@Composable
fun TacticalAlertBanner(
    reason: String,
    isSoundActive: Boolean,
    isVibrationActive: Boolean,
    onReset: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm_pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alphaAnim),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFDA3633).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, Color(0xFFF85149))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "SOS EMERGENCY SIGNAL ACTIVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "TRIGGER REASON: $reason",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Physical outputs indication
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🔊 SIREN: " + if (isSoundActive) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "📳 VIBRATE: " + if (isVibrationActive) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier.testTag("reset_alarm_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "RESET ALARM",
                        color = Color(0xFFDA3633),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun SimulatorControlCard(
    currentRssi: Int,
    batteryLevel: Int,
    connectionState: DeviceConnectionState,
    onRssiChange: (Int) -> Unit,
    onBatteryChange: (Int) -> Unit,
    onTriggerButton: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "VIRTUAL WEARABLE TAG SIMULATOR",
                color = Color(0xFFD29922),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // RSSI slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Simulated RSSI Signal",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${currentRssi} dBm",
                        color = if (currentRssi <= -90) Color(0xFFDA3633) else Color(0xFF30A46C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = currentRssi.toFloat(),
                    onValueChange = { onRssiChange(it.toInt()) },
                    valueRange = -100f..-40f,
                    modifier = Modifier.testTag("rssi_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD29922),
                        activeTrackColor = Color(0xFFD29922),
                        inactiveTrackColor = Color(0xFF30363D)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Battery slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Simulated Battery Level",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${batteryLevel}%",
                        color = Color(0xFF30A46C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = batteryLevel.toFloat(),
                    onValueChange = { onBatteryChange(it.toInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.testTag("battery_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF30A46C),
                        activeTrackColor = Color(0xFF30A46C),
                        inactiveTrackColor = Color(0xFF30363D)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive simulation command triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerButton,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trigger_button_press"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                ) {
                    Text(
                        text = "🖲️ PRESS BUTTON",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (connectionState != DeviceConnectionState.DISCONNECTED) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disconnect_sim_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633).copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFDA3633))
                    ) {
                        Text(
                            text = "🔌 SIM LOSS",
                            color = Color(0xFFDA3633),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30A46C).copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF30A46C))
                    ) {
                        Text(
                            text = "🔌 CONNECT SIM",
                            color = Color(0xFF30A46C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicalScannerCard(
    hasPermissions: Boolean,
    isScanning: Boolean,
    connectionState: DeviceConnectionState,
    connectedDeviceName: String?,
    connectedDeviceAddress: String?,
    scannedDevices: List<BleDeviceItem>,
    onScanStart: () -> Unit,
    onScanStop: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermissions: () -> Unit,
    onManualLink: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Radar Scan, 1 = Manual Input Link
    var manualAddress by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PHYSICAL BLE HARDWARE RADAR",
                    color = Color(0xFF30A46C),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

                if (isScanning && selectedTab == 0) {
                    Text(
                        text = "● RADAR ACTIVE",
                        color = Color(0xFF30A46C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasPermissions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(Color(0xFF21262D), RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Physical BLE operation requires Bluetooth and High Precision location permissions on your device.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30A46C)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "GRANT PERMISSIONS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                // Tactical Tab row selector inside Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                        .padding(3.dp)
                ) {
                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) Color(0xFF21262D) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                    ) {
                        Text(
                            text = "📡 BLE RADAR SCAN",
                            color = if (selectedTab == 0) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) Color(0xFF21262D) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                    ) {
                        Text(
                            text = "⌨️ MANUAL MAC LINK",
                            color = if (selectedTab == 1) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // TAB 0: SCAN RADAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isScanning) {
                            Button(
                                onClick = onScanStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_scan_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30A46C)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SCAN BLE SPACE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Button(
                                onClick = onScanStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_scan_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "🛑 STOP SCANNING",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (connectionState != DeviceConnectionState.DISCONNECTED) {
                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF30363D))
                            ) {
                                Text(
                                    text = "DISCONNECT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active connection display
                    if (connectionState != DeviceConnectionState.DISCONNECTED) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF21262D), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF30A46C), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "ACTIVE CONNECTED PHYSICAL TARGET:",
                                    color = Color(0xFF30A46C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "$connectedDeviceName [$connectedDeviceAddress]",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                val stateLabel = when (connectionState) {
                                    DeviceConnectionState.CONNECTING -> "CONNECTING..."
                                    DeviceConnectionState.CONNECTED -> "GATT CONNECTED"
                                    DeviceConnectionState.DISCOVERED_SERVICES -> "READY // SERVICE STREAM ACTIVE"
                                    else -> "DISCONNECTED"
                                }
                                Text(
                                    text = "STATE: $stateLabel",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Scrollable scanned physical devices list
                    Text(
                        text = "SCANNED TARGET LIST:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (scannedDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isScanning) "Searching for signals..." else "Radar Idle. Click 'Scan BLE Space'.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.height(140.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(scannedDevices) { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = device.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = device.address,
                                                color = Color.LightGray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${device.rssi} dBm",
                                                color = if (device.rssi <= -90) Color(0xFFDA3633) else Color(0xFF30A46C),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )

                                            Button(
                                                onClick = { onConnect(device.address, device.name) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30A46C)),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(2.dp)
                                            ) {
                                                Text("LOCK IN", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: MANUAL LINK
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "MANUAL TARGET LINK CONTROLS",
                            color = Color(0xFF58A6FF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Text(
                            text = "Directly specify target hardware MAC & Name. Handy when the physical device is not advertising, or for fast simulator linking.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // MAC Address field
                        Text(
                            text = "TARGET MAC ADDRESS",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = { manualAddress = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_mac_input"),
                            placeholder = { Text("e.g. FF:FF:10:E4:9C:15", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Device Name field
                        Text(
                            text = "TARGET DEVICE NAME",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_name_input"),
                            placeholder = { Text("e.g. iTAG", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Autofill preset button
                        Button(
                            onClick = {
                                manualAddress = "FF:FF:10:E4:9C:15"
                                manualName = "iTAG"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB).copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, Color(0xFF1F6FEB)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚡ AUTOFILL iTAG PRESET (FF:FF:10:E4:9C:15)",
                                color = Color(0xFF58A6FF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onManualLink(manualAddress, manualName)
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                                    .testTag("manual_link_action_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "🔗 LINK DEVICE DIRECTLY",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (connectionState != DeviceConnectionState.DISCONNECTED) {
                                Button(
                                    onClick = onDisconnect,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "DISCONNECT",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Connected confirmation banner inside Manual tab
                        if (connectionState != DeviceConnectionState.DISCONNECTED) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF238636).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF238636), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "ACTIVE: Manually paired and linked to target device $connectedDeviceName ($connectedDeviceAddress)!",
                                    color = Color(0xFF30A46C),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryVisualizerCard(
    currentRssi: Int,
    batteryLevel: Int,
    txPower: Int,
    packetSuccessRate: Double,
    latencyMs: Int,
    rssiHistory: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "HIGH-FIDELITY RSSI DIAGNOSTICS",
                color = Color(0xFF58A6FF),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // RSSI Signal Strength Graph
            RssiLiveGraph(
                rssiHistory = rssiHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF30363D))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Graph legend labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF30A46C)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Safe Zone (>-80dBm)", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFD29922)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Warning Zone (-80 to -89)", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFDA3633)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Danger Zone (≤-90dBm)", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Key Telemetry Metadata Grids
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Distance Metres Card
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("EST. RANGE", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    val distance = calculateDistance(currentRssi)
                    Text(
                        text = String.format(Locale.US, "%.1f M", distance),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Packet Success rate Card
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("PACKET RX", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = String.format(Locale.US, "%.1f%%", packetSuccessRate),
                        color = if (packetSuccessRate > 90) Color(0xFF30A46C) else Color(0xFFD29922),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Latency ms Card
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LATENCY", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "${latencyMs} MS",
                        color = if (latencyMs < 100) Color(0xFF30A46C) else Color(0xFFD29922),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Battery tag Card
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("TAG BATTERY", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "${batteryLevel}%",
                        color = if (batteryLevel > 20) Color(0xFF30A46C) else Color(0xFFDA3633),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencySmsSimulatorCard(
    isSosActive: Boolean,
    currentRssi: Int,
    batteryLevel: Int,
    connectedDevice: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "TACTICAL EMERGENCY RESPONSE (SMS)",
                color = Color(0xFFF85149),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Auto-draft dispatch template
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFDA3633).copy(alpha = if (isSosActive) 0.8f else 0.2f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "DRAFT OUTGOING EMERGENCY CELLULAR SMS:",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // The SMS packet payload
                Text(
                    text = "🚨 SAFETYLINK SOS // EMER-ID: #99281\n" +
                           "TAG STATUS: CONNECTED ($connectedDevice)\n" +
                           "RSSI: ${currentRssi}dBm // EST DISTANCE: ${String.format(Locale.US, "%.1f", calculateDistance(currentRssi))}m\n" +
                           "WEARABLE BATTERY: $batteryLevel%\n" +
                           "DISPATCH COORDINATES: 37.4220° N, 122.0841° W (SIMULATED)\n" +
                           "CRITICAL BROADCAST STATE: " + if (isSosActive) "TRANSMITTING ALARM..." else "STANDBY READY",
                    color = if (isSosActive) Color(0xFFF85149) else Color(0xFF30A46C),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dispatch dispatch status logger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISPATCHER CELLULAR LINK STATE:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = if (isSosActive) "⚡ TELEMETRY DISPATCHING..." else "🟢 LINK OK",
                    color = if (isSosActive) Color(0xFFD29922) else Color(0xFF30A46C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun LiveConsoleCard(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }
    val listState = rememberLazyListState()

    // Auto scroll logs to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE TELEMETRY CONSOLE & DEBUG LOGGER",
                    color = Color(0xFF58A6FF),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export logs",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("export_logs_button")
                            .clickable { onExport() }
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Clear logs",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("clear_logs_button")
                            .clickable { onClear() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Console filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    text = "ALL",
                    isSelected = selectedFilter == null,
                    onClick = { selectedFilter = null }
                )
                FilterChip(
                    text = "INFO",
                    isSelected = selectedFilter == LogLevel.INFO,
                    onClick = { selectedFilter = LogLevel.INFO }
                )
                FilterChip(
                    text = "WARN",
                    isSelected = selectedFilter == LogLevel.WARN,
                    onClick = { selectedFilter = LogLevel.WARN }
                )
                FilterChip(
                    text = "DANGER",
                    isSelected = selectedFilter == LogLevel.DANGER,
                    onClick = { selectedFilter = LogLevel.DANGER }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable terminal screen
            val filteredLogs = remember(logs.size, selectedFilter) {
                if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color(0xFF090D13), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No log records found for selection.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLogs) { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "[${log.timestamp}]",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(74.dp)
                                )

                                val badgeColor = when (log.level) {
                                    LogLevel.INFO -> Color(0xFF58A6FF)
                                    LogLevel.WARN -> Color(0xFFD29922)
                                    LogLevel.DANGER -> Color(0xFFF85149)
                                }
                                Text(
                                    text = "[${log.level.name}]",
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(60.dp)
                                )

                                Text(
                                    text = log.message,
                                    color = Color(0xFFC9D1D9),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) Color(0xFF21262D) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF58A6FF) else Color(0xFF30363D),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun RssiLiveGraph(rssiHistory: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val gridLinesY = 4
        val gridLinesX = 5

        // Background
        drawRect(color = Color(0xFF090D13))

        // Draw grid horizontal lines
        for (i in 0..gridLinesY) {
            val y = (height / gridLinesY) * i
            drawLine(
                color = Color(0xFF1F242C),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw grid vertical lines
        for (i in 0..gridLinesX) {
            val x = (width / gridLinesX) * i
            drawLine(
                color = Color(0xFF1F242C),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }

        // Mapping function: RSSI ranges from -100 to -40 (60 units total range)
        // y = height * (1.0f - (rssi - (-100)) / 60)
        
        // Threshold zones:
        // -90dBm Danger Zone line
        val dangerY = height * (1.0f - (-90f - (-100f)) / 60f)
        drawLine(
            color = Color(0xFFF85149).copy(alpha = 0.5f),
            start = Offset(0f, dangerY),
            end = Offset(width, dangerY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )

        // -80dBm Warning Zone line
        val warningY = height * (1.0f - (-80f - (-100f)) / 60f)
        drawLine(
            color = Color(0xFFD29922).copy(alpha = 0.5f),
            start = Offset(0f, warningY),
            end = Offset(width, warningY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        // Plot RSSI line if we have values
        if (rssiHistory.isNotEmpty()) {
            val points = rssiHistory.mapIndexed { idx, rssi ->
                val x = (width / (rssiHistory.size - 1).coerceAtLeast(1)) * idx
                val clamped = rssi.coerceIn(-100, -40)
                val y = height * (1.0f - (clamped.toFloat() - (-100f)) / 60f)
                Offset(x, y)
            }

            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            // Draw line
            drawPath(
                path = path,
                color = Color(0xFF30A46C),
                style = Stroke(
                    width = 3.5f,
                    join = StrokeJoin.Round
                )
            )

            // Draw current active dot with glow
            val currentPoint = points.last()
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = currentPoint
            )
            drawCircle(
                color = Color(0xFF30A46C).copy(alpha = 0.6f),
                radius = 14f,
                center = currentPoint,
                style = Stroke(width = 3f)
            )
        }
    }
}

// --- CORE UTILITY FUNCTIONS ---

fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

fun checkPermissions(context: Context): Boolean {
    val needed = getRequiredPermissions()
    return needed.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun calculateDistance(rssi: Int): Double {
    val txPower = -59.0 // Constant TX Power of standard iTag wearable at 1 meter
    if (rssi == 0) return 0.0
    // Standard path-loss exponent N for realistic indoor environment = 2.5
    return Math.pow(10.0, (txPower - rssi) / (10.0 * 2.5))
}

@Composable
fun SafetyLinkGatekeeperScreen(
    viewModel: BleLabViewModel
) {
    var regName by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regOrg by remember { mutableStateOf("") }
    var chosenRole by remember { mutableStateOf("USER") } // "USER" or "RESPONDER"
    var isSignUpMode by remember { mutableStateOf(true) }

    val loginError by viewModel.loginError.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D13))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .background(Color(0xFF161B22), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Crest Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF388BFD).copy(alpha = 0.15f), RoundedCornerShape(36.dp))
                    .border(BorderStroke(1.5.dp, Color(0xFF58A6FF)), RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SAFETYLINK SECURE ACCESS",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Tactical Wireless Telemetry Portal",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SIGN UP / SIGN IN TOGGLE SWITCHER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { isSignUpMode = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSignUpMode) Color(0xFF21262D) else Color.Transparent
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CREATE ACCOUNT",
                        color = if (isSignUpMode) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { isSignUpMode = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isSignUpMode) Color(0xFF21262D) else Color.Transparent
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SIGN IN",
                        color = if (!isSignUpMode) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Details Entry
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "OPERATOR CREDENTIALS",
                    color = Color(0xFF58A6FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Column {
                    Text(
                        text = "Email Address",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = regName,
                        onValueChange = { regName = it },
                        modifier = Modifier.fillMaxWidth().testTag("email_input"),
                        placeholder = { Text("operator@safetylink.org", fontSize = 12.sp, color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        )
                    )
                }

                Column {
                    Text(
                        text = "Password",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it },
                        modifier = Modifier.fillMaxWidth().testTag("password_input"),
                        placeholder = { Text("At least 6 characters", fontSize = 12.sp, color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        )
                    )
                }

                if (isSignUpMode) {
                    Column {
                        Text(
                            text = "Organization Code (Optional)",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = regOrg,
                            onValueChange = { regOrg = it },
                            modifier = Modifier.fillMaxWidth().testTag("org_id_input"),
                            placeholder = { Text("e.g. SL-ORG-100", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color(0xFF30363D)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "CHOOSE SYSTEM PROFILE TYPE",
                        color = Color(0xFF58A6FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Large Side-by-Side Role Selector buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // USER BUTTON
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { chosenRole = "USER" }
                                .border(
                                    width = 2.dp,
                                    color = if (chosenRole == "USER") Color(0xFF58A6FF) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (chosenRole == "USER") Color(0xFF21262D) else Color(0xFF0D1117)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("👤", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "USER MODE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "Simple SOS, kids/elder",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // RESPONDER BUTTON
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { chosenRole = "RESPONDER" }
                                .border(
                                    width = 2.dp,
                                    color = if (chosenRole == "RESPONDER") Color(0xFF238636) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (chosenRole == "RESPONDER") Color(0xFF1E281F) else Color(0xFF0D1117)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🚒", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "RESPONDER",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "Monitor alerts, rescues",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Launch Master Button
                Button(
                    onClick = {
                        if (isSignUpMode) {
                            viewModel.registerWithFirebase(regName, regPassword, regOrg, chosenRole) { success, err ->
                                if (success) {
                                    Toast.makeText(context, "Welcome to SafetyLink!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Registration Failed: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            viewModel.loginWithFirebase(regName, regPassword) { success, err ->
                                if (success) {
                                    Toast.makeText(context, "Welcome back Operator!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Login Failed: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("verify_login_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (chosenRole == "USER") Color(0xFF2188FF) else Color(0xFF2EA44F)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isSignUpMode) "🚀 REGISTER ACCOUNT" else "🔑 SIGN IN TO PANEL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencyContactsEditor(viewModel: BleLabViewModel) {
    val contacts = viewModel.emergencyContacts
    var editingIndex by remember { mutableStateOf(-1) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📞 MANAGE 5 EMERGENCY CONTACTS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            contacts.forEachIndexed { index, contact ->
                if (editingIndex == index) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "EDITING CONTACT #${index + 1}",
                            color = Color(0xFF58A6FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Contact Name", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color(0xFF30363D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone / Channel", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color(0xFF30363D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.updateEmergencyContact(index, editName, editPhone)
                                    editingIndex = -1
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                            ) {
                                Text("SAVE", fontSize = 11.sp, color = Color.White)
                            }
                            Button(
                                onClick = { editingIndex = -1 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("CANCEL", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${index + 1}. ${contact.name}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = contact.phone,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                editingIndex = index
                                editName = contact.name
                                editPhone = contact.phone
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("EDIT ✏️", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                    if (index < contacts.size - 1) {
                        HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
