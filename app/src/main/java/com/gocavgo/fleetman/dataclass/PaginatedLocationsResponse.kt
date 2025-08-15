package com.gocavgo.fleetman.dataclass

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedLocationsResponse(
    val data: List<SavePlaceResponse>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int,
    val total_pages: Int,
    val has_next: Boolean,
    val has_prev: Boolean
)