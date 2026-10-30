package com.ai.assistance.operit.data.llama

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** Metadata read from the GGUF header (everything before the tensor payload). */
data class GgufMetadata(
    val version: Int,
    val architecture: String?,
    val modelName: String?,
    val contextLength: Int?,
    val embeddingLength: Int?,
    val parameterCount: Long?,
    val hasChatTemplate: Boolean
)

class GgufParseException(message: String) : IOException(message)

/**
 * Strict reader for the GGUF header (v2/v3, little-endian).
 *
 * Layout per the llama.cpp gguf spec:
 * magic u32 "GGUF" | version u32 | tensor_count u64 | kv_count u64 |
 * kv_count * (key string, value_type u32, value) | tensor info...
 *
 * Only the header region is read; the tensor payload is never touched, so a
 * multi-GB model costs a few hundred KB of IO. Anything that does not match
 * the spec raises [GgufParseException] - there is no lenient mode, because a
 * mis-parsed header silently reporting wrong metadata is worse than an error.
 */
object GgufHeaderParser {

    private const val MAGIC = 0x46554747 // "GGUF" read as little-endian u32
    private const val SUPPORTED_VERSIONS = intArrayOf(2, 3)

    private const val MAX_STRING_BYTES = 4L * 1024 * 1024
    private const val MAX_COLLECTION_COUNT = 1_000_000L

    // GGUFValueType
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun parse(file: File): GgufMetadata {
        if (!file.isFile) {
            throw GgufParseException("not a file: ${file.path}")
        }
        FileInputStream(file).use { raw ->
            BufferedInputStream(raw).use { stream ->
                return parse(stream)
            }
        }
    }

    fun parse(input: InputStream): GgufMetadata {
        val stream = DataInputStream(input)
        val magic = stream.readLeU32()
        if (magic != MAGIC) {
            throw GgufParseException("GGUF magic invalid")
        }
        val version = stream.readLeU32().toInt()
        if (version !in SUPPORTED_VERSIONS) {
            throw GgufParseException("unsupported GGUF version: $version")
        }
        // tensor_count is not needed: tensor info lives after the KV section.
        stream.readLeU64()
        val kvCount = stream.readLeU64()
        if (kvCount < 0 || kvCount > MAX_COLLECTION_COUNT) {
            throw GgufParseException("implausible metadata_kv_count: $kvCount")
        }

        var architecture: String? = null
        var modelName: String? = null
        var parameterCount: Long? = null
        var contextLength: Int? = null
        var embeddingLength: Int? = null
        var hasChatTemplate = false

        for (index in 0 until kvCount) {
            val key = stream.readString()
            when (val valueType = stream.readLeU32().toInt()) {
                TYPE_STRING -> {
                    val value = stream.readString()
                    when (key) {
                        "general.architecture" -> architecture = value
                        "general.name" -> modelName = value
                        "tokenizer.chat_template" -> hasChatTemplate = true
                    }
                }
                TYPE_UINT32 -> {
                    val value = stream.readLeU32()
                    if (key.endsWith(".context_length")) {
                        contextLength = value.toInt()
                    } else if (key.endsWith(".embedding_length")) {
                        embeddingLength = value.toInt()
                    }
                }
                TYPE_UINT64 -> {
                    val value = stream.readLeU64()
                    if (key == "general.parameter_count") {
                        parameterCount = value
                    }
                }
                TYPE_ARRAY -> stream.skipArrayValue()
                else -> stream.skipScalarValue(valueType, key)
            }
        }

        return GgufMetadata(
            version = version,
            architecture = architecture,
            modelName = modelName,
            contextLength = contextLength,
            embeddingLength = embeddingLength,
            parameterCount = parameterCount,
            hasChatTemplate = hasChatTemplate
        )
    }

    private fun DataInputStream.readLeU32(): Long {
        val bytes = ByteArray(4)
        readFully(bytes)
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }

    private fun DataInputStream.readLeU64(): Long {
        val bytes = ByteArray(8)
        readFully(bytes)
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((bytes[index].toLong() and 0xFF) shl (8 * index))
        }
        return value
    }

    private fun DataInputStream.readString(): String {
        val length = readLeU64()
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw GgufParseException("implausible string length: $length")
        }
        if (length == 0L) {
            return ""
        }
        val bytes = ByteArray(length.toInt())
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    /** Consumes an array value: element type u32, count u64, then the elements. */
    private fun DataInputStream.skipArrayValue() {
        val elementType = readLeU32().toInt()
        if (elementType == TYPE_ARRAY) {
            // The spec does not allow nested arrays.
            throw GgufParseException("nested GGUF arrays are not supported")
        }
        val count = readLeU64()
        if (count < 0 || count > MAX_COLLECTION_COUNT) {
            throw GgufParseException("implausible GGUF array length: $count")
        }
        for (index in 0 until count) {
            skipScalarValue(elementType, "array element $index")
        }
    }

    private fun DataInputStream.skipScalarValue(type: Int, key: String) {
        when (type) {
            TYPE_STRING -> readString()
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skipExact(1)
            TYPE_UINT16, TYPE_INT16 -> skipExact(2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skipExact(4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skipExact(8)
            else -> throw GgufParseException("unknown GGUF value type $type for key '$key'")
        }
    }

    private fun DataInputStream.skipExact(byteCount: Int) {
        readFully(ByteArray(byteCount))
    }
}
