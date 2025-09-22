package com.gocavgo.fleetman.trips

/**
 * Parser for intelligent route search syntax
 * 
 * Rules:
 * - Origin only → plain name
 * - Destination only → prefix with !
 * - Origin + Destination → separate with ,
 * - Swap origin/destination → prefix the first location with ! in the pair
 * - City route filter → @c (true) or @p (false), can appear anywhere
 * - Empty text on focus → no filters
 */
data class RouteSearchFilters(
    val origin: String? = null,
    val destination: String? = null,
    val cityRoute: Boolean? = null
)

object RouteSearchParser {
    
    /**
     * Parse search text into route search filters
     * @param searchText The search text to parse
     * @return RouteSearchFilters object with parsed filters
     */
    fun parseSearchText(searchText: String): RouteSearchFilters {
        if (searchText.isBlank()) {
            return RouteSearchFilters()
        }
        
        val trimmedText = searchText.trim()
        
        // Extract city route filter (@c or @p) - can appear anywhere
        val cityRouteFilter = extractCityRouteFilter(trimmedText)
        val textWithoutCityFilter = removeCityRouteFilter(trimmedText)
        
        // Parse origin and destination
        val (origin, destination) = parseOriginDestination(textWithoutCityFilter)
        
        return RouteSearchFilters(
            origin = origin,
            destination = destination,
            cityRoute = cityRouteFilter
        )
    }
    
    /**
     * Extract city route filter from text
     * @param text The text to search
     * @return Boolean? - true for @c, false for @p, null if neither found
     */
    private fun extractCityRouteFilter(text: String): Boolean? {
        val hasCityFilter = text.contains("@c", ignoreCase = true)
        val hasProvinceFilter = text.contains("@p", ignoreCase = true)
        
        return when {
            hasCityFilter && hasProvinceFilter -> null // Both present, ambiguous
            hasCityFilter -> true
            hasProvinceFilter -> false
            else -> null
        }
    }
    
    /**
     * Remove city route filter markers from text
     * @param text The text to clean
     * @return Cleaned text without @c or @p markers
     */
    private fun removeCityRouteFilter(text: String): String {
        return text
            .replace("@c", "", ignoreCase = true)
            .replace("@p", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
            .trim()
    }
    
    /**
     * Parse origin and destination from text
     * @param text The text to parse (without city route filters)
     * @return Pair of (origin, destination)
     */
    private fun parseOriginDestination(text: String): Pair<String?, String?> {
        if (text.isBlank()) {
            return Pair(null, null)
        }
        
        // Check if text contains comma (origin, destination format)
        if (text.contains(",")) {
            val parts = text.split(",").map { it.trim() }
            if (parts.size == 2) {
                val first = parts[0]
                val second = parts[1]
                
                // Check if first part is prefixed with ! (swapped)
                return if (first.startsWith("!")) {
                    // Swapped: !origin, destination -> origin=destination, destination=origin
                    Pair(second, first.removePrefix("!"))
                } else {
                    // Normal: origin, destination
                    Pair(first, second)
                }
            }
        }
        
        // Single location - check if prefixed with ! (destination only)
        return if (text.startsWith("!")) {
            // Destination only
            Pair(null, text.removePrefix("!"))
        } else {
            // Origin only
            Pair(text, null)
        }
    }
    
    /**
     * Get a human-readable description of the current search filters
     * @param filters The search filters
     * @return Description string
     */
    fun getSearchDescription(filters: RouteSearchFilters): String {
        val parts = mutableListOf<String>()
        
        filters.origin?.let { parts.add("From: $it") }
        filters.destination?.let { parts.add("To: $it") }
        filters.cityRoute?.let { 
            parts.add(if (it) "City routes only" else "Intercity routes only") 
        }
        
        return if (parts.isEmpty()) {
            "All routes"
        } else {
            parts.joinToString(" • ")
        }
    }
    
    /**
     * Get search placeholder text based on current filters
     * @param filters The current search filters
     * @return Placeholder text
     */
    fun getPlaceholderText(filters: RouteSearchFilters): String {
        return when {
            filters.origin != null && filters.destination != null -> "Search within filtered routes..."
            filters.origin != null -> "Add destination with !name or search..."
            filters.destination != null -> "Add origin or search..."
            else -> "Type to search routes... (e.g., 'kigali', '!musanze', 'kigali, musanze', '@c kigali')"
        }
    }
}

