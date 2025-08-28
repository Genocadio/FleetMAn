package com.gocavgo.fleetman.service

import android.util.Log
import com.gocavgo.fleetman.dataclass.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.div

/**
 * Manages remote operations for places, routes, and trips.
 * Currently logs operations but designed to integrate with REST API later.
 */
class RemoteDataManager {

    companion object {
        private const val TAG = "RemoteDataManager"
        private const val BASE_URL = "https://api.gocavgo.com/api/navig/"
        private const val TRIPS_BASE_URL = "https://api.gocavgo.com/api/navig/trips"

        // Singleton instance
        @Volatile
        private var INSTANCE: RemoteDataManager? = null

        fun getInstance(): RemoteDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteDataManager().also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Initialize OkHttp client with proper configuration
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Initialize JSON serializer with proper configuration
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // EXISTING METHODS (vehicles, locations, routes)
    suspend fun getCompanyVehicles(
        companyId: Long
    ): RemoteResult<List<VehicleResponseDto>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== FETCHING VEHICLES FOR COMPANY $companyId FROM REMOTE ===")
                val url = "https://api.gocavgo.com/api/main/vehicles/company/$companyId".toHttpUrl()

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string() ?: ""
                        Log.d(TAG, "Response: $responseBody")

                        val vehicles = json.decodeFromString<List<VehicleResponseDto>>(responseBody)
                        RemoteResult.Success(vehicles)
                    } else {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Failed to fetch vehicles. Response code: ${resp.code}, Error: $errorBody")
                        RemoteResult.Error("Failed to fetch vehicles: HTTP ${resp.code} - $errorBody")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error while fetching vehicles: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch vehicles: ${e.message}", e)
                RemoteResult.Error("Failed to fetch vehicles: ${e.message}")
            }
        }
    }

    /**
     * Delete a trip
     * @param tripId ID of the trip to delete
     * @return RemoteResult indicating success or error
     */
    suspend fun deleteTrip(tripId: Int): RemoteResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== DELETING TRIP $tripId FROM REMOTE ===")

                val url = "$TRIPS_BASE_URL/$tripId"
                Log.d(TAG, "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .delete()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Trip $tripId deleted successfully")
                        RemoteResult.Success(Unit)
                    } else {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        Log.e(
                            TAG,
                            "Failed to delete trip. Response code: ${resp.code}, Error: $errorBody"
                        )
                        RemoteResult.Error("Failed to delete trip: HTTP ${resp.code} - $errorBody")
                    }
                }

            } catch (e: IOException) {
                Log.e(TAG, "Network error while deleting trip: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete trip: ${e.message}", e)
                RemoteResult.Error("Failed to delete trip: ${e.message}")
            }
        }
    }

    suspend fun getLocations(
        search: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): RemoteResult<PaginatedResult<List<SavePlaceResponse>>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== FETCHING LOCATIONS FROM REMOTE ===")

                val urlBuilder = "${BASE_URL}locations".toHttpUrl().newBuilder()
                search?.let { searchTerm ->
                    urlBuilder.addQueryParameter("search", searchTerm)
                    Log.d(TAG, "Search term: $searchTerm")
                }
                urlBuilder.addQueryParameter("page", page.toString())
                urlBuilder.addQueryParameter("limit", limit.toString())

                val url = urlBuilder.build()
                Log.d(TAG, "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string() ?: ""
                        Log.d(TAG, "Response: $responseBody")

                        val paginatedResponse = json.decodeFromString<PaginatedLocationsResponse>(responseBody)

                        Log.d(
                            TAG,
                            "Retrieved ${paginatedResponse.data.size} locations (page $page of ${paginatedResponse.pagination.total_pages})"
                        )

                        val paginatedResult = PaginatedResult(
                            data = paginatedResponse.data,
                            pagination = paginatedResponse.pagination
                        )

                        RemoteResult.Success(paginatedResult)
                    } else {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        Log.e(
                            TAG,
                            "Failed to fetch locations. Response code: ${resp.code}, Error: $errorBody"
                        )
                        RemoteResult.Error("Failed to fetch locations: HTTP ${resp.code} - $errorBody")
                    }
                }

            } catch (e: IOException) {
                Log.e(TAG, "Network error while fetching locations: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch locations: ${e.message}", e)
                RemoteResult.Error("Failed to fetch locations: ${e.message}")
            }
        }
    }

    suspend fun getRoutes(
        origin: String? = null,
        destination: String? = null,
        cityRoute: Boolean? = null,
        originProvince: String? = null,
        destinationProvince: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): RemoteResult<PaginatedResult<List<SaveRouteResponse>>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== FETCHING ROUTES FROM REMOTE ===")

                val urlBuilder = "${BASE_URL}routes".toHttpUrl().newBuilder()

                origin?.let {
                    urlBuilder.addQueryParameter("origin", it)
                    Log.d(TAG, "Origin filter: $it")
                }

                destination?.let {
                    urlBuilder.addQueryParameter("destination", it)
                    Log.d(TAG, "Destination filter: $it")
                }

                cityRoute?.let {
                    urlBuilder.addQueryParameter("city_route", it.toString())
                    Log.d(TAG, "City route filter: $it")
                }

                originProvince?.let {
                    urlBuilder.addQueryParameter("origin_province", it)
                    Log.d(TAG, "Origin province filter: $it")
                }

                destinationProvince?.let {
                    urlBuilder.addQueryParameter("destination_province", it)
                    Log.d(TAG, "Destination province filter: $it")
                }

                urlBuilder.addQueryParameter("page", page.toString())
                urlBuilder.addQueryParameter("limit", limit.toString())

                val url = urlBuilder.build()
                Log.d(TAG, "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string() ?: ""
                        Log.d(TAG, "Response: $responseBody")

                        val paginatedResponse = json.decodeFromString<PaginatedRoutesResponse>(responseBody)

                        Log.d(
                            TAG,
                            "Retrieved ${paginatedResponse.data.size} routes (page $page of ${paginatedResponse.pagination.total_pages})"
                        )

                        val paginatedResult = PaginatedResult(
                            data = paginatedResponse.data,
                            pagination = paginatedResponse.pagination
                        )

                        RemoteResult.Success(paginatedResult)
                    } else {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        Log.e(
                            TAG,
                            "Failed to fetch routes. Response code: ${resp.code}, Error: $errorBody"
                        )
                        RemoteResult.Error("Failed to fetch routes: HTTP ${resp.code} - $errorBody")
                    }
                }

            } catch (e: IOException) {
                Log.e(TAG, "Network error while fetching routes: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch routes: ${e.message}", e)
                RemoteResult.Error("Failed to fetch routes: ${e.message}")
            }
        }
    }

    // NEW TRIP METHODS

    /**
     * Get trips for a specific vehicle with pagination
     * @param vehicleId ID of the vehicle to get trips for
     * @param page Page number (default: 1)
     * @param limit Number of items per page (default: 20)
     * @return RemoteResult containing paginated list of TripResponse objects
     */
   suspend fun getVehicleTrips(
        vehicleId: Int,
        page: Int = 1,
        limit: Int = 20
    ): RemoteResult<RemoteDataManager.PaginatedResult<List<TripResponse>>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== FETCHING TRIPS FOR VEHICLE $vehicleId FROM REMOTE ===")

                val urlBuilder = TRIPS_BASE_URL.toHttpUrl().newBuilder()
                urlBuilder.addQueryParameter("vehicle_id", vehicleId.toString())
                urlBuilder.addQueryParameter("page", page.toString())
                urlBuilder.addQueryParameter("limit", limit.toString())

                val url = urlBuilder.build()
                Log.d(TAG, "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string() ?: ""
                        Log.d(TAG, "Response: $responseBody")

                        val paginatedResponse = json.decodeFromString<PaginatedTripsResponse>(responseBody)

                        val pagination = PaginationInfo(
                            total = paginatedResponse.total,
                            total_pages = (paginatedResponse.total + paginatedResponse.limit - 1) / paginatedResponse.limit,
                            page = (paginatedResponse.offset / paginatedResponse.limit) + 1,
                            limit = paginatedResponse.limit,
                            has_next = (paginatedResponse.offset + paginatedResponse.limit) < paginatedResponse.total,
                            has_prev = paginatedResponse.offset > 0
                        )
                        val paginatedResult = RemoteDataManager.PaginatedResult(
                            data = paginatedResponse.trips,
                            pagination = pagination
                        )
                        Log.d(
                            TAG,
                            "Retrieved ${paginatedResponse.trips.size} trips for vehicle $vehicleId (page ${pagination.page} of ${pagination.total_pages})"
                        )
                        Log.d(TAG, "Total trips: ${pagination.total}")
                        Log.d(TAG, "===============================")
                        RemoteResult.Success(paginatedResult)
                    } else {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        Log.e(
                            TAG,
                            "Failed to fetch trips. Response code: ${resp.code}, Error: $errorBody"
                        )
                        RemoteResult.Error("Failed to fetch trips: HTTP ${resp.code} - $errorBody")
                    }
                }

            } catch (e: IOException) {
                Log.e(TAG, "Network error while fetching trips: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch trips: ${e.message}", e)
                RemoteResult.Error("Failed to fetch trips: ${e.message}")
            }
        }
    }

    /**
     * Create a new trip
     * @param createTripRequest The trip creation request
     * @return RemoteResult containing the created TripResponse
     */
    suspend fun createTrip(
        createTripRequest: CreateTripRequest
    ): RemoteResult<TripResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== CREATING TRIP FROM REMOTE ===")
                Log.d(TAG, "Trip request: $createTripRequest")

                val requestBody = json.encodeToString(CreateTripRequest.serializer(), createTripRequest)
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(TRIPS_BASE_URL)
                    .post(requestBody)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""

                    if (resp.isSuccessful) {
                        Log.d(TAG, "Trip created successfully")
                        Log.d(TAG, "Response: $responseBody")

                        val tripResponse = json.decodeFromString<TripResponse>(responseBody)
                        RemoteResult.Success(tripResponse)
                    } else {
                        Log.e(
                            TAG,
                            "Failed to create trip. Response code: ${resp.code}, Error: $responseBody"
                        )
                        RemoteResult.Error("Failed to create trip: HTTP ${resp.code} - $responseBody")
                    }
                }

            } catch (e: IOException) {
                Log.e(TAG, "Network error while creating trip: ${e.message}", e)
                RemoteResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create trip: ${e.message}", e)
                RemoteResult.Error("Failed to create trip: ${e.message}")
            }
        }
    }

    data class PaginatedResult<T>(
        val data: T,
        val pagination: PaginationInfo
    )

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}

/**
 * Sealed class representing remote operation results
 */
sealed class RemoteResult<out T> {
    data class Success<T>(val data: T) : RemoteResult<T>()
    data class Error(val message: String) : RemoteResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Error -> message
    }
}