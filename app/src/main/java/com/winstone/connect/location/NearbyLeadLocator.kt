package com.winstone.connect.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.winstone.connect.data.Lead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object NearbyLeadLocator {
    @SuppressLint("MissingPermission")
    suspend fun locate(context: Context, leads: List<Lead>): List<Lead> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext leads
        val current = manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return@withContext leads
        if (!Geocoder.isPresent()) return@withContext leads

        val geocoder = Geocoder(context, Locale("bn", "BD"))
        sortNearbyLeads(leads.map { lead ->
            val address = lead.address?.trim().orEmpty()
            if (address.isBlank()) return@map lead
            val target = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName("$address, Bangladesh", 1)?.firstOrNull()
            }.getOrNull() ?: return@map lead
            val result = FloatArray(1)
            Location.distanceBetween(
                current.latitude,
                current.longitude,
                target.latitude,
                target.longitude,
                result,
            )
            lead.copy(distanceKm = result[0].toDouble() / 1000.0)
        })
    }
}

fun sortNearbyLeads(leads: List<Lead>): List<Lead> =
    leads.sortedWith(compareBy<Lead> { it.distanceKm == null }.thenBy { it.distanceKm })

fun Double.asDistanceLabel(): String =
    if (this < 1.0) "${(this * 1000).toInt()} মিটার দূরে" else String.format(Locale.US, "%.1f কিমি দূরে", this)