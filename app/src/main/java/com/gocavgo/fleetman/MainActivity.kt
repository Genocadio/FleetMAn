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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.fleetman.settings.PasswordManager
import androidx.lifecycle.lifecycleScope
import com.gocavgo.fleetman.auth.AuthActivity
import com.gocavgo.fleetman.auth.CredentialsManager
import com.gocavgo.fleetman.dataclass.VehicleResponseDto
import com.gocavgo.fleetman.enums.VehicleStatus
import com.gocavgo.fleetman.service.RemoteDataManager
import com.gocavgo.fleetman.service.RemoteResult
import com.gocavgo.fleetman.settings.VehicleSettingsActivity
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
        var isRefreshing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        
        // Get user initials
        val userName = credentialsManager.getUserName()
        val userInitials = remember(userName) {
            extractInitials(userName)
        }

        // Function to load vehicles
        fun loadVehicles(isRefresh: Boolean = false) {
            lifecycleScope.launch {
                if (isRefresh) {
                    isRefreshing = true
                } else {
                    isLoading = true
                }
                errorMessage = null
                
                val companyId = credentialsManager.getCompanyId()
                if (companyId != null) {
                    when (val result = remoteDataManager.getCompanyVehicles(companyId)) {
                        is RemoteResult.Success -> {
                            vehicles = result.data
                            Log.d("MainActivity", "Fetched vehicles: $vehicles")
                            isLoading = false
                            isRefreshing = false
                            errorMessage = null
                        }
                        is RemoteResult.Error -> {
                            errorMessage = result.message
                            isLoading = false
                            isRefreshing = false
                        }
                    }
                } else {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }

        // Fetch vehicles on composition
        LaunchedEffect(Unit) {
            loadVehicles()
        }

        // Filter vehicles based on search query
        val filteredVehicles = remember(vehicles, searchQuery) {
            if (searchQuery.isBlank()) {
                vehicles
            } else {
                vehicles.filter { vehicle ->
                    vehicle.licensePlate.contains(searchQuery, ignoreCase = true)
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
                    Box {
                        IconButton(
                            onClick = { showUserMenu = !showUserMenu }
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userInitials,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // Floating Dropdown Menu
                        DropdownMenu(
                            expanded = showUserMenu,
                            onDismissRequest = { showUserMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Company: ${credentialsManager.getCompanyName()}",
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = { showUserMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text("Logout")
                                },
                                onClick = {
                                    showUserMenu = false
                                    credentialsManager.clearUserData()
                                    startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                                    finish()
                                }
                            )
                        }
                    }
                }
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by plate number...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true
            )

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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Error: $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { loadVehicles() }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                filteredVehicles.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No vehicles found matching '$searchQuery'" else "No vehicles found",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    SwipeRefresh(
                        state = rememberSwipeRefreshState(isRefreshing),
                        onRefresh = { loadVehicles(isRefresh = true) }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredVehicles) { vehicle ->
                                VehicleCard(vehicle = vehicle)
                            }
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons (Icon buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(this@MainActivity, VehicleSettingsActivity::class.java).apply {
                                putExtra(VehicleSettingsActivity.EXTRA_VEHICLE_ID, vehicle.id.toString())
                                putExtra(VehicleSettingsActivity.EXTRA_LICENSE_PLATE, vehicle.licensePlate)
                            }
                            startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(this@MainActivity, CreateTripActivity::class.java).apply {
                                putExtra(CreateTripActivity.EXTRA_CAR_ID, vehicle.id.toString())
                                putExtra(CreateTripActivity.EXTRA_LICENSE_PLATE, vehicle.licensePlate)
                            }
                            startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create Trip"
                        )
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
        // Clear all passwords when app is destroyed
        PasswordManager.clearAllPasswords()
        if (::remoteDataManager.isInitialized) {
            lifecycleScope.launch {
                remoteDataManager.cleanup()
            }
        }
    }
    
    /**
     * Extracts initials from a user name.
     * Rules:
     * - If one name: first letter of that name
     * - If two names: first letter of each name
     * - If more than two names: first letter of first two names only
     */
    private fun extractInitials(userName: String): String {
        if (userName.isBlank()) return "U"
        
        val names = userName.trim().split(Regex("\\s+"))
        return when {
            names.size == 1 -> {
                // Single name: return first letter
                names[0].take(1).uppercase()
            }
            names.size >= 2 -> {
                // Two or more names: return first letter of first two names
                "${names[0].take(1)}${names[1].take(1)}".uppercase()
            }
            else -> "U"
        }
    }
}