package com.superexercisebook.importscanner

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime


data class PyPIResult(
    @JsonProperty("info") val info: PyPIInfo,
    @JsonProperty("releases") val releases: Map<String, List<PyPIResultRelease>>,
)

data class PyPIInfo(
    @JsonProperty("author") val author: String? = null,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: String,

    @JsonProperty("requires_dist") val requiresDist: List<String>? = null,
)

data class PyPIResultRelease(
    @JsonProperty("upload_time") val uploadTime: LocalDateTime,
    @JsonProperty("python_version") val pythonVersion: String? = null,
)

data class DagNode(
    @JsonProperty("name") val name: String,
    @JsonProperty("children") val successorList: MutableList<DagNode>,
    @JsonProperty("precursor_count") var precursorCount: Int,
)