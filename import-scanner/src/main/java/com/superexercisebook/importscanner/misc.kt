package com.superexercisebook.importscanner

import java.time.LocalDateTime


fun PyPIResult.isAcceptable(): Boolean {
    val latestVersion = this.info.version

    val latestRelease = this.releases[latestVersion] ?: return false

    latestRelease.forEach { release ->
        val pythonVersion = release.pythonVersion
        if (pythonVersion != null) {
            if (pythonVersion.startsWith("3") ||
                pythonVersion.startsWith("py3", ignoreCase = true) ||
                pythonVersion.startsWith("pypy3", ignoreCase = true) ||
                pythonVersion.startsWith("cp3", ignoreCase = true) ||
                (pythonVersion.equals("source", ignoreCase = true) && release.uploadTime > LocalDateTime.of(2019, 1, 1, 0,0,0))
            ) {
                return true
            }
        }
    }

    return false
}