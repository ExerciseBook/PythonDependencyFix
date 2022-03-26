package com.superexercisebook.importscanner

import io.github.g00fy2.versioncompare.Version
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

fun PyPIResultRelease.isAcceptable(releaseBefore: LocalDateTime = LocalDateTime.now()): Result<Unit> {
    val pythonVersion = this.pythonVersion
    if (this.uploadTime > releaseBefore) {
        return Result.failure(IllegalStateException())
    }
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

fun Map<String, PyPIResult>.getAcceptableVersion(releaseBefore: LocalDateTime = LocalDateTime.now()): Map<String, PyPIResult> {
    val ret = mutableMapOf<String, PyPIResult>()

    for ((name, pypiMeta) in this) {
        ret[name] = PyPIResult(pypiMeta.info, mutableMapOf<String, List<PyPIResultRelease>>().also { releaseMap ->
            for ((pypiVersion, pypiReleases) in pypiMeta.releases) {
                if (pypiReleases.isEmpty()) {
                    continue
                }

                mutableListOf<PyPIResultRelease>().also { releaseList ->
                    for (pypiReleaseItem in pypiReleases) {
                        if (pypiReleaseItem.isAcceptable(releaseBefore = releaseBefore).isSuccess) {
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

fun Map<String, PyPIResult>.toDag(): List<DagNode> {
    val dagPool = mapOf(* this.map { it.key to DagNode(it.key, mutableListOf(), 0) }.toTypedArray())

    for ((name, pypiMeta) in this) {
        val dependencies = pypiMeta.getDependenciesSet()

        val node = dagPool[name] ?: continue

        for (dependency in dependencies) {
            val dependencyNode = dagPool[dependency] ?: continue
            node.successorList.add(dependencyNode)
            dependencyNode.precursorCount += 1
        }
    }

    return dagPool.filter { it.value.precursorCount == 0 }.map { it.value }.toList()
}

fun PyPIResult.getLatestVersion() : Pair<String, List<PyPIResultRelease>>{
    var retVersion = "0.0.0"
    var retInfo = listOf(PyPIResultRelease( LocalDateTime.now(), null))

    for ((version, info) in this.releases) {
        if (Version(version) > Version(retVersion)) {
            retVersion = version
            retInfo = info
        }
    }

    return retVersion to retInfo
}

fun PyPIResult.getDependenciesSet() =
    (this.info.requiresDist ?: listOf()).filter { !it.contains(";") }
        .map { it.split(" ").first() }.toSet()

fun List<DagNode>.print(layer: Int = 0, required: MutableSet<String> = mutableSetOf()): String {
    val ret = StringBuilder()

    for (node in this) {
        if (layer == 0) {
            ret.append(node.name)
            ret.append("\n")
        }
        node.successorList.forEach {
            ret.append("|  " * layer)
            ret.append("+- ")
            if (required.contains(it.name)) {
                ret.append("*")
            }
            required.add(it.name)
            ret.append(it.name)
            ret.append("\n")

            ret.append(it.successorList.print(layer + 1, required))
        }
    }

    return ret.toString()
}

operator fun String.times(times: Int): String {
    val builder = StringBuilder()
    for (i in 0 until times) {
        builder.append(this)
    }
    return builder.toString()
}

operator fun Char.times(times: Int): String {
    val builder = StringBuilder()
    for (i in 0 until times) {
        builder.append(this)
    }
    return builder.toString()
}
