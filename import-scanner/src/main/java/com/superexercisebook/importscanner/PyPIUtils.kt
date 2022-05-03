package com.superexercisebook.importscanner

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import java.time.LocalDateTime

object PyPIUtils {
    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer(jsonMapper)
        }
    }

    val pypiPackageSpecification = mapOf(
        "attr" to "attrs",
        "skimage" to "scikit-image",
        "sklearn" to "scikit-learn",
        "cv2" to "opencv-python",
        "OpenSSL" to "pyOpenSSL",
        "pydispatch" to "PyDispatcher",
        "pil" to "Pillow",
        "dotenv" to "python-dotenv",
        "docx" to "python-docx"
    )

    suspend fun findInPypi(item: String, pythonVersion: String, releaseBefore: LocalDateTime): Result<PyPIResult> =
        pypiPackageSpecification.getOrDefault(item, item).let {
            try {
                val result: PyPIResult = httpClient.get("https://pypi.org/pypi/$it/json")
                val acceptable = result.isAcceptable(pythonVersion, releaseBefore)
                if (acceptable.isSuccess) {
                    Result.success(result)
                } else {
                    Result.failure(acceptable.exceptionOrNull()!!)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
