package com.gocavgo.fleetman.dataclass

import com.gocavgo.fleetman.enums.VehicleStatus
import com.gocavgo.fleetman.enums.VehicleType
import kotlinx.serialization.Serializable

@Serializable
data class VehicleResponseDto (
    val id: Long,
    val companyId: Long,
    val companyName: String,
    val make: String,
    val model: String,
    val capacity: Int,
    val licensePlate: String,
    val vehicleType: VehicleType,
    val status: VehicleStatus,
    val driver : CompanyUserResponseDto? = null,
)