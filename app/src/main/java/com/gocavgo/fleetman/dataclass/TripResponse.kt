package com.gocavgo.fleetman.dataclass


import kotlinx.serialization.Serializable

@Serializable
data class TripResponse(
    val id: Int,
    val route_id: Int,
    val vehicle_id: Int,
    val vehicle: VehicleInfo,
    val status: String,
    val departure_time: Long,
    val connection_mode: String,
    val notes: String?,
    val seats: Int,
    val is_reversed: Boolean,
    val has_custom_waypoints: Boolean,
    val created_at: String,
    val updated_at: String,
    val route: TripRoute,
    val waypoints: List<TripWaypoint>
)

@Serializable
data class VehicleInfo(
    val id: Int,
    val company_id: Int,
    val company_name: String,
    val capacity: Int,
    val license_plate: String,
    val driver: DriverInfo?
)

@Serializable
data class DriverInfo(
    val name: String,
    val phone: String
)

@Serializable
data class TripRoute(
    val id: Int,
    val origin: SavePlaceResponse,
    val destination: SavePlaceResponse
)

@Serializable
data class TripWaypoint(
    val id: Int,
    val trip_id: Int,
    val location_id: Int,
    val order: Int,
    val price: Double,
    val is_passed: Boolean,
    val is_next: Boolean,
    val is_custom: Boolean,
    val remaining_time: Long?,
    val remaining_distance: Double?,
    val location: SavePlaceResponse
)

@Serializable
data class CreateTripRequest(
    val route_id: Int,
    val vehicle_id: Int,
    val departure_time: Long,
    val connection_mode: String,
    val notes: String? = null,
    val is_reversed: Boolean = false,
    val custom_waypoints: List<CreateCustomWaypoint> = emptyList(),
    val no_waypoints: Boolean = false  // NEW FIELD
)

@Serializable
data class CreateCustomWaypoint(
    val location_id: Int,
    val order: Int,
    val price: Double? = null,
    val remaining_time: Long? = null,
    val remaining_distance: Double? = null
)

@Serializable
data class PaginatedTripsResponse(
    val data: List<TripResponse>,
    val pagination: PaginationInfo
)