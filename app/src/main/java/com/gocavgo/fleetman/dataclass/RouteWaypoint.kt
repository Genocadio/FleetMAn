package com.gocavgo.fleetman.dataclass

import kotlinx.serialization.Serializable

@Serializable
data class RouteWaypoint(
    val id: Int,
    val route_id: Int,
    val location_id: Int,
    var order: Int,
    var price: Double,
    val created_at: String,
    val location: SavePlaceResponse
)