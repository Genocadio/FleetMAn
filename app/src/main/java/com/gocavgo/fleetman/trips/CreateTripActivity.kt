package com.gocavgo.fleetman.trips

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.gocavgo.fleetman.ui.theme.FleetManTheme
import com.gocavgo.fleetman.service.RemoteDataManager
import com.gocavgo.fleetman.dataclass.*
import com.gocavgo.fleetman.service.RemoteResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Note: You'll need to update your CreateTripRequest data class to match the API:
/*
@Serializable
data class CreateTripRequest(
    val route_id: Int,
    val vehicle_id: Int,
    val departure_time: Long,
    val connection_mode: String,
    val notes: String? = null,
    val is_reversed: Boolean = false,
    val custom_waypoints: List<CreateCustomWaypoint> = emptyList(),
    val no_waypoints: Boolean = false
)

@Serializable
data class CreateCustomWaypoint(
    val location_id: Int,
    val order: Int,
    val price: Double? = null,
    val remaining_time: Long? = null,
    val remaining_distance: Double? = null
)
*/

class CreateTripActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CAR_ID = "extra_car_id"
        const val EXTRA_LICENSE_PLATE = "extra_license_plate"
        private const val TAG = "CreateTripActivity"
    }

    private val remoteDataManager = RemoteDataManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get the car ID and license plate from the intent
        val carId = intent.getStringExtra(EXTRA_CAR_ID)
        val licensePlate = intent.getStringExtra(EXTRA_LICENSE_PLATE)

        // Log the received car ID
        Log.d(TAG, "Received car ID: $carId")
        Log.d(TAG, "Received license plate: $licensePlate")

        setContent {
            FleetManTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TripManagementContent(
                        carId = carId?.toIntOrNull() ?: 0,
                        licensePlate = licensePlate,
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
        remoteDataManager.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripManagementContent(
    carId: Int,
    licensePlate: String?,
    onBackPressed: () -> Unit,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Trip Management",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                TextButton(
                    onClick = onBackPressed
                ) {
                    Text("← Back")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Vehicle Information Card
        VehicleInfoCard(carId = carId, licensePlate = licensePlate)

        Spacer(modifier = Modifier.height(24.dp))

        // Tab Row
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("View Trips") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Create Trip") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        when (selectedTabIndex) {
            0 -> ViewTripsTab(
                vehicleId = carId,
                remoteDataManager = remoteDataManager,
                lifecycleScope = lifecycleScope
            )
            1 -> CreateTripTab(
                vehicleId = carId,
                remoteDataManager = remoteDataManager,
                lifecycleScope = lifecycleScope
            )
        }
    }
}

