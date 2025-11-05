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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

        // Show license plate below tab switcher only on View Trips tab
        if (selectedTabIndex == 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vehicle: ${licensePlate ?: "Unknown"}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
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
    isLoading: Boolean,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchFilters by remember { mutableStateOf(RouteSearchFilters()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SaveRouteResponse>>(emptyList()) }

    // Parse search text into filters
    val currentFilters = remember(searchText) {
        RouteSearchParser.parseSearchText(searchText)
    }

    // Load search results when filters change
    LaunchedEffect(currentFilters) {
        if (searchText.isNotBlank()) {
            isSearching = true
            searchFilters = currentFilters
            
            when (val result = remoteDataManager.getRoutes(
                origin = currentFilters.origin,
                destination = currentFilters.destination,
                cityRoute = currentFilters.cityRoute,
                page = 1,
                limit = 50
            )) {
                is RemoteResult.Success -> {
                    searchResults = result.data.data
                }
                is RemoteResult.Error -> {
                    activity.showErrorToast(
                        message = "Search failed: ${result.message}",
                        title = "Search Error"
                    )
                    searchResults = emptyList()
                }
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            searchFilters = RouteSearchFilters()
        }
    }

    val filteredRoutes = remember(searchResults, searchText) {
        if (searchText.isBlank()) {
            // Show some default routes when search is empty
            routes.take(5)
        } else {
            // If we have search results from API, use them
            // Otherwise fall back to local filtering
            if (searchResults.isNotEmpty()) {
                searchResults.take(10)
            } else {
                routes.filter { route ->
                    val origin = route.origin.custom_name ?: route.origin.google_place_name
                    val destination =
                        route.destination.custom_name ?: route.destination.google_place_name
                    origin.contains(searchText, ignoreCase = true) ||
                            destination.contains(searchText, ignoreCase = true)
                }.take(10)
            }
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
                placeholder = { Text(RouteSearchParser.getPlaceholderText(searchFilters)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                interactionSource = interactionSource
            )
            
            // Update suggestions visibility based on focus and text
            LaunchedEffect(isFocused, searchText) {
                showSuggestions = isFocused && (searchText.isNotBlank() || routes.isNotEmpty())
            }

            // Search status indicator
            if (searchText.isNotBlank() && searchFilters != RouteSearchFilters()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 56.dp)
                        .zIndex(8f), // Lower than suggestions but above other content
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🔍 ${RouteSearchParser.getSearchDescription(searchFilters)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // True popup suggestions - appears outside card boundaries
            if (showSuggestions && (filteredRoutes.isNotEmpty() || isSearching)) {
                // This will be rendered outside the card using a separate composable
                RouteSearchPopup(
                    searchText = searchText,
                    searchFilters = searchFilters,
                    filteredRoutes = filteredRoutes,
                    isSearching = isSearching,
                    onRouteSelected = { route ->
                        onRouteSelected(route)
                        searchText = ""
                        showSuggestions = false
                    },
                    onDismiss = { showSuggestions = false }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableLocationDropdown(
    selectedLocation: SavePlaceResponse?,
    onLocationSelected: (SavePlaceResponse?) -> Unit,
    isLoading: Boolean,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity,
    label: String = "Location"
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SavePlaceResponse>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var internalSelectedLocation by remember { mutableStateOf<SavePlaceResponse?>(selectedLocation) }
    
    // Update internal selected location when prop changes
    LaunchedEffect(selectedLocation) {
        internalSelectedLocation = selectedLocation
        if (selectedLocation != null && searchText.isEmpty()) {
            searchText = selectedLocation.custom_name ?: selectedLocation.google_place_name
        }
    }

    // Load search results when search text changes
    LaunchedEffect(searchText) {
        if (searchText.isNotBlank() && searchText.length >= 2) { // Minimum 2 characters to search
            isSearching = true
            Log.d("SearchableLocationDropdown", "Searching for: $searchText")
            
            when (val result = remoteDataManager.getLocations(
                search = searchText,
                page = 1,
                limit = 20
            )) {
                is RemoteResult.Success -> {
                    searchResults = result.data.data
                    Log.d("SearchableLocationDropdown", "Found ${searchResults.size} locations")
                }
                is RemoteResult.Error -> {
                    activity.showErrorToast(
                        message = "Search failed: ${result.message}",
                        title = "Search Error"
                    )
                    searchResults = emptyList()
                    showSuggestions = false
                }
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            showSuggestions = false
        }
    }

    Log.d("SearchableLocationDropdown", "selectedLocation: $selectedLocation, internalSelectedLocation: $internalSelectedLocation, showing search input: ${internalSelectedLocation == null}")
    if (internalSelectedLocation == null) {
        // Show search input with auto-suggestions
        Box(modifier = Modifier.fillMaxWidth()) {
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            OutlinedTextField(
                value = searchText,
                onValueChange = { 
                    searchText = it
                    showSuggestions = it.isNotBlank()
                    Log.d("SearchableLocationDropdown", "Text changed to: '$it', showSuggestions: $showSuggestions")
                },
                label = { Text(label) },
                placeholder = { Text("Type to search locations...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                interactionSource = interactionSource,
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
            
            // Update suggestions visibility based on focus and text
            LaunchedEffect(isFocused, searchText) {
                showSuggestions = isFocused && searchText.isNotBlank()
            }

            // Auto-suggestions dropdown - position above input for keyboard visibility
            Log.d("SearchableLocationDropdown", "showSuggestions: $showSuggestions, searchResults.size: ${searchResults.size}, isSearching: $isSearching")
            if (showSuggestions && (searchResults.isNotEmpty() || isSearching)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 56.dp) // Position below the input field
                        .zIndex(10f), // Lower than route search to avoid conflicts
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isSearching) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Text("Searching locations...")
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No locations found matching your search",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(searchResults) { location ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = location.custom_name ?: location.google_place_name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                            Text(
                                                text = "${location.district}, ${location.province}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        Log.d("SearchableLocationDropdown", "Location selected: ${location.custom_name ?: location.google_place_name} (ID: ${location.id})")
                                        internalSelectedLocation = location
                                        onLocationSelected(location)
                                        showSuggestions = false
                                        // Update search text to show the selected location
                                        searchText = location.custom_name ?: location.google_place_name
                                        Log.d("SearchableLocationDropdown", "After selection - internalSelectedLocation set, searchText: $searchText")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show selected location with clear button
        Log.d("SearchableLocationDropdown", "Showing selected location card for: ${internalSelectedLocation?.custom_name ?: internalSelectedLocation?.google_place_name}")
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
                        text = internalSelectedLocation?.custom_name ?: internalSelectedLocation?.google_place_name ?: "Unknown Location",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${internalSelectedLocation?.district ?: ""}, ${internalSelectedLocation?.province ?: ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                IconButton(
                    onClick = { 
                        Log.d("SearchableLocationDropdown", "Clearing location selection")
                        internalSelectedLocation = null
                        onLocationSelected(null)
                        searchText = ""
                        showSuggestions = false
                        searchResults = emptyList()
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
private fun RouteSearchPopup(
    searchText: String,
    searchFilters: RouteSearchFilters,
    filteredRoutes: List<SaveRouteResponse>,
    isSearching: Boolean,
    onRouteSelected: (SaveRouteResponse) -> Unit,
    onDismiss: () -> Unit
) {
    // This popup renders outside the card boundaries
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f) // Very high z-index to appear over everything
    ) {
        // Semi-transparent overlay to catch clicks outside
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.3f)
            ) {}
        }
        
        // Popup content positioned at the top
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .offset(y = 100.dp), // Position below the search field
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Scrollable height
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Results",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp)
                    }
                }
                
                Divider()
                
                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (isSearching) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Searching routes...")
                            }
                        }
                    } else if (filteredRoutes.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (searchText.isBlank()) 
                                        "No routes available. Try searching for specific routes."
                                    else 
                                        "No routes found matching your search",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredRoutes) { route ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onRouteSelected(route)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.custom_name ?: route.destination.google_place_name}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
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
private fun ViewTripsTab(
    vehicleId: Int,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
) {
    var trips by remember { mutableStateOf<List<TripResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isDeletingTrip by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Function to load trips
    fun loadTrips(page: Int = 1, isRefresh: Boolean = false) {
        lifecycleScope.launch {
            if (isRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            errorMessage = null

            when (val result = remoteDataManager.getVehicleTrips(vehicleId, page, 20)) {
                is RemoteResult.Success -> {
                    trips = if (page == 1) {
                        result.data.data
                    } else {
                        trips + result.data.data
                    }
                    currentPage = page
                    hasNextPage = result.data.pagination.has_next
                    isLoading = false
                    isRefreshing = false
                    errorMessage = null
                }

                is RemoteResult.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    isRefreshing = false
                    // Show error toast
                    activity.showErrorToast(
                        message = "Failed to load trips: ${result.message}",
                        title = "Load Error"
                    )
                }
            }
        }
    }

    // Function to delete/cancel trip
    fun deleteTrip(tripId: Int) {
        lifecycleScope.launch {
            isDeletingTrip = true

            val tripStatus = trips.find { it.id == tripId }?.status
            val isCanceling = tripStatus == "IN_PROGRESS" || tripStatus == "SCHEDULED"
            val isDeleting = tripStatus == "CANCELLED"

            when (val result = remoteDataManager.deleteTrip(tripId)) {
                is RemoteResult.Success -> {
                    // Show success toast
                    val action = when {
                        isCanceling -> "canceled"
                        isDeleting -> "deleted"
                        else -> "deleted"
                    }
                    activity.showSuccessToast(
                        message = "Trip #$tripId $action successfully",
                        title = if (action == "canceled") "Trip Canceled" else "Trip Deleted"
                    )
                    
                    // For canceling (IN_PROGRESS/SCHEDULED), refresh the list to show updated status
                    // Trip stays in list but status changes to CANCELLED
                    // For deleting (CANCELLED), remove from list
                    if (isCanceling) {
                        loadTrips(1, isRefresh = true) // Refresh the list to show updated status (now CANCELLED)
                    } else if (isDeleting) {
                        trips = trips.filter { it.id != tripId }
                    }
                }

                is RemoteResult.Error -> {
                    // Show error toast
                    val action = when {
                        isCanceling -> "cancel"
                        isDeleting -> "delete"
                        else -> "delete"
                    }
                    activity.showErrorToast(
                        message = "Failed to $action trip: ${result.message}",
                        title = if (action == "cancel") "Cancel Failed" else "Delete Failed"
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
        if (isLoading && trips.isEmpty() && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && trips.isEmpty()) {
            // Show error with retry button when no trips loaded
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = { loadTrips(1) }
                    ) {
                        Text("Retry")
                    }
                }
            }
        } else if (trips.isEmpty() && !isLoading) {
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
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing),
                onRefresh = { loadTrips(1, isRefresh = true) }
            ) {
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
                                "IN_PROGRESS" -> MaterialTheme.colorScheme.secondaryContainer
                                "COMPLETED" -> MaterialTheme.colorScheme.tertiaryContainer
                                "CANCELLED" -> MaterialTheme.colorScheme.errorContainer
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

                    // Cancel button for IN_PROGRESS and SCHEDULED trips
                    if (trip.status == "IN_PROGRESS" || trip.status == "SCHEDULED") {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel trip",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Delete button for CANCELLED trips
                    if (trip.status == "CANCELLED") {
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
                    
                    // COMPLETED trips have no delete/cancel button
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
            title = { 
                Text(
                    when (trip.status) {
                        "IN_PROGRESS", "SCHEDULED" -> "Cancel Trip"
                        "CANCELLED" -> "Delete Trip"
                        else -> "Delete Trip"
                    }
                ) 
            },
            text = {
                Text(
                    when (trip.status) {
                        "IN_PROGRESS", "SCHEDULED" -> "Are you sure you want to cancel Trip #${trip.id}? This will cancel the trip and then delete it."
                        "CANCELLED" -> "Are you sure you want to permanently delete Trip #${trip.id}? This action cannot be undone."
                        else -> "Are you sure you want to delete Trip #${trip.id}? This action cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteTrip(trip.id)
                    }
                ) {
                    Text(
                        when (trip.status) {
                            "IN_PROGRESS", "SCHEDULED" -> "Cancel"
                            "CANCELLED" -> "Delete"
                            else -> "Delete"
                        }, 
                        color = MaterialTheme.colorScheme.error
                    )
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

// Internal data class for unified waypoint management
private data class UnifiedWaypoint(
    val location_id: Int,
    val location: SavePlaceResponse,
    val order: Int,
    val price: Double?,
    val isFromRoute: Boolean, // true if from original route, false if custom/edited
    val originalRouteWaypointId: Int? // null if custom, id if from route
)

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
    
    // Unified waypoint state
    var unifiedWaypoints by remember { mutableStateOf<List<UnifiedWaypoint>>(emptyList()) }

    // Use Calendar for date/time selection
    var selectedDateTime by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1) // Default to 1 hour from now
        })
    }

    var noWaypoints by remember { mutableStateOf(false) }
    var tripPrice by remember { mutableStateOf("") }
    
    // Track if user intentionally cleared waypoints
    var waypointsWereCleared by remember { mutableStateOf(false) }

    var isLoadingRoutes by remember { mutableStateOf(false) }
    var isLoadingLocations by remember { mutableStateOf(false) }
    var isCreatingTrip by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // Function to handle reverse route changes
    fun onReverseRouteChanged(reversed: Boolean) {
        isReversed = reversed
        // Waypoints will be reinitialized by LaunchedEffect(selectedRoute, isReversed)
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

    // Initialize unified waypoints from route
    // Don't reinitialize if user intentionally cleared waypoints
    LaunchedEffect(selectedRoute, isReversed) {
        // If waypoints were cleared, don't reinitialize when toggling reverse
        if (waypointsWereCleared && unifiedWaypoints.isEmpty()) {
            return@LaunchedEffect
        }
        
        selectedRoute?.let { route ->
            val routeWaypoints = if (isReversed) {
                route.waypoints.reversed().mapIndexed { index, waypoint ->
                    waypoint.copy(order = route.waypoints.size - 1 - index)
                }
            } else {
                route.waypoints
            }
            
            unifiedWaypoints = routeWaypoints.map { waypoint ->
                UnifiedWaypoint(
                    location_id = waypoint.location_id,
                    location = waypoint.location,
                    order = waypoint.order,
                    price = waypoint.price,
                    isFromRoute = true,
                    originalRouteWaypointId = waypoint.id
                )
            }
            
            // Update noWaypoints based on waypoint count
            noWaypoints = unifiedWaypoints.isEmpty()
            waypointsWereCleared = false // Reset flag when route changes
        } ?: run {
            unifiedWaypoints = emptyList()
            noWaypoints = false
            waypointsWereCleared = false
        }
    }

    // Waypoint editing handlers
    fun handlePriceChange(index: Int, newPrice: Double?) {
        unifiedWaypoints = unifiedWaypoints.mapIndexed { i, wp ->
            if (i == index) {
                // Convert to custom if it was from route
                wp.copy(
                    price = newPrice,
                    isFromRoute = false
                )
            } else wp
        }
    }

    fun handleDelete(index: Int) {
        unifiedWaypoints = unifiedWaypoints.filterIndexed { i, _ -> i != index }
            .mapIndexed { newIndex, wp -> wp.copy(order = newIndex) }
        noWaypoints = unifiedWaypoints.isEmpty()
        // Mark that waypoints were cleared if list becomes empty
        if (unifiedWaypoints.isEmpty() && selectedRoute?.waypoints?.isNotEmpty() == true) {
            waypointsWereCleared = true
        }
    }

    fun handleMoveUp(index: Int) {
        if (index > 0) {
            val newList = unifiedWaypoints.toMutableList()
            val temp = newList[index]
            newList[index] = newList[index - 1].copy(order = index)
            newList[index - 1] = temp.copy(order = index - 1)
            unifiedWaypoints = newList
        }
    }

    fun handleMoveDown(index: Int) {
        if (index < unifiedWaypoints.size - 1) {
            val newList = unifiedWaypoints.toMutableList()
            val temp = newList[index]
            newList[index] = newList[index + 1].copy(order = index)
            newList[index + 1] = temp.copy(order = index + 1)
            unifiedWaypoints = newList
        }
    }

    fun handleRevertToRoute() {
        selectedRoute?.let { route ->
            val routeWaypoints = if (isReversed) {
                route.waypoints.reversed().mapIndexed { index, waypoint ->
                    waypoint.copy(order = route.waypoints.size - 1 - index)
                }
            } else {
                route.waypoints
            }
            
            unifiedWaypoints = routeWaypoints.map { waypoint ->
                UnifiedWaypoint(
                    location_id = waypoint.location_id,
                    location = waypoint.location,
                    order = waypoint.order,
                    price = waypoint.price,
                    isFromRoute = true,
                    originalRouteWaypointId = waypoint.id
                )
            }
            noWaypoints = false
            waypointsWereCleared = false // Reset flag when reverting
        }
    }

    fun handleAddWaypoint(location: SavePlaceResponse) {
        val newWaypoint = UnifiedWaypoint(
            location_id = location.id,
            location = location,
            order = unifiedWaypoints.size,
            price = null,
            isFromRoute = false,
            originalRouteWaypointId = null
        )
        unifiedWaypoints = unifiedWaypoints + newWaypoint
        noWaypoints = false
    }

    // Load locations on first composition (routes will be loaded via search)
    LaunchedEffect(Unit) {
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

    // Function to validate and show confirmation dialog
    fun validateAndShowConfirmation() {
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

        // Validate unified waypoints if they exist
        if (!noWaypoints && unifiedWaypoints.isNotEmpty()) {
            val invalidWaypoints = unifiedWaypoints.filter { it.location_id == 0 }
            if (invalidWaypoints.isNotEmpty()) {
                activity.showWarningToast(
                    message = "All waypoints must have a valid location",
                    title = "Invalid Waypoints"
                )
                return
            }

            // Check if any waypoint is missing a price
            val waypointsWithoutPrice = unifiedWaypoints.filter { it.price == null || it.price <= 0.0 }
            if (waypointsWithoutPrice.isNotEmpty()) {
                activity.showWarningToast(
                    message = "All waypoints must have a valid price. Please complete the pricing for all waypoints.",
                    title = "Incomplete Waypoint Data"
                )
                return
            }
        }

        // Show confirmation dialog instead of creating trip directly
        showConfirmationDialog = true
    }

    // Get available locations for waypoints (excluding route origin and destination)
    val availableLocations = remember(displayRoute, locations, unifiedWaypoints) {
        displayRoute?.let { route ->
            val filteredLocations = locations.filter { location ->
                location.id != route.origin_id && location.id != route.destination_id
            }
            // Include any already selected locations from unified waypoints
            val selectedLocationIds = unifiedWaypoints.map { it.location_id }.filter { it > 0 }
            val selectedLocations = locations.filter { it.id in selectedLocationIds }
            (filteredLocations + selectedLocations).distinctBy { it.id }
        } ?: locations
    }

    var showAddWaypointDialog by remember { mutableStateOf(false) }
    var selectedLocationForWaypoint by remember { mutableStateOf<SavePlaceResponse?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
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
                                Log.d("CreateTripTab", "Route selected: ${it?.id}")
                                selectedRoute = it
                                // Don't clear custom waypoints - let user decide if they want to override
                                // Reset reverse state when new route is selected
                                isReversed = false
                                // Reset cleared flag when new route is selected
                                waypointsWereCleared = false
                                
                                // Show info toast when route is selected
                                it?.let { route ->
                                    activity.showInfoToast(
                                        message = "Route selected: ${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.google_place_name}",
                                        title = "Route Selected"
                                    )
                                }
                            },
                            isLoading = isLoadingRoutes,
                            remoteDataManager = remoteDataManager,
                            lifecycleScope = lifecycleScope,
                            activity = activity
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
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Editable Trip Price
                                    OutlinedTextField(
                                        value = tripPrice,
                                        onValueChange = { newValue ->
                                            // Only allow numbers and at most one decimal point
                                            val filtered = newValue.filter { it.isDigit() || it == '.' }
                                            // Ensure only one decimal point
                                            if (filtered.count { it == '.' } <= 1) {
                                                tripPrice = filtered
                                            }
                                        },
                                        label = { Text("Trip Price (RWF)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("Enter trip price") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Reverse Route Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isReversed,
                                            onCheckedChange = { onReverseRouteChanged(it) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Reverse Route",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Unified Waypoint Table (only show if route selected)
            if (selectedRoute != null) {
                item {
                    UnifiedWaypointTable(
                        unifiedWaypoints = unifiedWaypoints,
                        onPriceChanged = { index, price -> handlePriceChange(index, price) },
                        onDelete = { index -> handleDelete(index) },
                        onMoveUp = { index -> handleMoveUp(index) },
                        onMoveDown = { index -> handleMoveDown(index) },
                        selectedRoute = selectedRoute,
                        onRevertToRoute = { handleRevertToRoute() }
                    )
                }
            }

            // Departure Date & Time Selection (replaces Create Trip button)
            // Only show when route and price are set
            if (selectedRoute != null && tripPrice.isNotBlank()) {
                item {
                    DateTimePickerSection(
                        selectedDateTime = selectedDateTime,
                        onDateTimeChanged = { newDateTime ->
                            selectedDateTime = newDateTime
                        },
                        onDateTimeConfirmed = {
                            // After date/time is confirmed, validate and show confirmation dialog
                            validateAndShowConfirmation()
                        },
                        enabled = !isCreatingTrip,
                        isCreatingTrip = isCreatingTrip
                    )
                }
            }
        }
        
        // Floating Action Button for adding waypoints
        if (selectedRoute != null) {
            FloatingActionButton(
                onClick = { showAddWaypointDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Waypoint",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        // Location search dialog for adding waypoints
        if (showAddWaypointDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddWaypointDialog = false
                    selectedLocationForWaypoint = null
                },
                title = { Text("Add Waypoint") },
                text = {
                    SearchableLocationDropdown(
                        selectedLocation = selectedLocationForWaypoint,
                        onLocationSelected = { location ->
                            selectedLocationForWaypoint = location
                            // Don't add yet - wait for user to click "Add" button
                        },
                        isLoading = isLoadingLocations,
                        remoteDataManager = remoteDataManager,
                        lifecycleScope = lifecycleScope,
                        activity = activity,
                        label = "Search Location"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedLocationForWaypoint?.let { handleAddWaypoint(it) }
                            showAddWaypointDialog = false
                            selectedLocationForWaypoint = null
                        },
                        enabled = selectedLocationForWaypoint != null
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAddWaypointDialog = false
                        selectedLocationForWaypoint = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
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
                        if (noWaypoints || unifiedWaypoints.isEmpty()) {
                            // Direct route A → B
                            Text("Route: ${route.origin.custom_name ?: route.origin.google_place_name} → ${route.destination.custom_name ?: route.destination.google_place_name}")
                        } else {
                            // Route with waypoints A → B → C → D
                            val sortedWaypoints = unifiedWaypoints.sortedBy { it.order }
                            
                            val routeString = buildString {
                                append(route.origin.custom_name ?: route.origin.google_place_name)
                                sortedWaypoints.forEach { waypoint ->
                                    val locationName = waypoint.location.custom_name ?: waypoint.location.google_place_name
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
                        if (!noWaypoints && unifiedWaypoints.isNotEmpty()) {
                            unifiedWaypoints.sortedBy { it.order }.forEachIndexed { index, waypoint ->
                                Text("Waypoint ${index + 1}: ${waypoint.location.custom_name ?: waypoint.location.google_place_name} - ${waypoint.price?.let { "$it RWF" } ?: "N/A"}")
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

                            // Convert unifiedWaypoints to customWaypoints for API
                            // Only include waypoints that are custom (edited) or all if route waypoints were modified
                            val finalCustomWaypoints = if (!noWaypoints && unifiedWaypoints.isNotEmpty()) {
                                // Check if all waypoints are from route and unchanged
                                val allUnchanged = unifiedWaypoints.all { it.isFromRoute && 
                                    it.price == selectedRoute?.waypoints?.find { wp -> wp.id == it.originalRouteWaypointId }?.price }
                                
                                if (allUnchanged) {
                                    // All waypoints are unchanged from route, send empty list
                                    emptyList()
                                } else {
                                    // Convert to CreateCustomWaypoint format
                                    unifiedWaypoints.map { wp ->
                                        CreateCustomWaypoint(
                                            location_id = wp.location_id,
                                            order = wp.order,
                                            price = wp.price
                                        )
                                    }
                                }
                            } else {
                                emptyList()
                            }

                            // Validate unified waypoints if they exist
                            if (!noWaypoints && unifiedWaypoints.isNotEmpty()) {
                                val invalidWaypoints = unifiedWaypoints.filter { it.location_id == 0 }
                                if (invalidWaypoints.isNotEmpty()) {
                                    activity.showErrorToast(
                                        message = "All waypoints must have a valid location.",
                                        title = "Invalid Waypoints"
                                    )
                                    isCreatingTrip = false
                                    return@launch
                                }

                                // Check if any waypoint is missing a price
                                val waypointsWithoutPrice = unifiedWaypoints.filter { it.price == null || it.price <= 0.0 }
                                if (waypointsWithoutPrice.isNotEmpty()) {
                                    activity.showErrorToast(
                                        message = "All waypoints must have a valid price. Please complete the pricing for all waypoints.",
                                        title = "Incomplete Waypoint Data"
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
                                custom_waypoints = finalCustomWaypoints,
                                no_waypoints = noWaypoints
                            )

                            when (val result = remoteDataManager.createTrip(createTripRequest)) {
                                is RemoteResult.Success -> {
                                    val tripType = when {
                                        noWaypoints -> "direct trip (no waypoints)"
                                        finalCustomWaypoints.isNotEmpty() -> "trip with ${finalCustomWaypoints.size} waypoints"
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
                                    unifiedWaypoints = emptyList()
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
    onDateTimeChanged: (java.util.Calendar) -> Unit,
    onDateTimeConfirmed: () -> Unit,
    enabled: Boolean = true,
    isCreatingTrip: Boolean = false
) {
    val dateTimeFormatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    var showDateTimePicker by remember { mutableStateOf(false) }

    // Styled as Create Trip button
    Button(
        onClick = { showDateTimePicker = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !isCreatingTrip
    ) {
        if (isCreatingTrip) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📅 🕐", color = MaterialTheme.colorScheme.onPrimary)
            Text(
                text = dateTimeFormatter.format(selectedDateTime.time),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    // Combined Date-Time Picker Dialog
    if (showDateTimePicker) {
        DateTimePickerDialog(
            selectedDateTime = selectedDateTime,
            onDateTimeSelected = { newDateTime ->
                onDateTimeChanged(newDateTime)
                showDateTimePicker = false
                // After confirming date/time selection, trigger validation and confirmation
                onDateTimeConfirmed()
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
private fun UnifiedWaypointTable(
    unifiedWaypoints: List<UnifiedWaypoint>,
    onPriceChanged: (Int, Double?) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    selectedRoute: SaveRouteResponse?,
    onRevertToRoute: () -> Unit
) {
    val routeHadWaypoints = selectedRoute?.waypoints?.isNotEmpty() == true
    val showRevertButton = routeHadWaypoints && unifiedWaypoints.isEmpty()
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Waypoints",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showRevertButton) {
                Button(
                    onClick = onRevertToRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Revert to Route Waypoints")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (unifiedWaypoints.isEmpty()) {
                Text(
                    text = if (routeHadWaypoints) 
                        "All waypoints have been removed. Click 'Revert to Route Waypoints' to restore them."
                    else
                        "No waypoints. Use the + button to add waypoints.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                val sortedWaypoints = unifiedWaypoints.sortedBy { it.order }
                sortedWaypoints.forEachIndexed { sortedIndex, waypoint ->
                    val actualIndex = unifiedWaypoints.indexOf(waypoint)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (waypoint.isFromRoute) 
                                MaterialTheme.colorScheme.primaryContainer
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = waypoint.location.custom_name ?: waypoint.location.google_place_name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (waypoint.isFromRoute)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Order: ${waypoint.order + 1}",
                                        fontSize = 12.sp,
                                        color = if (waypoint.isFromRoute)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val sortedIndex = sortedWaypoints.indexOf(waypoint)
                                    if (sortedIndex > 0) {
                                        IconButton(
                                            onClick = { onMoveUp(actualIndex) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("↑", fontSize = 14.sp)
                                        }
                                    }
                                    if (sortedIndex < sortedWaypoints.size - 1) {
                                        IconButton(
                                            onClick = { onMoveDown(actualIndex) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("↓", fontSize = 14.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDelete(actualIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = waypoint.price?.toString() ?: "",
                                onValueChange = { newPrice ->
                                    val filtered = newPrice.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) {
                                        val price = if (filtered.isBlank()) null else filtered.toDoubleOrNull()
                                        onPriceChanged(actualIndex, price)
                                    }
                                },
                                label = { Text("Price (RWF)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Enter price") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomWaypointCard(
    waypoint: CreateCustomWaypoint,
    index: Int,
    availableLocations: List<SavePlaceResponse>,
    allLocations: List<SavePlaceResponse>,
    isLoadingLocations: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLocationChanged: (Int) -> Unit,
    onPriceChanged: (Double?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    remoteDataManager: RemoteDataManager,
    lifecycleScope: kotlinx.coroutines.CoroutineScope,
    activity: CreateTripActivity
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

            // Location Selection with Search
            val selectedLocation = availableLocations.find { it.id == waypoint.location_id }
            Log.d("CustomWaypointCard", "Waypoint $index: location_id = ${waypoint.location_id}")
            Log.d("CustomWaypointCard", "Available locations count: ${availableLocations.size}")
            Log.d("CustomWaypointCard", "Available location IDs: ${availableLocations.map { it.id }}")
            Log.d("CustomWaypointCard", "All locations count: ${allLocations.size}")
            Log.d("CustomWaypointCard", "All location IDs: ${allLocations.map { it.id }}")
            Log.d("CustomWaypointCard", "Selected location: ${selectedLocation?.custom_name ?: selectedLocation?.google_place_name}")
            
            // If we have a location_id but no selectedLocation, it means the location was filtered out
            // In this case, we need to find it in the full locations list
            val finalSelectedLocation = if (waypoint.location_id > 0 && selectedLocation == null) {
                Log.d("CustomWaypointCard", "Location ${waypoint.location_id} not found in availableLocations, searching in full locations list")
                val foundLocation = allLocations.find { it.id == waypoint.location_id }
                Log.d("CustomWaypointCard", "Found in allLocations: ${foundLocation?.custom_name ?: foundLocation?.google_place_name}")
                foundLocation
            } else {
                Log.d("CustomWaypointCard", "Using selectedLocation from availableLocations: ${selectedLocation?.custom_name ?: selectedLocation?.google_place_name}")
                selectedLocation
            }
            
            Log.d("CustomWaypointCard", "Passing finalSelectedLocation to SearchableLocationDropdown: ${finalSelectedLocation?.custom_name ?: finalSelectedLocation?.google_place_name}")
            SearchableLocationDropdown(
                selectedLocation = finalSelectedLocation,
                onLocationSelected = { location ->
                    Log.d("CustomWaypointCard", "SearchableLocationDropdown onLocationSelected called with: ${location?.custom_name ?: location?.google_place_name}")
                    onLocationChanged(location?.id ?: 0)
                },
                isLoading = isLoadingLocations,
                remoteDataManager = remoteDataManager,
                lifecycleScope = lifecycleScope,
                activity = activity,
                label = "Location"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Price Input (Optional)
            OutlinedTextField(
                value = waypoint.price?.toString() ?: "",
                onValueChange = { newPrice ->
                    // Only allow numbers and at most one decimal point
                    val filtered = newPrice.filter { it.isDigit() || it == '.' }
                    // Ensure only one decimal point
                    if (filtered.count { it == '.' } <= 1) {
                        val price = if (filtered.isBlank()) null else filtered.toDoubleOrNull()
                        onPriceChanged(price)
                    }
                },
                label = { Text("Price (RWF) - Optional") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Leave empty for default pricing") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
        }
    }
}