package com.cadence.maps

/**
 * MapLibre style for the offline map (STEPW-65), written for the Protomaps basemap schema (layers
 * earth, water, landuse, roads, buildings, places; checked against the bundled Chennai tiles).
 * Tiles come from a local PMTiles file and fonts from the app's assets, so the map never contacts
 * a server. Light and dark palettes follow the app theme. Pure Kotlin: builds a JSON string.
 */
data class MapPalette(
    val water: String,
    val land: String,
    val green: String,
    val residential: String,
    val industrial: String,
    val building: String,
    val minorRoad: String,
    val majorRoad: String,
    val highway: String,
    val casing: String,
    val path: String,
    val label: String,
    val labelHalo: String,
)

val LIGHT_MAP = MapPalette(
    water = "#A9CFE0", land = "#F3F2EE", green = "#D3E8CC", residential = "#EEECE6", industrial = "#E7E3DF",
    building = "#DEDAD3", minorRoad = "#FFFFFF", majorRoad = "#FFFFFF", highway = "#F5D49A",
    casing = "#CFCAC0", path = "#B9B3A8", label = "#3F4B47", labelHalo = "#FCFEFD",
)

val DARK_MAP = MapPalette(
    water = "#1A2B33", land = "#141B19", green = "#1C2B23", residential = "#171F1D", industrial = "#1B2120",
    building = "#232C29", minorRoad = "#33403C", majorRoad = "#46534F", highway = "#7A6237",
    casing = "#0E1513", path = "#3A4541", label = "#B9C8C3", labelHalo = "#0E1513",
)

/** Font stack bundled under assets/fonts. */
const val MAP_FONT: String = "Noto Sans Regular"

/** English name where the map has one, the local name otherwise. */
private const val NAME = """["coalesce",["get","name:en"],["get","name"]]"""

/**
 * The style for the map file at [pmtilesPath] (an absolute path on the device).
 * [attribution] is shown by MapLibre's attribution control if enabled; the app also shows it as text.
 */
fun offlineStyleJson(pmtilesPath: String, palette: MapPalette, attribution: String = MAP_ATTRIBUTION): String {
    val p = palette
    fun roadWidth(base: Double, max: Double) =
        """["interpolate",["exponential",1.6],["zoom"],10,$base,18,$max]"""
    val layers = listOf(
        """{"id":"background","type":"background","paint":{"background-color":"${p.water}"}}""",
        """{"id":"earth","type":"fill","source":"base","source-layer":"earth","paint":{"fill-color":"${p.land}"}}""",
        """{"id":"landuse-green","type":"fill","source":"base","source-layer":"landuse",
            "filter":["in",["get","kind"],["literal",["park","grass","wood","forest","cemetery","nature_reserve","garden","golf_course","pitch","recreation_ground"]]],
            "paint":{"fill-color":"${p.green}"}}""",
        """{"id":"landuse-residential","type":"fill","source":"base","source-layer":"landuse",
            "filter":["in",["get","kind"],["literal",["residential","pedestrian"]]],"paint":{"fill-color":"${p.residential}"}}""",
        """{"id":"landuse-industrial","type":"fill","source":"base","source-layer":"landuse",
            "filter":["in",["get","kind"],["literal",["industrial","commercial","railway","aerodrome"]]],"paint":{"fill-color":"${p.industrial}"}}""",
        """{"id":"water-area","type":"fill","source":"base","source-layer":"water",
            "filter":["==",["geometry-type"],"Polygon"],"paint":{"fill-color":"${p.water}"}}""",
        """{"id":"water-line","type":"line","source":"base","source-layer":"water",
            "filter":["==",["geometry-type"],"LineString"],
            "paint":{"line-color":"${p.water}","line-width":["interpolate",["linear"],["zoom"],10,0.5,16,3]}}""",
        """{"id":"buildings","type":"fill","source":"base","source-layer":"buildings","minzoom":14,
            "paint":{"fill-color":"${p.building}","fill-opacity":["interpolate",["linear"],["zoom"],14,0.4,16,1]}}""",
        """{"id":"roads-path","type":"line","source":"base","source-layer":"roads","minzoom":14,
            "filter":["==",["get","kind"],"path"],
            "paint":{"line-color":"${p.path}","line-width":1,"line-dasharray":[2,2]}}""",
        """{"id":"roads-casing","type":"line","source":"base","source-layer":"roads",
            "filter":["in",["get","kind"],["literal",["minor_road","major_road","highway"]]],
            "layout":{"line-cap":"round","line-join":"round"},
            "paint":{"line-color":"${p.casing}","line-width":1,
                "line-gap-width":["interpolate",["exponential",1.6],["zoom"],
                    10,["match",["get","kind"],"highway",1.2,"major_road",0.8,0.3],
                    18,["match",["get","kind"],"highway",16,"major_road",12,7]]}}""",
        """{"id":"roads-minor","type":"line","source":"base","source-layer":"roads",
            "filter":["==",["get","kind"],"minor_road"],"layout":{"line-cap":"round","line-join":"round"},
            "paint":{"line-color":"${p.minorRoad}","line-width":${roadWidth(0.3, 7.0)}}}""",
        """{"id":"roads-major","type":"line","source":"base","source-layer":"roads",
            "filter":["==",["get","kind"],"major_road"],"layout":{"line-cap":"round","line-join":"round"},
            "paint":{"line-color":"${p.majorRoad}","line-width":${roadWidth(0.8, 12.0)}}}""",
        """{"id":"roads-highway","type":"line","source":"base","source-layer":"roads",
            "filter":["==",["get","kind"],"highway"],"layout":{"line-cap":"round","line-join":"round"},
            "paint":{"line-color":"${p.highway}","line-width":${roadWidth(1.2, 16.0)}}}""",
        """{"id":"road-labels","type":"symbol","source":"base","source-layer":"roads","minzoom":14,
            "filter":["in",["get","kind"],["literal",["minor_road","major_road","highway"]]],
            "layout":{"symbol-placement":"line","text-field":$NAME,"text-font":["$MAP_FONT"],"text-size":11},
            "paint":{"text-color":"${p.label}","text-halo-color":"${p.labelHalo}","text-halo-width":1.5}}""",
        """{"id":"place-labels","type":"symbol","source":"base","source-layer":"places",
            "filter":["in",["get","kind"],["literal",["locality","neighbourhood","suburb","macrohood"]]],
            "layout":{"text-field":$NAME,"text-font":["$MAP_FONT"],
                "text-size":["match",["get","kind"],"locality",14,12],"text-max-width":8},
            "paint":{"text-color":"${p.label}","text-halo-color":"${p.labelHalo}","text-halo-width":2}}""",
    )
    val escapedAttribution = attribution.replace("\"", "\\\"")
    return """{"version":8,"name":"Cadence offline",
        "glyphs":"asset://fonts/{fontstack}/{range}.pbf",
        "sources":{"base":{"type":"vector","url":"pmtiles://file://$pmtilesPath","attribution":"$escapedAttribution"}},
        "layers":[${layers.joinToString(",")}]}"""
}

/** Shown under the map: the data is OpenStreetMap (ODbL), the tile build is Protomaps. */
const val MAP_ATTRIBUTION: String = "© OpenStreetMap contributors · Protomaps · MapLibre"
