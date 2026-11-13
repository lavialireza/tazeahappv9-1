package com.example.bookapp.data

/** قرارداد قابل تعویض برای مدل واقعی؛ خروجی باید منبع/نسخه مدل و میزان اطمینان داشته باشد. */
data class AiModelRequest(val task: String, val inputText: String, val language: String = "fa", val sourceUid: String? = null)
data class AiModelResponse(val outputText: String, val modelId: String, val modelVersion: String, val confidence: Float?, val citations: List<String>, val isGenerated: Boolean = true)
interface TaziehAiModelProvider {
    val providerId: String
    val supportedTasks: Set<String>
    suspend fun run(request: AiModelRequest): AiModelResponse
}
object AiModelGateway {
    suspend fun run(provider: TaziehAiModelProvider, request: AiModelRequest): AiModelResponse {
        require(request.task in provider.supportedTasks) { "مدل انتخاب‌شده این وظیفه را پشتیبانی نمی‌کند." }
        require(request.inputText.isNotBlank() && request.inputText.length <= 500_000) { "متن ورودی خالی یا بیش از حد بزرگ است." }
        val response = provider.run(request)
        require(response.outputText.length <= 1_000_000) { "پاسخ مدل بیش از حد بزرگ است." }
        require(response.confidence == null || response.confidence in 0f..1f) { "درجه اطمینان مدل معتبر نیست." }
        return response
    }
}

/** Adapter for a real embedding model; no lexical fallback is mislabeled as semantic AI. */
interface TextEmbeddingProvider {
    val modelId: String
    val dimension: Int
    suspend fun embed(text: String, language: String = "fa"): FloatArray
}
object EmbeddingSimilarity {
    fun cosine(left: FloatArray, right: FloatArray): Float {
        require(left.isNotEmpty() && left.size == right.size) { "بردارها باید هم‌اندازه و غیرخالی باشند." }
        var dot = 0.0; var leftNorm = 0.0; var rightNorm = 0.0
        for (i in left.indices) {
            require(left[i].isFinite() && right[i].isFinite()) { "بردار شامل مقدار نامعتبر است." }
            dot += left[i] * right[i]; leftNorm += left[i] * left[i]; rightNorm += right[i] * right[i]
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0f
        return (dot / (kotlin.math.sqrt(leftNorm) * kotlin.math.sqrt(rightNorm))).toFloat().coerceIn(-1f, 1f)
    }
}
