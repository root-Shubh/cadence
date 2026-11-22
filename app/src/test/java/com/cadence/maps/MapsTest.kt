package com.cadence.maps

import com.cadence.patterns.TrackPoint
import com.cadence.ui.placementText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class MapsTest {

    private fun near(expected: Double, actual: Double, tol: Double) =
        assertTrue("$expected vs $actual", abs(expected - actual) <= tol)

    /** The real bundled map: a vector PMTiles v3 file covering Chennai, zoom 0 to 15. */
    @Test
    fun bundledChennaiMapHeaderIsValid() {
        val file = listOf(File("src/main/assets/maps/$BUNDLED_MAP_FILE"), File("app/src/main/assets/maps/$BUNDLED_MAP_FILE"))
            .first { it.exists() }
        val header = file.inputStream().use { it.readNBytes(PMTILES_HEADER_BYTES) }
        assertNull(checkMapHeader(header))
        val info = parsePmtilesHeader(header)!!
        assertEquals(0, info.minZoom)
        assertEquals(15, info.maxZoom)
        // Chennai: about 80.27 E, 13.08 N.
        assertTrue(80.27 in info.minLon..info.maxLon)
        assertTrue(13.08 in info.minLat..info.maxLat)
    }

    @Test
    fun rejectsFilesThatAreNotVectorPmtiles() {
        assertEquals(MapFileProblem.NOT_PMTILES, checkMapHeader(ByteArray(127)))
        assertEquals(MapFileProblem.NOT_PMTILES, checkMapHeader("PMTiles".toByteArray()))
        val raster = ByteArray(127).also {
            "PMTiles".toByteArray().copyInto(it)
            it[7] = 3
            it[99] = 2 // png tiles
        }
        assertEquals(MapFileProblem.NOT_VECTOR, checkMapHeader(raster))
        val v2 = raster.copyOf().also { it[7] = 2; it[99] = 1 }
        assertEquals(MapFileProblem.NOT_PMTILES, checkMapHeader(v2))
    }

    @Test
    fun importedNamesAreSafeAndNeverReplaceTheBuiltInMap() {
        assertEquals("my-city-2026.pmtiles", safeMapFileName("My City 2026.pmtiles"))
        assertEquals("map.pmtiles", safeMapFileName(null))
        assertEquals("passwd.pmtiles", safeMapFileName("../../etc/passwd"))
        assertEquals("map.pmtiles", safeMapFileName(".."))
        assertEquals("chennai-imported.pmtiles", safeMapFileName("Chennai.pmtiles"))
        assertEquals("Chennai", mapLabel(BUNDLED_MAP_FILE))
    }

    @Test
    fun offsetsAreMetresOnTheGround() {
        val (lat, lon) = offsetLatLon(13.0, 80.0, eastM = 0.0, northM = 1_113.2)
        near(13.01, lat, 1e-6)
        near(80.0, lon, 1e-9)
        val (_, lon2) = offsetLatLon(13.0, 80.0, eastM = 1_000.0, northM = 0.0)
        // A degree of longitude is shorter away from the equator: cos(13 deg) * 111.32 km.
        near(1_000.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(13.0))), lon2 - 80.0, 1e-9)
    }

    /** The drawing step closest in time to a GPS fix lands exactly on that fix. */
    @Test
    fun drawingIsPinnedToTheClosestFixInTime() {
        val drawing = (0..10).map { TrackPoint(1_000L * it, x = 0.0, y = 10.0 * it, pen = it > 0, drawing = 0) }
        val fixes = listOf(GeoPoint(13.05, 80.25, 4_200L), GeoPoint(13.2, 80.1, 60 * 60_000L))
        val anchor = anchorDrawing(drawing, fixes)!!
        assertEquals(200L, anchor.gapMillis)
        val placed = drawingOnMap(drawing, anchor)
        near(13.05, placed[4].lat, 1e-9) // step 4 (t = 4,000 ms) is on the fix
        near(80.25, placed[4].lon, 1e-9)
        assertTrue(placed[10].lat > placed[0].lat) // walked north
    }

    @Test
    fun noPlacementWithoutAFixNearInTime() {
        val drawing = listOf(TrackPoint(0L, 0.0, 0.0, false, 0), TrackPoint(1_000L, 0.0, 1.0, true, 0))
        assertNull(anchorDrawing(drawing, emptyList()))
        assertNull(anchorDrawing(drawing, listOf(GeoPoint(13.0, 80.0, MAX_ANCHOR_GAP_MS + 5_000L))))
        assertNotNull(anchorDrawing(drawing, listOf(GeoPoint(13.0, 80.0, 30_000L))))
    }

    @Test
    fun boundsCoverEveryPoint() {
        val (sw, ne) = boundsOf(listOf(GeoPoint(13.1, 80.3), GeoPoint(12.9, 80.2), GeoPoint(13.0, 80.25)))!!
        assertEquals(GeoPoint(12.9, 80.2), sw)
        assertEquals(GeoPoint(13.1, 80.3), ne)
        assertNull(boundsOf(emptyList()))
    }

    /** Offline only: the style reads a local file and bundled fonts, never a URL on the network. */
    @Test
    fun styleUsesOnlyLocalSources() {
        val json = offlineStyleJson("/data/user/0/com.cadence/files/maps/chennai.pmtiles", LIGHT_MAP)
        assertTrue(json.contains("pmtiles://file:///data/user/0/com.cadence/files/maps/chennai.pmtiles"))
        assertTrue(json.contains("asset://fonts/{fontstack}/{range}.pbf"))
        assertFalse(json.contains("http://"))
        assertFalse(json.contains("https://"))
        // Balanced brackets: a cheap check that the hand-written JSON is well formed.
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun captionExplainsWhyTheDrawingIsOrIsNotPlaced() {
        assertTrue(placementText(routeFixes = 0, hasDrawing = true, gapMillis = null).contains("No GPS fix"))
        assertTrue(placementText(routeFixes = 5, hasDrawing = true, gapMillis = 120_000L).contains("2 min"))
        assertTrue(placementText(routeFixes = 5, hasDrawing = true, gapMillis = null).contains("too far in time"))
        // 2026-11-22 on the phone: one fix, taken during the walk.
        val oneFix = placementText(routeFixes = 1, hasDrawing = true, gapMillis = 0L)
        assertTrue(oneFix.contains("Only one GPS fix"))
        assertTrue(oneFix.contains("during the walk"))
        assertFalse(oneFix.contains("0 min"))
    }
}