@Composable
private fun VehicleInfoCard(carId: Int, licensePlate: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Selected Vehicle",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Vehicle ID:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = carId.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "License Plate:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = licensePlate ?: "Unknown",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewTripsTab(
    vehicleId: Int,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var trips by remember { mutableStateOf<List<TripResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }

    // Function to load trips
    fun loadTrips(page: Int = 1) {
        lifecycleScope.launch {
            isLoading = true
            errorMessage = null

            when (val result = remoteDataManager.getVehicleTrips(vehicleId, page, 20)) {
                is RemoteResult.Success -> {
                    if (page == 1) {
                        trips = result.data.data
                    } else {
                        trips = trips + result.data.data
                    }
                    currentPage = page
                    hasNextPage = result.data.pagination.has_next
                }
                is RemoteResult.Error -> {
                    errorMessage = result.message
                }
            }

            isLoading = false
        }
    }

    // Load trips on first composition
    LaunchedEffect(vehicleId) {
        loadTrips()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && trips.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Error: $errorMessage",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else if (trips.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No trips found for this vehicle",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trips) { trip ->
                    TripCard(trip = trip)
                }

                if (hasNextPage) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Button(
                                    onClick = { loadTrips(currentPage + 1) }
                                ) {
                                    Text("Load More")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(trip: TripResponse) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val departureDate = Date(trip.departure_time * 1000)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip #${trip.id}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (trip.status) {
                            "SCHEDULED" -> MaterialTheme.colorScheme.primaryContainer
                            "ACTIVE" -> MaterialTheme.colorScheme.secondaryContainer
                            "COMPLETED" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text = trip.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "From: ${trip.route.origin.custom_name ?: trip.route.origin.google_place_name}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "To: ${trip.route.destination.custom_name ?: trip.route.destination.google_place_name}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Departure: ${dateFormatter.format(departureDate)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Seats: ${trip.seats}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trip.notes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes: ${trip.notes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTripTab(
    vehicleId: Int,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var routes by remember { mutableStateOf<List<SaveRouteResponse>>(emptyList()) }
    var locations by remember { mutableStateOf<List<SavePlaceResponse>>(emptyList()) }
    var selectedRoute by remember { mutableStateOf<SaveRouteResponse?>(null) }
    var connectionMode by remember { mutableStateOf("ONLINE") }
    var notes by remember { mutableStateOf("") }
    var isReversed by remember { mutableStateOf(false) }
    var customWaypoints by remember { mutableStateOf<List<CreateCustomWaypoint>>(emptyList()) }
    var departureTime by remember { mutableLongStateOf(System.currentTimeMillis() / 1000 + 3600) } // 1 hour from now
    var noWaypoints by remember { mutableStateOf(false) }

    var isLoadingRoutes by remember { mutableStateOf(false) }
    var isLoadingLocations by remember { mutableStateOf(false) }
    var isCreatingTrip by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Load routes and locations on first composition
    LaunchedEffect(Unit) {
        // Load routes
        lifecycleScope.launch {
            isLoadingRoutes = true
            when (val result = remoteDataManager.getRoutes(page = 1, limit = 100)) {
                is RemoteResult.Success -> routes = result.data.data
                is RemoteResult.Error -> errorMessage = "Failed to load routes: ${result.message}"
            }
            isLoadingRoutes = false
        }

        // Load locations
        lifecycleScope.launch {
            isLoadingLocations = true
            when (val result = remoteDataManager.getLocations(page = 1, limit = 100)) {
                is RemoteResult.Success -> locations = result.data.data
                is RemoteResult.Error -> errorMessage = "Failed to load locations: ${result.message}"
            }
            isLoadingLocations = false
        }
    }

    // Function to create trip
    fun createTrip() {
        if (selectedRoute == null) {
            errorMessage = "Please select a route"
            return
        }

        // Validate custom waypoints only if they exist and no_waypoints is false
        if (!noWaypoints && customWaypoints.isNotEmpty()) {
            val invalidWaypoints = customWaypoints.filter { it.location_id == 0 }
            if (invalidWaypoints.isNotEmpty()) {
                errorMessage = "All custom waypoints must have a valid location selected"
                return
            }

            // Check if any waypoint has a price that is negative or zero when specified
            val invalidPrices = customWaypoints.filter { it.price != null && it.price <= 0.0 }
            if (invalidPrices.isNotEmpty()) {
                errorMessage = "Custom waypoint prices must be greater than 0 when specified"
                return
            }

            // Check for negative remaining_time or remaining_distance
            val invalidTimes = customWaypoints.filter { it.remaining_time != null && it.remaining_time < 0 }
            if (invalidTimes.isNotEmpty()) {
                errorMessage = "Remaining time must be >= 0 when specified"
                return
            }

            val invalidDistances = customWaypoints.filter { it.remaining_distance != null && it.remaining_distance < 0.0 }
            if (invalidDistances.isNotEmpty()) {
                errorMessage = "Remaining distance must be >= 0 when specified"
                return
            }
        }

        lifecycleScope.launch {
            isCreatingTrip = true
            errorMessage = null
            successMessage = null

            val createTripRequest = CreateTripRequest(
                route_id = selectedRoute!!.id,
                vehicle_id = vehicleId,
                departure_time = departureTime,
                connection_mode = connectionMode,
                notes = notes.takeIf { it.isNotBlank() },
                is_reversed = isReversed,
                custom_waypoints = if (noWaypoints) emptyList() else customWaypoints,
                no_waypoints = noWaypoints
            )

            when (val result = remoteDataManager.createTrip(createTripRequest)) {
                is RemoteResult.Success -> {
                    val tripType = when {
                        noWaypoints -> "direct trip (no waypoints)"
                        customWaypoints.isNotEmpty() -> "trip with ${customWaypoints.size} custom waypoints"
                        isReversed -> "reversed route trip"
                        else -> "standard route trip"
                    }
                    successMessage = "Trip created successfully! Trip ID: ${result.data.id} ($tripType)"

                    // Reset form
                    selectedRoute = null
                    connectionMode = "ONLINE"
                    notes = ""
                    isReversed = false
                    customWaypoints = emptyList()
                    noWaypoints = false
                    departureTime = System.currentTimeMillis() / 1000 + 3600
                }
                is RemoteResult.Error -> {
                    errorMessage = "Failed to create trip: ${result.message}"
                }
            }

            isCreatingTrip = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success/Error Messages
            item {
                successMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                errorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Route Selection
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select Route",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoadingRoutes) {
                            CircularProgressIndicator()
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = false,
                                onExpandedChange = { }
                            ) {
                                OutlinedTextField(
                                    value = selectedRoute?.let {
                                        "${it.origin.custom_name ?: it.origin.google_place_name} → ${it.destination.custom_name ?: it.destination.google_place_name}"
                                    } ?: "Select a route",
                                    onValueChange = { },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = false,
                                    onDismissRequest = { }
                                ) {
                                    routes.forEach { route ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.custom_name ?: route.destination.google_place_name}")
                                            },
                                            onClick = {
                                                selectedRoute = route
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Trip Options
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trip Options",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // No Waypoints Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Waypoints (Direct Trip)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Only origin and destination, no intermediate stops",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = noWaypoints,
                                onCheckedChange = {
                                    noWaypoints = it
                                    if (it) {
                                        // Clear custom waypoints when no_waypoints is enabled
                                        customWaypoints = emptyList()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Connection Mode
                        Text(
                            text = "Connection Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ONLINE", "OFFLINE", "HYBRID").forEach { mode ->
                                FilterChip(
                                    selected = connectionMode == mode,
                                    onClick = { connectionMode = mode },
                                    label = { Text(mode) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Reverse Route Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reverse Route",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isReversed,
                                onCheckedChange = { isReversed = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // Custom Waypoints
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Waypoints",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (!noWaypoints) {
                                TextButton(
                                    onClick = {
                                        // Add new empty waypoint
                                        customWaypoints = customWaypoints + CreateCustomWaypoint(
                                            location_id = 0,
                                            order = customWaypoints.size,
                                            price = null
                                        )
                                    }
                                ) {
                                    Text("+ Add Waypoint")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (noWaypoints) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    text = "Direct trip mode: Only origin and destination will be used (no waypoints)",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else if (customWaypoints.isEmpty()) {
                            Text(
                                text = "No custom waypoints. Route's default waypoints will be used.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Custom waypoints will override route's default waypoints",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Display existing waypoints (only if not noWaypoints)
                        if (!noWaypoints) {
                            customWaypoints.forEachIndexed { index, waypoint ->
                                Spacer(modifier = Modifier.height(8.dp))

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Waypoint ${index + 1} (Order: ${waypoint.order})",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            TextButton(
                                                onClick = {
                                                    customWaypoints = customWaypoints.filterIndexed { i, _ -> i != index }
                                                        .mapIndexed { newIndex, wp -> wp.copy(order = newIndex) }
                                                }
                                            ) {
                                                Text("Remove", color = MaterialTheme.colorScheme.error)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Location Selection
                                        var expanded by remember { mutableStateOf(false) }
                                        val selectedLocation = locations.find { it.id == waypoint.location_id }

                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = it }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedLocation?.let {
                                                    it.custom_name ?: it.google_place_name
                                                } ?: "Select location",
                                                onValueChange = { },
                                                readOnly = true,
                                                label = { Text("Location") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor(),
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                if (isLoadingLocations) {
                                                    DropdownMenuItem(
                                                        text = { Text("Loading locations...") },
                                                        onClick = { }
                                                    )
                                                } else {
                                                    locations.forEach { location ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Column {
                                                                    Text(
                                                                        text = location.custom_name ?: location.google_place_name,
                                                                        fontSize = 14.sp
                                                                    )
                                                                    Text(
                                                                        text = "${location.district}, ${location.province}",
                                                                        fontSize = 12.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            },
                                                            onClick = {
                                                                customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                                                    if (i == index) wp.copy(location_id = location.id)
                                                                    else wp
                                                                }
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Price Input (Optional)
                                        OutlinedTextField(
                                            value = waypoint.price?.toString() ?: "",
                                            onValueChange = { newPrice ->
                                                val price = if (newPrice.isBlank()) null else newPrice.toDoubleOrNull()
                                                customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                                    if (i == index) wp.copy(price = price)
                                                    else wp
                                                }
                                            },
                                            label = { Text("Price (RWF) - Optional") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            placeholder = { Text("Leave empty for default pricing") }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Advanced fields (Optional)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = waypoint.remaining_time?.toString() ?: "",
                                                onValueChange = { newTime ->
                                                    val time = if (newTime.isBlank()) null else newTime.toLongOrNull()
                                                    customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                                        if (i == index) wp.copy(remaining_time = time)
                                                        else wp
                                                    }
                                                },
                                                label = { Text("Time (sec)") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                placeholder = { Text("Optional") }
                                            )

                                            OutlinedTextField(
                                                value = waypoint.remaining_distance?.toString() ?: "",
                                                onValueChange = { newDistance ->
                                                    val distance = if (newDistance.isBlank()) null else newDistance.toDoubleOrNull()
                                                    customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                                        if (i == index) wp.copy(remaining_distance = distance)
                                                        else wp
                                                    }
                                                },
                                                label = { Text("Distance (m)") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                placeholder = { Text("Optional") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create Trip Button
            item {
                Button(
                    onClick = { createTrip() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedRoute != null && !isCreatingTrip
                ) {
                    if (isCreatingTrip) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Create Trip")
                }
            }
        }
    }
}