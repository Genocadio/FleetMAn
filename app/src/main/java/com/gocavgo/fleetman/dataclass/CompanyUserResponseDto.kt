package com.gocavgo.fleetman.dataclass

import kotlinx.serialization.Serializable

@Serializable
data class CompanyUserResponseDto(
    val id: Long? = null,
    val companyId: Long? = null,
    val companyName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val status: String? = null,
    val dateOfBirth: String? = null,
    val address: String? = null,
    val role: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)