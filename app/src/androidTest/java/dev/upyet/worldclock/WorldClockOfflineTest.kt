package dev.upyet.worldclock

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.upyet.R
import dev.upyet.worldclock.data.WorldCitiesLoader
import dev.upyet.worldclock.data.WorldCitiesParser
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.data.resolveDeviceZone
import dev.upyet.worldclock.domain.CitySearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.Locale

/**
 * Offline guarantees of the World clock (ADR 0011, `world-clock-offline` device-matrix row).
 *
 * Radio-off search is a property of the code path, not of the device: the gazetteer is a bundled raw resource,
 * the search index is built in memory from it, and the app declares no `INTERNET` permission, so nothing on this
 * path can reach the network even when it is available. These tests pin the parts a JVM test cannot reach: the
 * resource really ships and parses against this device's tzdb, the search finds the acceptance cities from that
 * data alone, the attribution string is in the resource bundle, and the merged manifest still declares no network
 * permission.
 *
 * Airplane mode itself cannot be toggled from here. It is the manual half of the matrix row: turn it on, then run
 * the search by hand. These tests are the automated half and do not replace that check.
 */
@RunWith(AndroidJUnit4::class)
class WorldClockOfflineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledGazetteerLoadsAndEveryZoneResolvesOnThisDevice() {
        val cities = loadCities()
        assertThat(cities.size).isGreaterThan(5000)

        val rejectedZones = mutableSetOf<String>()
        val result = context.resources.openRawResource(R.raw.world_cities).use { stream ->
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                WorldCitiesParser.parse(lines) { id -> resolveDeviceZone(id).also { if (it == null) rejectedZones += id } }
            }
        }
        assertThat(result.droppedMalformed).isEqualTo(0)
        assertThat(cities).hasSize(result.cities.size)
        // A device tzdb can be older than the pinned one (ADR 0011): an API 35 emulator on tzdata 2024a has no
        // America/Coyhaique (2025b). Such a row is dropped by the runtime filter, never crashes the screen. The
        // only drop allowed is a zone this device genuinely lacks; a known zone that fails to load is a bug.
        val known = ZoneId.getAvailableZoneIds()
        assertWithMessage("zones dropped although this device knows them").that(rejectedZones.filter { it in known }).isEmpty()
        val unresolvable = cities.map { it.zoneId.id }.filter { id ->
            try {
                ZoneId.of(id)
                false
            } catch (e: java.time.DateTimeException) {
                true
            }
        }
        assertWithMessage("zone ids this device's tzdb cannot resolve: $unresolvable").that(unresolvable).isEmpty()
    }

    @Test fun searchFindsAcceptanceCitiesFromBundledDataOnly() {
        val cities = loadCities()
        val index = CitySearchIndex(cities) { iso ->
            setOf(Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH))
        }

        val expected = listOf(
            "San Francisco" to "America/Los_Angeles",
            "Barcelona" to "Europe/Madrid",
            "Mumbai" to "Asia/Kolkata",
            "Osaka" to "Asia/Tokyo",
        )
        for ((query, zone) in expected) {
            val first = index.search(query).firstOrNull()
            assertWithMessage("first result for \"$query\"").that(first).isNotNull()
            assertWithMessage("first result for \"$query\"").that(first!!.name).isEqualTo(query)
            assertWithMessage("zone of first result for \"$query\"").that(first.zoneId.id).isEqualTo(zone)
        }

        // Spanish display name, lowercase and without the capital: the name_es column is searched too.
        val nuevaYork = index.search("nueva york").firstOrNull()
        assertWithMessage("first result for \"nueva york\"").that(nuevaYork?.name).isEqualTo("New York")
        assertWithMessage("zone of New York").that(nuevaYork?.zoneId?.id).isEqualTo("America/New_York")

        // Accent folding: the accented and unaccented spellings must rank the same cities the same way.
        val accented = index.search("Macorís").map(WorldCity::geonamesId)
        val plain = index.search("Macoris").map(WorldCity::geonamesId)
        assertThat(accented).isNotEmpty()
        assertThat(accented).isEqualTo(plain)
    }

    @Test fun attributionStringNamesGeoNamesAndLicence() {
        // Pin English so the assertion does not depend on the device locale.
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val text = context.createConfigurationContext(config).getString(R.string.world_clock_attribution)
        assertThat(text).contains("GeoNames")
        assertThat(text).contains("CC BY 4.0")
    }

    @Test fun appDeclaresNoInternetPermission() {
        val requested = declaredPermissions(context)
        assertWithMessage("requested permissions: $requested")
            .that(requested)
            .doesNotContain(android.Manifest.permission.INTERNET)
    }

    private fun loadCities(): List<WorldCity> = runBlocking { WorldCitiesLoader(context, Dispatchers.Default).load() }

    private fun declaredPermissions(context: Context): List<String> =
        packageInfo(context.packageManager, context.packageName).requestedPermissions?.toList().orEmpty()

    /**
     * The `PackageInfoFlags` overload is API 33+. minSdk is 26, so the `Int` overload is still the only option
     * below 33. It is deprecated only from 33 on, and this function is the only place that calls it.
     */
    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
}
