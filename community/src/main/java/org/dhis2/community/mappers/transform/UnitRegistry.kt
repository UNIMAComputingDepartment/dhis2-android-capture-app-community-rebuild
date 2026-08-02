package org.dhis2.community.mappers.transform

/**
 * Converts between units of the same dimension.
 *
 * The registry exists to stop failure mode F3: a binding that stores haemoglobin in `g/L` feeding a
 * canonical in `g/dL` must be scaled by 0.1, and writing `120` unscaled is a 10x clinical error.
 *
 * The one behaviour deliberately *not* offered is a fallback: an unrecognised unit returns null, the
 * validator turns that into a config error, and the author must either use a known unit or supply an
 * explicit `scaleToCanonical`. Assuming 1.0 for a unit we do not understand is exactly how F3
 * happens, so it is not reachable.
 */
object UnitRegistry {

    /** Factor to multiply a value by to reach the dimension's base unit. */
    private val factorToBase: Map<String, Pair<Dimension, Double>> = buildMap {
        // Mass — base gram
        put("kg", Dimension.MASS to 1000.0)
        put("g", Dimension.MASS to 1.0)
        put("mg", Dimension.MASS to 0.001)
        put("µg", Dimension.MASS to 0.000001)
        put("ug", Dimension.MASS to 0.000001)
        put("lb", Dimension.MASS to 453.59237)

        // Length — base centimetre (clinical convention: heights in cm)
        put("m", Dimension.LENGTH to 100.0)
        put("cm", Dimension.LENGTH to 1.0)
        put("mm", Dimension.LENGTH to 0.1)
        put("in", Dimension.LENGTH to 2.54)

        // Volume — base litre
        put("l", Dimension.VOLUME to 1.0)
        put("dl", Dimension.VOLUME to 0.1)
        put("ml", Dimension.VOLUME to 0.001)

        // Mass concentration — base g/L
        put("g/l", Dimension.MASS_CONCENTRATION to 1.0)
        put("g/dl", Dimension.MASS_CONCENTRATION to 10.0)
        put("mg/l", Dimension.MASS_CONCENTRATION to 0.001)
        put("mg/dl", Dimension.MASS_CONCENTRATION to 0.01)
        put("µg/l", Dimension.MASS_CONCENTRATION to 0.000001)
        put("ug/l", Dimension.MASS_CONCENTRATION to 0.000001)

        // Time — base day
        put("year", Dimension.TIME to 365.2425)
        put("years", Dimension.TIME to 365.2425)
        put("month", Dimension.TIME to 30.436875)
        put("months", Dimension.TIME to 30.436875)
        put("week", Dimension.TIME to 7.0)
        put("weeks", Dimension.TIME to 7.0)
        put("day", Dimension.TIME to 1.0)
        put("days", Dimension.TIME to 1.0)

        // Temperature — base degree Celsius. Only °C is registered on purpose: Fahrenheit needs an
        // offset as well as a factor, and a silent factor-only conversion would be wrong. An author
        // needing °F supplies scaleToCanonical and offset explicitly.
        put("c", Dimension.TEMPERATURE to 1.0)
        put("°c", Dimension.TEMPERATURE to 1.0)

        // Pressure — base mmHg
        put("mmhg", Dimension.PRESSURE to 1.0)
        put("kpa", Dimension.PRESSURE to 7.50062)
    }

    enum class Dimension { MASS, LENGTH, VOLUME, MASS_CONCENTRATION, TIME, TEMPERATURE, PRESSURE }

    fun isKnown(unit: String?): Boolean = normalise(unit)?.let { factorToBase.containsKey(it) } == true

    fun dimensionOf(unit: String?): Dimension? = normalise(unit)?.let { factorToBase[it]?.first }

    /**
     * The factor that converts a value expressed in [from] into [to], or null when either unit is
     * unknown or the two belong to different dimensions.
     *
     * Identical units — including both being null/blank, meaning "unitless" — return 1.0.
     */
    fun factor(from: String?, to: String?): Double? {
        val a = normalise(from)
        val b = normalise(to)
        if (a == null && b == null) return 1.0
        if (a == b) return 1.0
        if (a == null || b == null) return null

        val (dimA, factorA) = factorToBase[a] ?: return null
        val (dimB, factorB) = factorToBase[b] ?: return null
        if (dimA != dimB) return null

        return factorA / factorB
    }

    private fun normalise(unit: String?): String? =
        unit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}
