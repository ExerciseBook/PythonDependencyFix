package com.superexercisebook.importscanner

import java.time.LocalDateTime


fun PyPIResult.isAcceptable(): Result<Unit> {
    val latestVersion = this.info.version

    val latestRelease = this.releases[latestVersion]
        ?: return Result.failure(IllegalStateException("No release found for package ${this.info.name} version $latestVersion"))

    latestRelease.forEach { release ->
        val pythonVersion = release.pythonVersion
        if (pythonVersion != null) {
            if (pythonVersion.startsWith("3") ||
                pythonVersion.contains("py3", ignoreCase = true) ||
                pythonVersion.contains("cp3", ignoreCase = true) ||
                (
                        pythonVersion.equals("source", ignoreCase = true) &&
                                release.uploadTime > LocalDateTime.of(2019, 1, 1, 0, 0, 0)
                        )
            ) {
                return Result.success(Unit)
            }
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