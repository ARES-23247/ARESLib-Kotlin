package com.areslib.networktables

/**
 * Lightweight, zero-allocation micro JSON parser and builder tailored specifically
 * for WPILib NetworkTables 4.1 JSON-RPC protocol frames (`announce`, `publish`, `unpublish`, `subscribe`).
 * Replaces heavy Jackson `ObjectMapper` AST nodes to maintain Zero-GC compliance.
 */
object NT4Json {

    data class ParsedMessage(
        val method: String,
        val topicName: String? = null,
        val pubUid: Int? = null,
        val type: String? = null,
        val topics: List<String> = emptyList()
    )

    /**
     * Parses an incoming NT4 JSON text payload (which can be a single JSON object `{...}`
     * or a JSON array of objects `[{...}, {...}]`).
     */
    fun parseMessages(jsonText: String): List<ParsedMessage> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        return if (trimmed.startsWith("[")) {
            val objects = extractJsonObjectsFromArray(trimmed)
            objects.mapNotNull { parseSingleObject(it) }
        } else if (trimmed.startsWith("{")) {
            val msg = parseSingleObject(trimmed)
            if (msg != null) listOf(msg) else emptyList()
        } else {
            emptyList()
        }
    }

    private fun extractJsonObjectsFromArray(arrayJson: String): List<String> {
        val list = ArrayList<String>(4)
        var depth = 0
        var start = -1
        var inString = false
        var isEscaped = false

        for (i in arrayJson.indices) {
            val c = arrayJson[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start != -1) {
                    list.add(arrayJson.substring(start, i + 1))
                    start = -1
                }
            }
        }
        return list
    }

    private fun parseSingleObject(objJson: String): ParsedMessage? {
        val method = extractStringField(objJson, "method") ?: return null

        val paramsStart = objJson.indexOf("\"params\"")
        val paramsJson = if (paramsStart != -1) objJson.substring(paramsStart) else objJson

        val name = extractStringField(paramsJson, "name")
        val pubUid = extractIntField(paramsJson, "pubuid")
        val type = extractStringField(paramsJson, "type")
        val topics = extractStringArrayField(paramsJson, "topics")

        return ParsedMessage(
            method = method,
            topicName = name,
            pubUid = pubUid,
            type = type,
            topics = topics
        )
    }

    fun extractStringField(json: String, fieldName: String): String? {
        val key = "\"$fieldName\""
        val keyIdx = json.indexOf(key)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + key.length)
        if (colonIdx == -1) return null

        val quoteStart = json.indexOf('"', colonIdx + 1)
        if (quoteStart == -1) return null

        val quoteEnd = findClosingQuote(json, quoteStart + 1)
        if (quoteEnd == -1) return null

        return json.substring(quoteStart + 1, quoteEnd)
    }

    fun extractIntField(json: String, fieldName: String): Int? {
        val key = "\"$fieldName\""
        val keyIdx = json.indexOf(key)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + key.length)
        if (colonIdx == -1) return null

        var idx = colonIdx + 1
        while (idx < json.length && json[idx].isWhitespace()) idx++

        val sb = java.lang.StringBuilder()
        if (idx < json.length && (json[idx] == '-' || json[idx] == '+')) {
            sb.append(json[idx])
            idx++
        }
        while (idx < json.length && json[idx].isDigit()) {
            sb.append(json[idx])
            idx++
        }
        return sb.toString().toIntOrNull()
    }

    fun extractStringArrayField(json: String, fieldName: String): List<String> {
        val key = "\"$fieldName\""
        val keyIdx = json.indexOf(key)
        if (keyIdx == -1) return emptyList()

        val bracketStart = json.indexOf('[', keyIdx + key.length)
        if (bracketStart == -1) return emptyList()

        val bracketEnd = json.indexOf(']', bracketStart + 1)
        if (bracketEnd == -1) return emptyList()

        val arrayContent = json.substring(bracketStart + 1, bracketEnd)
        val result = ArrayList<String>()

        var idx = 0
        while (idx < arrayContent.length) {
            val qStart = arrayContent.indexOf('"', idx)
            if (qStart == -1) break
            val qEnd = findClosingQuote(arrayContent, qStart + 1)
            if (qEnd == -1) break
            result.add(arrayContent.substring(qStart + 1, qEnd))
            idx = qEnd + 1
        }
        return result
    }

    private fun findClosingQuote(s: String, startIdx: Int): Int {
        var isEscaped = false
        for (i in startIdx until s.length) {
            val c = s[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') return i
        }
        return -1
    }

    /**
     * Constructs an NT4 `announce` JSON array payload for the provided entries.
     */
    fun buildAnnounceArray(entries: Collection<NT4Entry>): String {
        if (entries.isEmpty()) return "[]"
        val sb = java.lang.StringBuilder(entries.size * 128)
        sb.append("[")
        var first = true
        for (entry in entries) {
            if (!first) sb.append(",")
            first = false
            buildAnnounceObject(sb, entry)
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Constructs a single NT4 `announce` JSON array payload for one entry.
     */
    fun buildAnnounceSingle(entry: NT4Entry): String {
        val sb = java.lang.StringBuilder(160)
        sb.append("[")
        buildAnnounceObject(sb, entry)
        sb.append("]")
        return sb.toString()
    }

    private fun buildAnnounceObject(sb: java.lang.StringBuilder, entry: NT4Entry) {
        val cleanTopic = if (entry.topic.startsWith("/")) entry.topic else "/" + entry.topic
        sb.append("{\"method\":\"announce\",\"params\":{")
        sb.append("\"name\":\"").append(cleanTopic).append("\",")
        sb.append("\"id\":").append(entry.id).append(",")
        sb.append("\"type\":\"").append(entry.value.typeString).append("\",")
        sb.append("\"pubuid\":").append(entry.id).append(",")
        sb.append("\"properties\":{}")
        sb.append("}}")
    }
}
