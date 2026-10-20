package com.ai.assistance.operit.data.llama

/**
 * A GGUF model entry in the built-in catalog.
 *
 * The catalog ships with the app and every URL was verified against the
 * upstream host before release, so rendering it needs no network call and no
 * cache/fallback path.
 */
data class LlamaCatalogModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val parameterCount: String,
    val quantization: String,
    val contextLength: Int,
    val description: String,
    val tags: List<String> = emptyList(),
    /** Source label -> direct download URL. Map order defines display order. */
    val sources: Map<String, String> = emptyMap()
)

object LlamaModelCatalog {

    const val SOURCE_MODELSCOPE = "ModelScope"
    const val SOURCE_HUGGINGFACE = "HuggingFace"

    private fun modelscopeFileUrl(repo: String, file: String): String =
        "https://modelscope.cn/api/v1/models/$repo/repo?FilePath=$file"

    private fun huggingFaceFileUrl(repo: String, file: String): String =
        "https://huggingface.co/$repo/resolve/main/$file"

    /**
     * Curated phone-friendly instruct GGUF models (Q4_K_M), ordered by size.
     *
     * Sizes are the exact upstream file sizes in bytes; context lengths are
     * the models' native limits and only serve as a hint in the UI.
     */
    val models: List<LlamaCatalogModel> = listOf(
        LlamaCatalogModel(
            id = "gemma-3-270m-it-q4_k_m",
            displayName = "Gemma 3 270M",
            fileName = "gemma-3-270m-it-Q4_K_M.gguf",
            sizeBytes = 253_115_424L,
            parameterCount = "0.27B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "Google 最小 Gemma，适合低内存设备与快速问答",
            tags = listOf("轻量", "英文"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/gemma-3-270m-it-GGUF",
                    "gemma-3-270m-it-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "qwen3-0.6b-q4_k_m",
            displayName = "Qwen3 0.6B",
            fileName = "Qwen3-0.6B-Q4_K_M.gguf",
            sizeBytes = 396_705_472L,
            parameterCount = "0.6B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "通义千问3 最小杯，中英文混合，低功耗设备首选",
            tags = listOf("轻量", "中文"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/Qwen3-0.6B-GGUF",
                    "Qwen3-0.6B-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "gemma-3-1b-it-q4_k_m",
            displayName = "Gemma 3 1B",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 806_058_272L,
            parameterCount = "1B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "Gemma 3 指令版，综合质量稳定的轻量选择",
            tags = listOf("轻量", "英文"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/gemma-3-1b-it-GGUF",
                    "gemma-3-1b-it-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "smollm2-1.7b-instruct-q4_k_m",
            displayName = "SmolLM2 1.7B Instruct",
            fileName = "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_055_609_504L,
            parameterCount = "1.7B",
            quantization = "Q4_K_M",
            contextLength = 8_192,
            description = "HuggingFace 出品小模型，指令跟随干净利落",
            tags = listOf("轻量", "指令"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/SmolLM2-1.7B-Instruct-GGUF",
                    "SmolLM2-1.7B-Instruct-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "qwen3-1.7b-q4_k_m",
            displayName = "Qwen3 1.7B",
            fileName = "Qwen3-1.7B-Q4_K_M.gguf",
            sizeBytes = 1_107_409_472L,
            parameterCount = "1.7B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "通义千问3，中英文与代码能力均衡",
            tags = listOf("中文", "均衡"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/Qwen3-1.7B-GGUF",
                    "Qwen3-1.7B-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "llama-3.2-3b-instruct-q4_k_m",
            displayName = "Llama 3.2 3B Instruct",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_019_377_600L,
            parameterCount = "3B",
            quantization = "Q4_K_M",
            contextLength = 131_072,
            description = "Meta 官方 3B，128K 长上下文，生态工具兼容最好",
            tags = listOf("长上下文", "生态"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/Llama-3.2-3B-Instruct-GGUF",
                    "Llama-3.2-3B-Instruct-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "qwen2.5-3b-instruct-q4_k_m",
            displayName = "Qwen2.5 3B Instruct",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            sizeBytes = 2_104_932_768L,
            parameterCount = "3B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "通义千问2.5，中文写作与代码表现稳健",
            tags = listOf("中文", "代码"),
            sources = mapOf(
                SOURCE_MODELSCOPE to modelscopeFileUrl(
                    "Qwen/Qwen2.5-3B-Instruct-GGUF",
                    "qwen2.5-3b-instruct-q4_k_m.gguf"
                ),
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "Qwen/Qwen2.5-3B-Instruct-GGUF",
                    "qwen2.5-3b-instruct-q4_k_m.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "gemma-3-4b-it-q4_k_m",
            displayName = "Gemma 3 4B",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            sizeBytes = 2_489_894_016L,
            parameterCount = "4B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "Gemma 3 4B，同量级中综合质量第一梯队",
            tags = listOf("质量", "英文"),
            sources = mapOf(
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "unsloth/gemma-3-4b-it-GGUF",
                    "gemma-3-4b-it-Q4_K_M.gguf"
                )
            )
        ),
        LlamaCatalogModel(
            id = "qwen3-4b-q4_k_m",
            displayName = "Qwen3 4B",
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            sizeBytes = 2_497_280_256L,
            parameterCount = "4B",
            quantization = "Q4_K_M",
            contextLength = 32_768,
            description = "通义千问3 4B，目录内质量上限，建议 8GB 以上内存",
            tags = listOf("中文", "质量"),
            sources = mapOf(
                SOURCE_MODELSCOPE to modelscopeFileUrl(
                    "Qwen/Qwen3-4B-GGUF",
                    "Qwen3-4B-Q4_K_M.gguf"
                ),
                SOURCE_HUGGINGFACE to huggingFaceFileUrl(
                    "Qwen/Qwen3-4B-GGUF",
                    "Qwen3-4B-Q4_K_M.gguf"
                )
            )
        )
    )

    fun findById(id: String): LlamaCatalogModel? = models.firstOrNull { it.id == id }
}
