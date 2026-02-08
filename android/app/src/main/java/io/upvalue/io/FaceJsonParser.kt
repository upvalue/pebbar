package io.upvalue.io

import com.getpebble.android.kit.util.PebbleDictionary
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object FaceJsonParser {

    private const val MAX_ELEMENTS = 4
    private const val MAX_VALUE_LEN = 255

    private const val KEY_FACE_COUNT = 0
    private fun keyElemType(i: Int) = 1 + i * 3
    private fun keyElemValue(i: Int) = 2 + i * 3
    private fun keyElemIcon(i: Int) = 3 + i * 3

    data class FaceElement(val type: Int, val value: String, val icon: String = "")

    sealed class ParseResult {
        data class Success(val elements: List<FaceElement>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun parse(json: String): ParseResult {
        val obj: JSONObject
        try {
            obj = JSONObject(json)
        } catch (e: JSONException) {
            return ParseResult.Error("Invalid JSON: ${e.message}")
        }

        val faceArray: JSONArray
        try {
            faceArray = obj.getJSONArray("face")
        } catch (e: JSONException) {
            return ParseResult.Error("Missing 'face' array")
        }

        if (faceArray.length() == 0) {
            return ParseResult.Success(emptyList())
        }

        val count = minOf(faceArray.length(), MAX_ELEMENTS)
        val elements = mutableListOf<FaceElement>()

        for (i in 0 until count) {
            val elem: JSONObject
            try {
                elem = faceArray.getJSONObject(i)
            } catch (e: JSONException) {
                return ParseResult.Error("Element $i is not an object")
            }

            val typeStr = elem.optString("type", "")
            if (typeStr != "text") {
                return ParseResult.Error("Unknown element type: '$typeStr'")
            }

            val value = elem.optString("value", "")
            val truncated = if (value.length > MAX_VALUE_LEN) {
                value.substring(0, MAX_VALUE_LEN)
            } else {
                value
            }

            val icon = elem.optString("icon", "")

            elements.add(FaceElement(type = 0, value = truncated, icon = icon))
        }

        return ParseResult.Success(elements)
    }

    fun toPebbleDictionary(elements: List<FaceElement>): PebbleDictionary {
        val dict = PebbleDictionary()
        dict.addUint8(KEY_FACE_COUNT, elements.size.toByte())

        for ((i, elem) in elements.withIndex()) {
            dict.addUint8(keyElemType(i), elem.type.toByte())
            dict.addString(keyElemValue(i), elem.value)
            dict.addString(keyElemIcon(i), elem.icon)
        }

        return dict
    }

    fun wrapQuickText(text: String): String {
        val elem = JSONObject()
            .put("type", "text")
            .put("value", text)
        val face = JSONArray().put(elem)
        return JSONObject().put("face", face).toString()
    }
}
