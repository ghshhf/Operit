package com.ai.assistance.operit.data.llama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaModelCatalogTest {

    @Test
    fun idsAreUnique() {
        val ids = LlamaModelCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun fileNamesAreUnique() {
        val names = LlamaModelCatalog.models.map { it.fileName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyEntryIsWellFormed() {
        LlamaModelCatalog.models.forEach { model ->
            assertTrue("id must not be blank: ${model.id}", model.id.isNotBlank())
            assertTrue("displayName must not be blank: ${model.id}", model.displayName.isNotBlank())
            assertTrue(
                "fileName must end with .gguf: ${model.fileName}",
                model.fileName.lowercase().endsWith(".gguf")
            )
            assertTrue("sizeBytes must be positive: ${model.id}", model.sizeBytes > 0)
            assertTrue("contextLength must be positive: ${model.id}", model.contextLength > 0)
            assertTrue("parameterCount must not be blank: ${model.id}", model.parameterCount.isNotBlank())
            assertTrue("quantization must not be blank: ${model.id}", model.quantization.isNotBlank())
            assertTrue("description must not be blank: ${model.id}", model.description.isNotBlank())
        }
    }

    @Test
    fun everyEntryHasAtLeastOneSource() {
        LlamaModelCatalog.models.forEach { model ->
            assertTrue("no download source: ${model.id}", model.sources.isNotEmpty())
            model.sources.forEach { (label, url) ->
                assertTrue("source label must not be blank: ${model.id}", label.isNotBlank())
                assertTrue("url must be https: $url", url.startsWith("https://"))
                if (label == LlamaModelCatalog.SOURCE_MODELSCOPE) {
                    assertTrue("unexpected ModelScope host: $url", url.contains("modelscope.cn"))
                }
                if (label == LlamaModelCatalog.SOURCE_HUGGINGFACE) {
                    assertTrue("unexpected HuggingFace host: $url", url.contains("huggingface.co"))
                }
            }
        }
    }

    @Test
    fun entriesAreOrderedBySize() {
        val sizes = LlamaModelCatalog.models.map { it.sizeBytes }
        assertEquals(sizes.sorted(), sizes)
    }

    @Test
    fun findByIdResolvesKnownAndUnknown() {
        val first = LlamaModelCatalog.models.first()
        assertNotNull(LlamaModelCatalog.findById(first.id))
        assertEquals(first, LlamaModelCatalog.findById(first.id))
        assertNull(LlamaModelCatalog.findById("does-not-exist"))
    }
}
