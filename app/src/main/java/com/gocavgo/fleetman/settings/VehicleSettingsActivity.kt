package com.gocavgo.fleetman.settings

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.gocavgo.fleetman.service.RemoteDataManager
import com.gocavgo.fleetman.service.RemoteResult
import com.gocavgo.fleetman.settings.PasswordManager
import com.gocavgo.fleetman.ui.theme.FleetManTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VehicleSettingsActivity"

class VehicleSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_LICENSE_PLATE = "extra_license_plate"
    }

    private val remoteDataManager = RemoteDataManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get vehicle ID and license plate from intent
        val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        val licensePlate = intent.getStringExtra(EXTRA_LICENSE_PLATE)

        Log.d(TAG, "Received vehicle ID: $vehicleId")
        Log.d(TAG, "Received license plate: $licensePlate")

        setContent {
            FleetManTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VehicleSettingsContent(
                        vehicleId = vehicleId?.toLongOrNull() ?: 0L,
                        licensePlate = licensePlate ?: "",
                        onBackPressed = { finish() },
                        remoteDataManager = remoteDataManager,
                        lifecycleScope = lifecycleScope
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            remoteDataManager.cleanup()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleSettingsContent(
    vehicleId: Long,
    licensePlate: String,
    onBackPressed: () -> Unit,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(true) }
    
    // Observe global password state for this specific vehicle - poll periodically to check for password
    var passwordText by remember { mutableStateOf(PasswordManager.getPassword(vehicleId)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            passwordText = PasswordManager.getPassword(vehicleId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Vehicle Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Vehicle Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "License Plate: $licensePlate",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Vehicle ID: $vehicleId",
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Password Display (if exists)
            passwordText?.let { password ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vehicle Password",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isPasswordVisible) password else "•".repeat(password.length),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible }
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Logout Button
            Button(
                onClick = {
                    if (vehicleId > 0 && licensePlate.isNotBlank()) {
                        isLoading = true
                        errorMessage = null
                        lifecycleScope.launch {
                            // First, logout the vehicle
                            when (val logoutResult = remoteDataManager.logoutVehicle(vehicleId)) {
                                is RemoteResult.Success -> {
                                    Log.d(TAG, "Logout successful, now resetting password")
                                    // If logout successful (200), reset password
                                    when (val passwordResult = remoteDataManager.resetVehiclePassword(licensePlate)) {
                                        is RemoteResult.Success -> {
                                            PasswordManager.setPassword(vehicleId, passwordResult.data)
                                            passwordText = passwordResult.data
                                            isLoading = false
                                            Log.d(TAG, "Password reset successful: ${passwordResult.data}")
                                        }
                                        is RemoteResult.Error -> {
                                            errorMessage = passwordResult.message
                                            isLoading = false
                                            Log.e(TAG, "Password reset failed: ${passwordResult.message}")
                                        }
                                    }
                                }
                                is RemoteResult.Error -> {
                                    errorMessage = logoutResult.message
                                    isLoading = false
                                    Log.e(TAG, "Logout failed: ${logoutResult.message}")
                                }
                            }
                        }
                    } else {
                        errorMessage = "Invalid vehicle information"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && vehicleId > 0 && licensePlate.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Logout Vehicle")
                }
            }

            // Error Message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
