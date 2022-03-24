package com.superexercisebook.importscanner

import java.time.LocalDateTime


fun PyPIResult.isAcceptable(): Result<Unit> {
    val latestVersion = this.info.version

    val latestRelease = this.releases[latestVersion]
        ?: return Result.failure(IllegalStateException("No release found for package ${this.info.name} version $latestVersion"))

    latestRelease.forEach { release ->
        if (release.isAcceptable().isSuccess) {
            return Result.success(Unit)
        }
    }

    return Result.failure(
        IllegalStateException("${this.info.name} is not acceptable. Latest version: $latestVersion. \n" +
                latestRelease.map {
                    if (it.pythonVersion.equals("source", ignoreCase = true))
                        "Source release " + it.uploadTime
                    else
                        it.pythonVersion
                }.joinToString("\n"))
    )
}

private fun PyPIResultRelease.isAcceptable(): Result<Unit> {
    val pythonVersion = this.pythonVersion
    if (pythonVersion != null) {
        if (pythonVersion.startsWith("3") ||
            pythonVersion.contains("py3", ignoreCase = true) ||
            pythonVersion.contains("cp3", ignoreCase = true) ||
            (
                    pythonVersion.equals("source", ignoreCase = true) &&
                            this.uploadTime > LocalDateTime.of(2019, 1, 1, 0, 0, 0)
                    )
        ) {
            return Result.success(Unit)
        }
    }
    return Result.failure(IllegalStateException())
}

fun Map<String, PyPIResult>.getAcceptableVersion(): Map<String, PyPIResult> {
    val ret = mutableMapOf<String, PyPIResult>()

    for ((name, pypiMeta) in this) {
        ret[name] = PyPIResult(pypiMeta.info, mutableMapOf<String, List<PyPIResultRelease>>().also { releaseMap ->
            for ((pypiVersion, pypiReleases) in pypiMeta.releases) {
                if (pypiReleases.isEmpty()) {
                    continue
                }

                mutableListOf<PyPIResultRelease>().also { releaseList ->
                    for (pypiReleaseItem in pypiReleases) {
                        if (pypiReleaseItem.isAcceptable().isSuccess) {
                            releaseList.add(pypiReleaseItem)
                        }
                    }
                }.also {
                    if (it.isNotEmpty()) {
                        releaseMap[pypiVersion] = it
                    }
                }
            }
        })
    }

    return ret
}
