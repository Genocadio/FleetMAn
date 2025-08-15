package com.gocavgo.fleetman

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.gocavgo.fleetman.auth.AuthActivity
import com.gocavgo.fleetman.auth.CredentialsManager
import com.gocavgo.fleetman.dataclass.VehicleResponseDto
import com.gocavgo.fleetman.enums.VehicleStatus
import com.gocavgo.fleetman.service.RemoteDataManager
import com.gocavgo.fleetman.service.RemoteResult
import com.gocavgo.fleetman.trips.CreateTripActivity
import com.gocavgo.fleetman.ui.theme.FleetManTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var credentialsManager: CredentialsManager
    private lateinit var remoteDataManager: RemoteDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize managers
        credentialsManager = CredentialsManager(applicationContext)
        remoteDataManager = RemoteDataManager.getInstance()

        // Check if user is logged in
        if (!credentialsManager.getLoginStatus()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContent {
            FleetManTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent() {
        var showUserMenu by remember { mutableStateOf(false) }
        var vehicles by remember { mutableStateOf<List<VehicleResponseDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Fetch vehicles on composition
        LaunchedEffect(Unit) {
            val companyId = credentialsManager.getCompanyId()
            if (companyId != null) {
                lifecycleScope.launch {
                    when (val result = remoteDataManager.getCompanyVehicles(companyId)) {
                        is RemoteResult.Success -> {
                            vehicles = result.data
                            Log.d("MainActivity", "Fetched vehicles: $vehicles")
                            isLoading = false
                        }
                        is RemoteResult.Error -> {
                            errorMessage = result.message
                            isLoading = false
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar with User Name
            TopAppBar(
                title = {
                    Text(
                        text = "Hello ${credentialsManager.getUserName()}!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    TextButton(
                        onClick = { showUserMenu = !showUserMenu }
                    ) {
                        Text("▼")
                    }
                }
            )

            // User Menu Dropdown
            if (showUserMenu) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Company: ${credentialsManager.getCompanyName()}",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = {
                                credentialsManager.clearUserData()
                                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Logout")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Content Area
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                vehicles.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No vehicles found",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vehicles) { vehicle ->
                            VehicleCard(vehicle = vehicle)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun VehicleCard(vehicle: VehicleResponseDto) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // License Plate and Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = vehicle.licensePlate,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    StatusChip(status = vehicle.status)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vehicle Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${vehicle.make} ${vehicle.model}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Type: ${vehicle.vehicleType}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Capacity: ${vehicle.capacity} passengers",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Driver Information
                vehicle.driver?.let { driver ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Driver Information",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Note: CompanyUserResponseDto has private fields, so we need getters or make them public
                    // For now, showing placeholder text since fields are private
                    Text(
                        text = "Driver: Available",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: run {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No driver assigned",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { /* TODO: Handle details */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Details", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(this@MainActivity, CreateTripActivity::class.java).apply {
                                putExtra(CreateTripActivity.EXTRA_CAR_ID, vehicle.id.toString())
                                putExtra(CreateTripActivity.EXTRA_LICENSE_PLATE, vehicle.licensePlate)
                            }
                            startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create Trip", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { /* TODO: Handle action 3 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Action 3", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusChip(status: VehicleStatus) {
        val (backgroundColor, textColor) = when (status) {
            VehicleStatus.AVAILABLE -> Pair(Color(0xFF4CAF50), Color.White)
            VehicleStatus.OCCUPIED -> Pair(Color(0xFFFF9800), Color.White)
            VehicleStatus.MAINTENANCE -> Pair(Color(0xFFF44336), Color.White)
            else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor
        ) {
            Text(
                text = status.name.replace("_", " "),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::remoteDataManager.isInitialized) {
            lifecycleScope.launch {
                remoteDataManager.cleanup()
            }
        }
    }
}