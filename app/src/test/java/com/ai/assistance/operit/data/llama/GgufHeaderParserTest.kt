package com.ai.assistance.operit.data.llama

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufHeaderParserTest {

    @Test
    fun parsesMinimalHeader() {
        val bytes = GgufWriter()
            .magic()
            .u32(3) // version
            .u64(0) // tensor_count
            .u64(0) // kv_count
            .bytes()

        val metadata = GgufHeaderParser.parse(ByteArrayInputStream(bytes))

        assertEquals(3, metadata.version)
        assertNull(metadata.architecture)
        assertNull(metadata.contextLength)
        assertFalse(metadata.hasChatTemplate)
    }

    @Test
    fun parsesKnownKeys() {
        val bytes = GgufWriter()
            .magic()
            .u32(2)
            .u64(12)
            .u64(5)
            .kvString("general.architecture", "qwen3")
            .kvString("general.name", "Test Model")
            .kvString("tokenizer.chat_template", "{% for m in messages %}")
            .kvU32("qwen3.context_length", 32768)
            .kvU64("general.parameter_count", 1_700_000_000L)
            .bytes()

        val metadata = GgufHeaderParser.parse(ByteArrayInputStream(bytes))

        assertEquals(2, metadata.version)
        assertEquals("qwen3", metadata.architecture)
        assertEquals("Test Model", metadata.modelName)
        assertEquals(32768, metadata.contextLength)
        assertEquals(1_700_000_000L, metadata.parameterCount)
        assertTrue(metadata.hasChatTemplate)
    }

    @Test
    fun skipsArrayValuesAndKeepsParsing() {
        val bytes = GgufWriter()
            .magic()
            .u32(3)
            .u64(0)
            .u64(2)
            .arrayString("tokenizer.ggml.tokens", listOf("hello", "world"))
            .kvU32("llama.context_length", 4096)
            .bytes()

        val metadata = GgufHeaderParser.parse(ByteArrayInputStream(bytes))

        assertEquals(4096, metadata.contextLength)
    }

    @Test
    fun embeddingLengthIsCaptured() {
        val bytes = GgufWriter()
            .magic()
            .u32(3)
            .u64(0)
            .u64(1)
            .kvU32("qwen3.embedding_length", 2048)
            .bytes()

        val metadata = GgufHeaderParser.parse(ByteArrayInputStream(bytes))

        assertEquals(2048, metadata.embeddingLength)
    }

    @Test(expected = GgufParseException::class)
    fun rejectsBadMagic() {
        val bytes = ByteArrayOutputStream().apply {
            write("NOPE".toByteArray())
        }.toByteArray()

        GgufHeaderParser.parse(ByteArrayInputStream(bytes))
    }

    @Test(expected = GgufParseException::class)
    fun rejectsUnsupportedVersion() {
        val bytes = GgufWriter()
            .magic()
            .u32(4)
            .u64(0)
            .u64(0)
            .bytes()

        GgufHeaderParser.parse(ByteArrayInputStream(bytes))
    }

    @Test(expected = GgufParseException::class)
    fun rejectsImplausibleKvCount() {
        val bytes = GgufWriter()
            .magic()
            .u32(3)
            .u64(0)
            .u64(1_000_001)
            .bytes()

        GgufHeaderParser.parse(ByteArrayInputStream(bytes))
    }

    @Test(expected = GgufParseException::class)
    fun rejectsNestedArrays() {
        val bytes = GgufWriter()
            .magic()
            .u32(3)
            .u64(0)
            .u64(1)
            .beginKv("some.array")
            .u32(9) // element type: array (nested is illegal)
            .u64(1)
            .bytes()

        GgufHeaderParser.parse(ByteArrayInputStream(bytes))
    }

    private class GgufWriter {
        private val out = ByteArrayOutputStream()

        fun magic() = apply { out.write("GGUF".toByteArray()) }

        fun u32(value: Long) = apply {
            out.write(
                byteArrayOf(
                    (value and 0xFF).toByte(),
                    ((value shr 8) and 0xFF).toByte(),
                    ((value shr 16) and 0xFF).toByte(),
                    ((value shr 24) and 0xFF).toByte()
                )
            )
        }

        fun u64(value: Long) = apply {
            for (index in 0 until 8) {
                out.write(((value shr (8 * index)) and 0xFF).toByte())
            }
        }

        fun string(value: String) = apply {
            u64(value.toByteArray(Charsets.UTF_8).size.toLong())
            out.write(value.toByteArray(Charsets.UTF_8))
        }

        /** key + u32 value_type + string value */
        fun kvString(key: String, value: String) = apply {
            string(key)
            u32(8)
            string(value)
        }

        /** key + u32 value_type + u32 value */
        fun kvU32(key: String, value: Long) = apply {
            string(key)
            u32(4)
            u32(value)
        }

        /** key + u64 value_type + u64 value */
        fun kvU64(key: String, value: Long) = apply {
            string(key)
            u32(10)
            u64(value)
        }

        /** key + array type, element type u32 (string), count u64, elements */
        fun arrayString(key: String, values: List<String>) = apply {
            string(key)
            u32(9)
            u32(8)
            u64(values.size.toLong())
            values.forEach { string(it) }
        }

        /** key only; the caller appends the value_type next (for malformed input) */
        fun beginKv(key: String) = apply { string(key) }

        fun bytes() = out.toByteArray()
    }
}
