package com.gocavgo.fleetman.trips

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.lifecycle.lifecycleScope
import com.gocavgo.fleetman.ui.theme.FleetManTheme
import com.gocavgo.fleetman.ui.components.*
import com.gocavgo.fleetman.service.RemoteDataManager
import com.gocavgo.fleetman.dataclass.*
import com.gocavgo.fleetman.service.RemoteResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                        lifecycleScope = lifecycleScope,
                        activity = this@CreateTripActivity
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
private fun TripManagementContent(
    carId: Int,
    licensePlate: String?,
    onBackPressed: () -> Unit,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Vehicle Information Card - only show on View Trips tab
        if (selectedTabIndex == 0) {
            VehicleInfoCard(carId = carId, licensePlate = licensePlate)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tab Row - moved up
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
                lifecycleScope = lifecycleScope,
                activity = activity
            )

            1 -> CreateTripTab(
                vehicleId = carId,
                remoteDataManager = remoteDataManager,
                lifecycleScope = lifecycleScope,
                activity = activity
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableRouteDropdown(
    routes: List<SaveRouteResponse>,
    selectedRoute: SaveRouteResponse?,
    onRouteSelected: (SaveRouteResponse?) -> Unit,
    isLoading: Boolean
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val filteredRoutes = remember(routes, searchText) {
        if (searchText.isBlank()) {
            emptyList()
        } else {
            routes.filter { route ->
                val origin = route.origin.custom_name ?: route.origin.google_place_name
                val destination =
                    route.destination.custom_name ?: route.destination.google_place_name
                origin.contains(searchText, ignoreCase = true) ||
                        destination.contains(searchText, ignoreCase = true)
            }.take(10) // Limit to 10 suggestions for better performance
        }
    }

    if (selectedRoute == null) {
        // Show search input with auto-suggestions
        Box(modifier = Modifier.fillMaxWidth()) {
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            OutlinedTextField(
                value = searchText,
                onValueChange = { 
                    searchText = it
                    showSuggestions = it.isNotBlank()
                },
                label = { Text("Search Routes") },
                placeholder = { Text("Type to search routes...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                interactionSource = interactionSource
            )
            
            // Update suggestions visibility based on focus and text
            LaunchedEffect(isFocused, searchText) {
                showSuggestions = isFocused && searchText.isNotBlank()
            }

            // Auto-suggestions dropdown
            if (showSuggestions && filteredRoutes.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 56.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Text("Loading routes...")
                                }
                            }
                        } else {
                            items(filteredRoutes) { route ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.custom_name ?: route.destination.google_place_name}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                            Text(
                                                text = "Route ID: ${route.id} | Distance: ${route.distance_meters}m | Duration: ${route.estimated_duration_seconds}s",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (route.city_route) {
                                                Text(
                                                    text = "City Route",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onRouteSelected(route)
                                        searchText = ""
                                        showSuggestions = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show selected route with clear button
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedRoute.origin.custom_name ?: selectedRoute.origin.google_place_name} → ${selectedRoute.destination.custom_name ?: selectedRoute.destination.google_place_name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Route #${selectedRoute.id} • ${selectedRoute.distance_meters}m • ${selectedRoute.estimated_duration_seconds}s",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                IconButton(
                    onClick = { 
                        onRouteSelected(null)
                        searchText = ""
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
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
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
) {
    var trips by remember { mutableStateOf<List<TripResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isDeletingTrip by remember { mutableStateOf(false) }

    // Function to load trips
    fun loadTrips(page: Int = 1) {
        lifecycleScope.launch {
            isLoading = true

            when (val result = remoteDataManager.getVehicleTrips(vehicleId, page, 20)) {
                is RemoteResult.Success -> {
                    trips = if (page == 1) {
                        result.data.data
                    } else {
                        trips + result.data.data
                    }
                    currentPage = page
                    hasNextPage = result.data.pagination.has_next
                }

                is RemoteResult.Error -> {
                    // Show error toast
                    activity.showErrorToast(
                        message = "Failed to load trips: ${result.message}",
                        title = "Load Error"
                    )
                }
            }

            isLoading = false
        }
    }

    // Function to delete trip
    fun deleteTrip(tripId: Int) {
        lifecycleScope.launch {
            isDeletingTrip = true

            when (val result = remoteDataManager.deleteTrip(tripId)) {
                is RemoteResult.Success -> {
                    // Show success toast
                    activity.showSuccessToast(
                        message = "Trip #$tripId deleted successfully",
                        title = "Trip Deleted"
                    )
                    // Remove the deleted trip from the list
                    trips = trips.filter { it.id != tripId }
                }

                is RemoteResult.Error -> {
                    // Show error toast
                    activity.showErrorToast(
                        message = "Failed to delete trip: ${result.message}",
                        title = "Delete Failed"
                    )
                }
            }

            isDeletingTrip = false
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                    TripCard(
                        trip = trip,
                        onDeleteTrip = { tripId -> deleteTrip(tripId) }
                    )
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

                // Loading indicator for delete operation
                if (isDeletingTrip) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deleting trip...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: TripResponse,
    onDeleteTrip: (Int) -> Unit
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val departureDate = Date(trip.departure_time * 1000)
    var showDeleteDialog by remember { mutableStateOf(false) }

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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    // Delete button - only show for SCHEDULED trips
                    if (trip.status == "SCHEDULED") {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete trip",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trip") },
            text = {
                Text("Are you sure you want to delete Trip #${trip.id}? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteTrip(trip.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTripTab(
    vehicleId: Int,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
) {
    var routes by remember { mutableStateOf<List<SaveRouteResponse>>(emptyList()) }
    var locations by remember { mutableStateOf<List<SavePlaceResponse>>(emptyList()) }
    var selectedRoute by remember { mutableStateOf<SaveRouteResponse?>(null) }
    var notes by remember { mutableStateOf("") }
    var isReversed by remember { mutableStateOf(false) }
    var customWaypoints by remember { mutableStateOf<List<CreateCustomWaypoint>>(emptyList()) }

    // Use Calendar for date/time selection
    var selectedDateTime by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1) // Default to 1 hour from now
        })
    }

    var noWaypoints by remember { mutableStateOf(false) }
    var tripPrice by remember { mutableStateOf("") }

    var isLoadingRoutes by remember { mutableStateOf(false) }
    var isLoadingLocations by remember { mutableStateOf(false) }
    var isCreatingTrip by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // Function to handle reverse route changes
    fun onReverseRouteChanged(reversed: Boolean) {
        isReversed = reversed
        if (reversed && customWaypoints.isNotEmpty()) {
            // Clear custom waypoints when reversing
            customWaypoints = emptyList()
            // Show toast message
            activity.showInfoToast(
                message = "Route reversed! Custom waypoints cleared.",
                title = "Route Reversed"
            )
        }
    }

    // Get reversed route data for display
    val displayRoute = remember(selectedRoute, isReversed) {
        selectedRoute?.let { route ->
            if (isReversed) {
                // Create a reversed version of the route for display
                route.copy(
                    origin = route.destination,
                    destination = route.origin,
                    waypoints = route.waypoints.reversed().mapIndexed { index, waypoint ->
                        waypoint.copy(order = route.waypoints.size - 1 - index)
                    }
                )
            } else {
                route
            }
        }
    }

    // Update trip price when route changes
    LaunchedEffect(selectedRoute) {
        selectedRoute?.let { route ->
            tripPrice = route.route_price.toString()
        }
    }

    // Load routes and locations on first composition
    LaunchedEffect(Unit) {
        // Load routes
        lifecycleScope.launch {
            isLoadingRoutes = true
            when (val result = remoteDataManager.getRoutes(page = 1, limit = 100)) {
                is RemoteResult.Success -> routes = result.data.data
                is RemoteResult.Error -> {
                    activity.showErrorToast(
                        message = "Failed to load routes: ${result.message}",
                        title = "Load Error"
                    )
                }
            }
            isLoadingRoutes = false
        }

        // Load locations
        lifecycleScope.launch {
            isLoadingLocations = true
            when (val result = remoteDataManager.getLocations(page = 1, limit = 100)) {
                is RemoteResult.Success -> locations = result.data.data
                is RemoteResult.Error -> {
                    activity.showErrorToast(
                        message = "Failed to load locations: ${result.message}",
                        title = "Load Error"
                )
                }
            }
            isLoadingLocations = false
        }
    }

    // Function to create trip
    fun createTrip() {
        if (selectedRoute == null) {
            activity.showWarningToast(
                message = "Please select a route",
                title = "Route Required"
            )
            return
        }

        val price = tripPrice.toDoubleOrNull()
        if (price == null || price <= 0) {
            activity.showWarningToast(
                message = "Please enter a valid trip price",
                title = "Invalid Price"
            )
            return
        }

        // Check if departure time is in the future
        val currentTime = java.util.Calendar.getInstance()
        if (selectedDateTime.timeInMillis <= currentTime.timeInMillis) {
            activity.showWarningToast(
                message = "Departure time must be in the future",
                title = "Invalid Time"
            )
            return
        }

        // Validate custom waypoints only if they exist and no_waypoints is false
        if (!noWaypoints && customWaypoints.isNotEmpty()) {
            val invalidWaypoints = customWaypoints.filter { it.location_id == 0 }
            if (invalidWaypoints.isNotEmpty()) {
                activity.showWarningToast(
                    message = "All custom waypoints must have a valid location selected",
                    title = "Invalid Waypoints"
                )
                return
            }

            // Check if any custom waypoint is missing a price
            val waypointsWithoutPrice = customWaypoints.filter { it.price == null || it.price <= 0.0 }
            if (waypointsWithoutPrice.isNotEmpty()) {
                activity.showWarningToast(
                    message = "All custom waypoints must have a valid price. Please complete the pricing for all waypoints.",
                    title = "Incomplete Waypoint Data"
                )
                return
            }
        }

        // Check if route has default waypoints without prices (only if not using custom waypoints)
        if (!noWaypoints && customWaypoints.isEmpty() && displayRoute?.waypoints?.isNotEmpty() == true) {
            val waypointsWithoutPrice = displayRoute!!.waypoints.filter { it.price <= 0.0 }
            if (waypointsWithoutPrice.isNotEmpty()) {
                activity.showWarningToast(
                    message = "Route has waypoints without prices. Please add custom waypoints with proper pricing or contact support.",
                    title = "Incomplete Waypoint Pricing"
                )
                return
            }
        }

        // Show confirmation dialog instead of creating trip directly
        showConfirmationDialog = true
    }

    // Get available locations for waypoints (excluding route origin and destination)
    val availableLocations = remember(displayRoute, locations) {
        displayRoute?.let { route ->
            locations.filter { location ->
                location.id != route.origin_id && location.id != route.destination_id
            }
        } ?: locations
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Route Selection with Search
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select Route",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SearchableRouteDropdown(
                            routes = routes,
                            selectedRoute = selectedRoute,
                            onRouteSelected = {
                                selectedRoute = it
                                customWaypoints = emptyList()
                                // Reset reverse state when new route is selected
                                isReversed = false
                                
                                // Show info toast when route is selected
                                it?.let { route ->
                                    activity.showInfoToast(
                                        message = "Route selected: ${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.google_place_name}",
                                        title = "Route Selected"
                                    )
                                }
                            },
                            isLoading = isLoadingRoutes
                        )

                        // Show selected route details (using displayRoute for reversed view)
                        displayRoute?.let { route ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Route Details",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        
                                        if (isReversed) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Text(
                                                    text = "REVERSED",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Compact route info in rows
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "From: ${route.origin.custom_name ?: route.origin.google_place_name}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "To: ${route.destination.custom_name ?: route.destination.google_place_name}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "${route.distance_meters}m",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "${route.estimated_duration_seconds}s",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (route.city_route) "City Route" else "Intercity Route",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${route.route_price} RWF",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Trip Configuration
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trip Configuration",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trip Price
                        OutlinedTextField(
                            value = tripPrice,
                            onValueChange = { tripPrice = it },
                            label = { Text("Trip Price (RWF)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter trip price") }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Departure Date & Time Selection
                        DateTimePickerSection(
                            selectedDateTime = selectedDateTime,
                            onDateTimeChanged = { selectedDateTime = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Compact toggles row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Only show No Waypoints if route has waypoints
                            if (selectedRoute?.waypoints?.isNotEmpty() == true) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = noWaypoints,
                                        onCheckedChange = {
                                            noWaypoints = it
                                            if (it) {
                                                customWaypoints = emptyList()
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "No Waypoints",
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            // Reverse Route Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isReversed,
                                    onCheckedChange = { onReverseRouteChanged(it) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reverse Route",
                                    fontSize = 14.sp
                                )
                            }
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

            // Route Waypoints Display (read-only)
            displayRoute?.let { route ->
                if (route.waypoints.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Route Waypoints",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    if (isReversed) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text(
                                                text = "REVERSED ORDER",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (isReversed) 
                                        "Default waypoints for this route (reversed order - will be used unless custom waypoints are added):"
                                    else
                                        "Default waypoints for this route (will be used unless custom waypoints are added):",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                route.waypoints.sortedBy { it.order }.forEach { waypoint ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = waypoint.location.custom_name
                                                    ?: waypoint.location.google_place_name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Order: ${waypoint.order + 1} | Price: ${waypoint.price} RWF",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

           // Custom Waypoints (only show if no_waypoints is false)
           if (!noWaypoints) {
               item {
                   Card(modifier = Modifier.fillMaxWidth()) {
                       Column(modifier = Modifier.padding(16.dp)) {
                           Row(
                               modifier = Modifier.fillMaxWidth(),
                               horizontalArrangement = Arrangement.SpaceBetween,
                               verticalAlignment = Alignment.CenterVertically
                           ) {
                               Row(
                                   verticalAlignment = Alignment.CenterVertically,
                                   horizontalArrangement = Arrangement.spacedBy(8.dp)
                               ) {
                                   Text(
                                       text = "Waypoints Configuration",
                                       fontSize = 16.sp,
                                       fontWeight = FontWeight.Bold
                                   )
                                   
                                   if (isReversed && selectedRoute?.waypoints?.isNotEmpty() == true) {
                                       Card(
                                           colors = CardDefaults.cardColors(
                                               containerColor = MaterialTheme.colorScheme.primary
                                           )
                                       ) {
                                           Text(
                                               text = "REVERSED",
                                               modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                               fontSize = 10.sp,
                                               fontWeight = FontWeight.Bold,
                                               color = MaterialTheme.colorScheme.onPrimary
                                           )
                                       }
                                   }
                               }

                               TextButton(
                                   onClick = {
                                       customWaypoints = customWaypoints + CreateCustomWaypoint(
                                           location_id = 0,
                                           order = customWaypoints.size,
                                           price = null
                                       )
                                       
                                       // Show info toast when waypoint is added
                                       activity.showInfoToast(
                                           message = "Custom waypoint added. Please select a location.",
                                           title = "Waypoint Added"
                                       )
                                   }
                               ) {
                                   Text("+ Add Custom")
                               }
                           }

                           Spacer(modifier = Modifier.height(8.dp))

                           // Show route's default waypoints if they exist
                           displayRoute?.let { route ->
                               if (route.waypoints.isNotEmpty()) {
                                   Text(
                                       text = "Route Default Waypoints:",
                                       fontSize = 14.sp,
                                       fontWeight = FontWeight.Medium,
                                       color = MaterialTheme.colorScheme.primary
                                   )

                                   Spacer(modifier = Modifier.height(8.dp))

                                   route.waypoints.sortedBy { it.order }.forEach { waypoint ->
                                       Card(
                                           colors = CardDefaults.cardColors(
                                               containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                           ),
                                           modifier = Modifier.padding(vertical = 2.dp)
                                       ) {
                                           Row(
                                               modifier = Modifier.padding(12.dp),
                                               horizontalArrangement = Arrangement.SpaceBetween,
                                               verticalAlignment = Alignment.CenterVertically
                                           ) {
                                               Column(modifier = Modifier.weight(1f)) {
                                                   Text(
                                                       text = waypoint.location.custom_name ?: waypoint.location.google_place_name,
                                                       fontSize = 14.sp,
                                                       fontWeight = FontWeight.Medium
                                                   )
                                                   Text(
                                                       text = "Order: ${waypoint.order + 1}",
                                                       fontSize = 12.sp,
                                                       color = MaterialTheme.colorScheme.onSurfaceVariant
                                                   )
                                               }
                                               Text(
                                                   text = "${waypoint.price} RWF",
                                                   fontSize = 12.sp,
                                                   fontWeight = FontWeight.Medium,
                                                   color = MaterialTheme.colorScheme.onSurfaceVariant
                                               )
                                           }
                                       }
                                   }

                                   if (customWaypoints.isNotEmpty()) {
                                       Spacer(modifier = Modifier.height(12.dp))
                                       Text(
                                           text = if (isReversed) 
                                               "Custom Waypoints (will override reversed defaults):"
                                           else
                                               "Custom Waypoints (will override defaults):",
                                           fontSize = 14.sp,
                                           fontWeight = FontWeight.Medium,
                                           color = MaterialTheme.colorScheme.error
                                       )
                                   }
                               }
                           }

                           // Custom waypoints section
                           if (customWaypoints.isNotEmpty()) {
                               Spacer(modifier = Modifier.height(8.dp))
                               customWaypoints.forEachIndexed { index, waypoint ->
                                   Spacer(modifier = Modifier.height(8.dp))
                                   CustomWaypointCard(
                                       waypoint = waypoint,
                                       index = index,
                                       availableLocations = availableLocations,
                                       isLoadingLocations = isLoadingLocations,
                                       canMoveUp = index > 0,
                                       canMoveDown = index < customWaypoints.size - 1,
                                       onLocationChanged = { locationId ->
                                           customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                               if (i == index) wp.copy(location_id = locationId)
                                               else wp
                                           }
                                       },
                                       onPriceChanged = { price ->
                                           customWaypoints = customWaypoints.mapIndexed { i, wp ->
                                               if (i == index) wp.copy(price = price)
                                               else wp
                                           }
                                       },
                                       onMoveUp = {
                                           val newList = customWaypoints.toMutableList()
                                           val temp = newList[index]
                                           newList[index] = newList[index - 1].copy(order = index)
                                           newList[index - 1] = temp.copy(order = index - 1)
                                           customWaypoints = newList
                                       },
                                       onMoveDown = {
                                           val newList = customWaypoints.toMutableList()
                                           val temp = newList[index]
                                           newList[index] = newList[index + 1].copy(order = index)
                                           newList[index + 1] = temp.copy(order = index + 1)
                                           customWaypoints = newList
                                       },
                                       onRemove = {
                                           customWaypoints = customWaypoints.filterIndexed { i, _ -> i != index }
                                               .mapIndexed { newIndex, wp ->
                                                   wp.copy(order = newIndex)
                                               }
                                       }
                                   )
                               }
                           } else if (selectedRoute?.waypoints?.isEmpty() == true) {
                               Spacer(modifier = Modifier.height(8.dp))
                               Text(
                                   text = "This route has no default waypoints. Add custom waypoints if needed.",
                                   fontSize = 14.sp,
                                   color = MaterialTheme.colorScheme.onSurfaceVariant
                               )
                           }
                           
                           // Show note about reversed route
                           if (isReversed && selectedRoute?.waypoints?.isNotEmpty() == true) {
                               Spacer(modifier = Modifier.height(8.dp))
                               Card(
                                   colors = CardDefaults.cardColors(
                                       containerColor = MaterialTheme.colorScheme.primaryContainer
                                   )
                               ) {
                                   Column(modifier = Modifier.padding(8.dp)) {
                                       Text(
                                           text = "⚠️ Route Reversed",
                                           fontSize = 12.sp,
                                           fontWeight = FontWeight.Bold,
                                           color = MaterialTheme.colorScheme.onPrimaryContainer
                                       )
                                       Text(
                                           text = "Origin and destination are swapped. Waypoints are in reverse order.",
                                           fontSize = 11.sp,
                                           color = MaterialTheme.colorScheme.onPrimaryContainer
                                       )
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
                    enabled = selectedRoute != null && tripPrice.isNotBlank() && !isCreatingTrip
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

    // Simple Confirmation Dialog
    if (showConfirmationDialog) {
        val dateTimeFormatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Confirm Trip Creation") },
            text = {
                Column {
                    // Show route summary
                    displayRoute?.let { route ->
                        if (noWaypoints || (route.waypoints.isEmpty() && customWaypoints.isEmpty())) {
                            // Direct route A → B
                            Text("Route: ${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.custom_name ?: route.destination.google_place_name}")
                        } else {
                            // Route with waypoints A → B → C → D
                            val allWaypoints = if (customWaypoints.isNotEmpty()) {
                                customWaypoints.sortedBy { it.order }
                            } else {
                                route.waypoints.sortedBy { it.order }
                            }
                            
                            val routeString = buildString {
                                append(route.origin.custom_name ?: route.origin.google_place_name)
                                allWaypoints.forEach { waypoint ->
                                    val locationName = if (customWaypoints.isNotEmpty()) {
                                        val customWaypoint = waypoint as CreateCustomWaypoint
                                        availableLocations.find { it.id == customWaypoint.location_id }?.custom_name 
                                            ?: availableLocations.find { it.id == customWaypoint.location_id }?.google_place_name 
                                            ?: "Unknown"
                                    } else {
                                        val routeWaypoint = waypoint as RouteWaypoint
                                        routeWaypoint.location.custom_name ?: routeWaypoint.location.google_place_name
                                    }
                                    append(" → $locationName")
                                }
                                append(" → ${route.destination.custom_name ?: route.destination.google_place_name}")
                            }
                            Text("Route: $routeString")
                        }
                        
                        if (isReversed) Text("⚠️ Route will be REVERSED")
                        Text("Trip Price: $tripPrice RWF")
                        Text("Departure: ${dateTimeFormatter.format(selectedDateTime.time)}")
                        
                        // Show waypoint prices if applicable
                        if (!noWaypoints && (route.waypoints.isNotEmpty() || customWaypoints.isNotEmpty())) {
                            if (customWaypoints.isNotEmpty()) {
                                customWaypoints.sortedBy { it.order }.forEachIndexed { index, waypoint ->
                                    val location = availableLocations.find { it.id == waypoint.location_id }
                                    Text("Waypoint ${index + 1}: ${location?.custom_name ?: location?.google_place_name ?: "Unknown"} - ${waypoint.price} RWF")
                                }
                            } else {
                                route.waypoints.sortedBy { it.order }.forEach { waypoint ->
                                    Text("Waypoint ${waypoint.order + 1}: ${waypoint.location.custom_name ?: waypoint.location.google_place_name} - ${waypoint.price} RWF")
                                }
                            }
                        }
                        
                        if (notes.isNotBlank()) Text("Notes: $notes")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        // Proceed with trip creation
                        lifecycleScope.launch {
                            isCreatingTrip = true
                            val price = tripPrice.toDoubleOrNull()
                            if (price == null || price <= 0) {
                                activity.showErrorToast(
                                    message = "Invalid trip price. Please enter a valid price.",
                                    title = "Invalid Price"
                                )
                                isCreatingTrip = false
                                return@launch
                            }

                            // Check if departure time is in the future
                            val currentTime = java.util.Calendar.getInstance()
                            if (selectedDateTime.timeInMillis <= currentTime.timeInMillis) {
                                activity.showErrorToast(
                                    message = "Departure time must be in the future. Please select a future date and time.",
                                    title = "Invalid Time"
                                )
                                isCreatingTrip = false
                                return@launch
                            }

                            // Validate custom waypoints only if they exist and no_waypoints is false
                            if (!noWaypoints && customWaypoints.isNotEmpty()) {
                                val invalidWaypoints = customWaypoints.filter { it.location_id == 0 }
                                if (invalidWaypoints.isNotEmpty()) {
                                    activity.showErrorToast(
                                        message = "All custom waypoints must have a valid location selected.",
                                        title = "Invalid Waypoints"
                                    )
                                    isCreatingTrip = false
                                    return@launch
                                }

                                // Check if any custom waypoint is missing a price
                                val waypointsWithoutPrice = customWaypoints.filter { it.price == null || it.price <= 0.0 }
                                if (waypointsWithoutPrice.isNotEmpty()) {
                                    activity.showErrorToast(
                                        message = "All custom waypoints must have a valid price. Please complete the pricing for all waypoints.",
                                        title = "Incomplete Waypoint Data"
                                    )
                                    isCreatingTrip = false
                                    return@launch
                                }
                            }

                            // Check if route has default waypoints without prices (only if not using custom waypoints)
                            if (!noWaypoints && customWaypoints.isEmpty() && displayRoute?.waypoints?.isNotEmpty() == true) {
                                val waypointsWithoutPrice = displayRoute!!.waypoints.filter { it.price <= 0.0 }
                                if (waypointsWithoutPrice.isNotEmpty()) {
                                    activity.showErrorToast(
                                        message = "Route has waypoints without prices. Please add custom waypoints with proper pricing or contact support.",
                                        title = "Incomplete Waypoint Pricing"
                                    )
                                    isCreatingTrip = false
                                    return@launch
                                }
                            }

                            val createTripRequest = CreateTripRequest(
                                route_id = selectedRoute!!.id,
                                vehicle_id = vehicleId,
                                price = price,
                                departure_time = selectedDateTime.timeInMillis / 1000, // Convert to seconds
                                connection_mode = "ONLINE", // Always online
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
                                    
                                    // Show success toast
                                    activity.showSuccessToast(
                                        message = "Trip created successfully! Trip ID: ${result.data.id} ($tripType)",
                                        title = "Trip Created"
                                    )

                                    // Reset form
                                    selectedRoute = null
                                    notes = ""
                                    isReversed = false
                                    customWaypoints = emptyList()
                                    noWaypoints = false
                                    tripPrice = ""
                                    selectedDateTime = java.util.Calendar.getInstance().apply {
                                        add(java.util.Calendar.HOUR_OF_DAY, 1)
                                    }
                                }

                                is RemoteResult.Error -> {
                                    // Show error toast
                                    activity.showErrorToast(
                                        message = "Failed to create trip: ${result.message}",
                                        title = "Creation Failed"
                                    )
                                }
                            }
                            isCreatingTrip = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Create Trip", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerSection(
    selectedDateTime: java.util.Calendar,
    onDateTimeChanged: (java.util.Calendar) -> Unit
) {
    val dateTimeFormatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    var showDateTimePicker by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Departure Date & Time",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Single Date-Time Picker Button
        OutlinedButton(
            onClick = { showDateTimePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📅 🕐")
                Text(dateTimeFormatter.format(selectedDateTime.time))
            }
        }
    }

    // Combined Date-Time Picker Dialog
    if (showDateTimePicker) {
        DateTimePickerDialog(
            selectedDateTime = selectedDateTime,
            onDateTimeSelected = { newDateTime ->
                onDateTimeChanged(newDateTime)
                showDateTimePicker = false
            },
            onDismiss = { showDateTimePicker = false }
        )
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    selectedDateTime: java.util.Calendar,
    onDateTimeSelected: (java.util.Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) } // Start with Time tab (0)

    // Initialize with current time rounded to next 5-minute interval
    var selectedHour by remember {
        val currentTime = java.util.Calendar.getInstance()
        val hour = currentTime.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(java.util.Calendar.MINUTE)
        // If we're past 55 minutes, move to next hour
        mutableIntStateOf(if (minute >= 55) (hour + 1) % 24 else hour)
    }

    var selectedMinute by remember {
        val currentTime = java.util.Calendar.getInstance()
        val minute = currentTime.get(java.util.Calendar.MINUTE)
        // Round up to next 5-minute interval
        val nextInterval = ((minute / 5) + 1) * 5
        mutableIntStateOf(if (nextInterval >= 60) 0 else nextInterval)
    }

    // Calculate the date based on selected time
    val calculatedDate = remember(selectedHour, selectedMinute) {
        val now = java.util.Calendar.getInstance()
        val selectedTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
            set(java.util.Calendar.MINUTE, selectedMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        // If selected time is before current time, use tomorrow
        if (selectedTime.timeInMillis <= now.timeInMillis) {
            selectedTime.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        selectedTime
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calculatedDate.timeInMillis
    )

    // Hour options (0-23)
    val hourOptions = (0..23).map { String.format("%02d", it) }

    // Minute options in 5-minute intervals
    val minuteOptions = (0..59 step 5).map { String.format("%02d", it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date & Time") },
        text = {
            Column {
                // Tab Row - Time first, then Date
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Time") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Date") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (currentTab) {
                    0 -> {
                        // Time Picker with dropdowns
                        Column {
                            Text(
                                text = "Select Time",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hour Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hour", fontSize = 12.sp)
                                    var hourExpanded by remember { mutableStateOf(false) }

                                    ExposedDropdownMenuBox(
                                        expanded = hourExpanded,
                                        onExpandedChange = { hourExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = String.format("%02d", selectedHour),
                                            onValueChange = { },
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = hourExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = hourExpanded,
                                            onDismissRequest = { hourExpanded = false }
                                        ) {
                                            hourOptions.forEachIndexed { index, hour ->
                                                DropdownMenuItem(
                                                    text = { Text(hour) },
                                                    onClick = {
                                                        selectedHour = index
                                                        hourExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)

                                // Minute Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Minute", fontSize = 12.sp)
                                    var minuteExpanded by remember { mutableStateOf(false) }

                                    ExposedDropdownMenuBox(
                                        expanded = minuteExpanded,
                                        onExpandedChange = { minuteExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = String.format("%02d", selectedMinute),
                                            onValueChange = { },
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = minuteExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = minuteExpanded,
                                            onDismissRequest = { minuteExpanded = false }
                                        ) {
                                            minuteOptions.forEach { minute ->
                                                DropdownMenuItem(
                                                    text = { Text(minute) },
                                                    onClick = {
                                                        selectedMinute = minute.toInt()
                                                        minuteExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Show calculated date
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Date will be:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SimpleDateFormat(
                                            "EEEE, MMM dd, yyyy",
                                            Locale.getDefault()
                                        ).format(calculatedDate.time),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (calculatedDate.get(java.util.Calendar.DAY_OF_YEAR) !=
                                        java.util.Calendar.getInstance()
                                            .get(java.util.Calendar.DAY_OF_YEAR)
                                    ) {
                                        Text(
                                            text = "Tomorrow (selected time is past today)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Date Picker (for manual override)
                        Column {
                            Text(
                                text = "Override Date (Optional)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Date is automatically set based on selected time. Use this to override.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DatePicker(
                                state = datePickerState,
                                modifier = Modifier.height(400.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview
                val previewDateTime = java.util.Calendar.getInstance().apply {
                    timeInMillis = datePickerState.selectedDateMillis ?: calculatedDate.timeInMillis
                    set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                    set(java.util.Calendar.MINUTE, selectedMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Selected:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = SimpleDateFormat(
                                "MMM dd, yyyy 'at' HH:mm",
                                Locale.getDefault()
                            ).format(previewDateTime.time),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalDateTime = java.util.Calendar.getInstance().apply {
                        timeInMillis =
                            datePickerState.selectedDateMillis ?: calculatedDate.timeInMillis
                        set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                        set(java.util.Calendar.MINUTE, selectedMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }

                    // Validate that the selected time is in the future
                    val currentTime = java.util.Calendar.getInstance()
                    if (finalDateTime.timeInMillis > currentTime.timeInMillis) {
                        onDateTimeSelected(finalDateTime)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomWaypointCard(
    waypoint: CreateCustomWaypoint,
    index: Int,
    availableLocations: List<SavePlaceResponse>,
    isLoadingLocations: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLocationChanged: (Int) -> Unit,
    onPriceChanged: (Double?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
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
                    text = "Waypoint ${index + 1}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canMoveUp) {
                        TextButton(onClick = onMoveUp) { Text("↑") }
                    }
                    if (canMoveDown) {
                        TextButton(onClick = onMoveDown) { Text("↓") }
                    }
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location Selection
            var expanded by remember { mutableStateOf(false) }
            val selectedLocation = availableLocations.find { it.id == waypoint.location_id }

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
                        availableLocations.forEach { location ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = location.custom_name
                                                ?: location.google_place_name,
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
                                    onLocationChanged(location.id)
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
                    onPriceChanged(price)
                },
                label = { Text("Price (RWF) - Optional") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Leave empty for default pricing") }
            )
        }
    }
}