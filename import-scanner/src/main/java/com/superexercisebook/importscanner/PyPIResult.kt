package com.superexercisebook.importscanner

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable


@Serializable
data class PyPIResult(val info: PyPIInfo, val releases: Map<String, List<PyPIResultRelease>>)

@Serializable
data class PyPIInfo(
    val author: String? = null,
)

@Serializable
data class PyPIResultRelease(
    val upload_time: LocalDateTime? = null,
)
