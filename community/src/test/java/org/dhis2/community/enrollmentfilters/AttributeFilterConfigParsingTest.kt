package org.dhis2.community.enrollmentfilters

import com.google.gson.Gson
import org.dhis2.community.enrollmentfilters.models.AttributeFilterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributeFilterConfigParsingTest {

    private val gson = Gson()

    @Test
    fun `typeOverrides as an empty array does not discard the program filter`() {
        val json =
            """{"programFilters":[{"attributes":["qNBy5NNq29E","EDjTF6dn75s"],""" +
                """"programUid":"NFnp1h3IMzl","typeOverrides":[]}]}"""

        val config = gson.fromJson(json, AttributeFilterConfig::class.java)

        val program = config.programFilters?.single()
        assertNotNull(program)
        assertEquals("NFnp1h3IMzl", program!!.programUid)
        assertEquals(listOf("qNBy5NNq29E", "EDjTF6dn75s"), program.attributes)
        assertTrue(program.typeOverrides.isNullOrEmpty())
    }

    @Test
    fun `typeOverrides as an object is parsed`() {
        val json =
            """{"programFilters":[{"attributes":["a"],"programUid":"p","typeOverrides":{"a":"AGE_RANGE"}}]}"""

        val program = gson.fromJson(json, AttributeFilterConfig::class.java).programFilters?.single()

        assertEquals(mapOf("a" to "AGE_RANGE"), program?.typeOverrides)
    }

    @Test
    fun `omitted typeOverrides is fine`() {
        val json = """{"programFilters":[{"attributes":["a"],"programUid":"p"}]}"""

        val program = gson.fromJson(json, AttributeFilterConfig::class.java).programFilters?.single()

        assertEquals(listOf("a"), program?.attributes)
        assertTrue(program?.typeOverrides.isNullOrEmpty())
    }
}
