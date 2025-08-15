package com.gocavgo.fleetman.enums

import kotlinx.serialization.Serializable

@Serializable
enum class VehicleStatus {
    AVAILABLE,  MAINTENANCE, OUT_OF_SERVICE, OCCUPIED,
}