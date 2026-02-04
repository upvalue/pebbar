package io.upvalue.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceJsonParserTest {

    @Test
    fun `parse valid single text element`() {
        val json = """{"face":[{"type":"text","value":"Hello"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(1, elements.size)
        assertEquals(0, elements[0].type)
        assertEquals("Hello", elements[0].value)
    }

    @Test
    fun `parse valid multi-element JSON`() {
        val json = """{"face":[{"type":"text","value":"Line 1"},{"type":"text","value":"Line 2"},{"type":"text","value":"Line 3"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(3, elements.size)
        assertEquals("Line 1", elements[0].value)
        assertEquals("Line 2", elements[1].value)
        assertEquals("Line 3", elements[2].value)
    }

    @Test
    fun `parse empty face array returns empty list`() {
        val json = """{"face":[]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(0, elements.size)
    }

    @Test
    fun `parse missing face array returns error`() {
        val json = """{"other":"value"}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Error)
        assertTrue((result as FaceJsonParser.ParseResult.Error).message.contains("face"))
    }

    @Test
    fun `parse invalid JSON returns error`() {
        val result = FaceJsonParser.parse("not json")
        assertTrue(result is FaceJsonParser.ParseResult.Error)
        assertTrue((result as FaceJsonParser.ParseResult.Error).message.contains("Invalid JSON"))
    }

    @Test
    fun `parse too many elements truncates to 4`() {
        val json = """{"face":[
            {"type":"text","value":"1"},
            {"type":"text","value":"2"},
            {"type":"text","value":"3"},
            {"type":"text","value":"4"},
            {"type":"text","value":"5"}
        ]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(4, elements.size)
        assertEquals("4", elements[3].value)
    }

    @Test
    fun `parse unknown type returns error`() {
        val json = """{"face":[{"type":"image","value":"test"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Error)
        assertTrue((result as FaceJsonParser.ParseResult.Error).message.contains("Unknown element type"))
    }

    @Test
    fun `parse missing type returns error`() {
        val json = """{"face":[{"value":"test"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Error)
        assertTrue((result as FaceJsonParser.ParseResult.Error).message.contains("Unknown element type"))
    }

    @Test
    fun `parse long value is truncated to 255 chars`() {
        val longValue = "A".repeat(300)
        val json = """{"face":[{"type":"text","value":"$longValue"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(255, elements[0].value.length)
    }

    @Test
    fun `parse element that is not an object returns error`() {
        val json = """{"face":["not an object"]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Error)
        assertTrue((result as FaceJsonParser.ParseResult.Error).message.contains("not an object"))
    }

    @Test
    fun `wrapQuickText wraps plain text in JSON format`() {
        val result = FaceJsonParser.wrapQuickText("Hello world")
        val parsed = FaceJsonParser.parse(result)
        assertTrue(parsed is FaceJsonParser.ParseResult.Success)
        val elements = (parsed as FaceJsonParser.ParseResult.Success).elements
        assertEquals(1, elements.size)
        assertEquals("Hello world", elements[0].value)
    }

    @Test
    fun `wrapQuickText escapes special characters`() {
        val result = FaceJsonParser.wrapQuickText("He said \"hello\" & 'bye'")
        val parsed = FaceJsonParser.parse(result)
        assertTrue(parsed is FaceJsonParser.ParseResult.Success)
        val elements = (parsed as FaceJsonParser.ParseResult.Success).elements
        assertEquals("He said \"hello\" & 'bye'", elements[0].value)
    }

    @Test
    fun `wrapQuickText handles backslashes and newlines`() {
        val result = FaceJsonParser.wrapQuickText("line1\nline2\\end")
        val parsed = FaceJsonParser.parse(result)
        assertTrue(parsed is FaceJsonParser.ParseResult.Success)
        val elements = (parsed as FaceJsonParser.ParseResult.Success).elements
        assertEquals("line1\nline2\\end", elements[0].value)
    }

    @Test
    fun `parse value at exactly 255 chars is not truncated`() {
        val exactValue = "B".repeat(255)
        val json = """{"face":[{"type":"text","value":"$exactValue"}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(255, elements[0].value.length)
        assertEquals(exactValue, elements[0].value)
    }

    @Test
    fun `parse empty value is allowed`() {
        val json = """{"face":[{"type":"text","value":""}]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals("", elements[0].value)
    }

    @Test
    fun `parse exactly 4 elements succeeds`() {
        val json = """{"face":[
            {"type":"text","value":"A"},
            {"type":"text","value":"B"},
            {"type":"text","value":"C"},
            {"type":"text","value":"D"}
        ]}"""
        val result = FaceJsonParser.parse(json)
        assertTrue(result is FaceJsonParser.ParseResult.Success)
        val elements = (result as FaceJsonParser.ParseResult.Success).elements
        assertEquals(4, elements.size)
        assertEquals("A", elements[0].value)
        assertEquals("D", elements[3].value)
    }

    @Test
    fun `wrapQuickText with empty string produces valid JSON`() {
        val result = FaceJsonParser.wrapQuickText("")
        val parsed = FaceJsonParser.parse(result)
        assertTrue(parsed is FaceJsonParser.ParseResult.Success)
        val elements = (parsed as FaceJsonParser.ParseResult.Success).elements
        assertEquals(1, elements.size)
        assertEquals("", elements[0].value)
    }

    @Test
    fun `wrapQuickText with unicode characters`() {
        val result = FaceJsonParser.wrapQuickText("Hello \u2603 world")
        val parsed = FaceJsonParser.parse(result)
        assertTrue(parsed is FaceJsonParser.ParseResult.Success)
        val elements = (parsed as FaceJsonParser.ParseResult.Success).elements
        assertEquals("Hello \u2603 world", elements[0].value)
    }

    @Test
    fun `FaceElement data class equality`() {
        val a = FaceJsonParser.FaceElement(type = 0, value = "test")
        val b = FaceJsonParser.FaceElement(type = 0, value = "test")
        assertEquals(a, b)
    }
}
