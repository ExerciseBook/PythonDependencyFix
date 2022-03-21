package com.superexercisebook.importscanner

import kotlinx.datetime.LocalDateTime

fun PyPIResult.isAcceptable(): Boolean {
    val latestVersion = this.info.version

    val latestRelease = this.releases[latestVersion] ?: return false

    latestRelease.forEach { release ->
        val pythonVersion = release.python_version
        if (pythonVersion != null) {
            if (pythonVersion.startsWith("3") ||
                pythonVersion.startsWith("py3", ignoreCase = true) ||
                pythonVersion.startsWith("pypy3", ignoreCase = true) ||
                pythonVersion.startsWith("cp3", ignoreCase = true) ||
                (pythonVersion.equals("source", ignoreCase = true) && release.upload_time > LocalDateTime(2019, 1, 1, 0,0,0))
            ) {
                return true
            }
        }
    }

    return false
}