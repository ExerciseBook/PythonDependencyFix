package com.superexercisebook.importscanner

import io.github.g00fy2.versioncompare.Version
import java.time.LocalDateTime

/**
 * Check package is acceptable or not
 */
fun PyPIResult.isAcceptable(
    pythonVersion: String,
    releaseBefore: LocalDateTime,
): Result<Unit> {
    val latestVersion = this.info.version

    val latestRelease = this.releases[latestVersion]
        ?: return Result.failure(IllegalStateException("No release found for package ${this.info.name} version $latestVersion"))

    latestRelease.forEach { release ->
        if (release.isAcceptable(pythonVersion, releaseBefore).isSuccess) {
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

/**
 * Check release (version) is acceptable or not
 */
fun PyPIResultRelease.isAcceptable(
    constrainPythonVersionStr: String,
    releaseBefore: LocalDateTime,
): Result<Unit> {
    val constrainPythonVersionStep = Version(constrainPythonVersionStr)

    val constrainPythonVersion = constrainPythonVersionStep.let { "${it.major}.${it.minor}" }
    val constrainPythonVersionNoDots = constrainPythonVersionStep.let { "${it.major}${it.minor}" }
    val constrainPythonVersionMajorVersion = constrainPythonVersionStep.let { "${it.major}" }

    val acceptableVersion = listOf(
        constrainPythonVersion,
        constrainPythonVersionNoDots,
        constrainPythonVersionMajorVersion,
        "py$constrainPythonVersion",
        "py$constrainPythonVersionNoDots",
        "py$constrainPythonVersionMajorVersion",
        "cp$constrainPythonVersion",
        "cp$constrainPythonVersionNoDots",
        "cp$constrainPythonVersionMajorVersion",
        "source",
    )

    val packagePythonVersion = this.pythonVersion
    if (this.uploadTime > releaseBefore) {
        return Result.failure(IllegalStateException())
    }
    if (packagePythonVersion != null) {
        if (this.uploadTime < LocalDateTime.of(2017, 1, 1, 0, 0, 0)) {
            return Result.failure(IllegalStateException())
        }

        packagePythonVersion.split(".").forEach { version ->
            if (acceptableVersion.any { it.equals(version, ignoreCase = true) }) {
                return Result.success(Unit)
            }
        }
    }
    return Result.failure(IllegalStateException())
}

/**
 * Get acceptable version of package
 */
fun Map<String, PyPIResult>.getAcceptableVersion(pythonVersion: String, releaseBefore: LocalDateTime): Map<String, PyPIResult> {
    val ret = mutableMapOf<String, PyPIResult>()

    for ((name, pypiMeta) in this) {
        ret[name] = pypiMeta.getAcceptableVersion(pythonVersion, releaseBefore)
    }

    return ret
}

fun PyPIResult.getAcceptableVersion(pythonVersion: String, releaseBefore: LocalDateTime): PyPIResult =
    PyPIResult(this.info, mutableMapOf<String, List<PyPIResultRelease>>().also { releaseMap ->
        for ((pypiVersion, pypiReleases) in this.releases) {
            if (pypiReleases.isEmpty()) {
                continue
            }

            mutableListOf<PyPIResultRelease>().also { releaseList ->
                for (pypiReleaseItem in pypiReleases) {
                    if (pypiReleaseItem.isAcceptable(constrainPythonVersionStr = pythonVersion,
                            releaseBefore = releaseBefore).isSuccess
                    ) {
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


/**
 * Construct a dag.
 * Return a list representing every node with no precursor.
 */
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

    val ret = dagPool.filter { it.value.precursorCount == 0 }.map { it.value }.toMutableList()

    if (ret.any { it.name.equals("pyarrow", ignoreCase = true) }) {
        val numpy = dagPool.values.firstOrNull { it.name.equals("numpy", ignoreCase = true) }
        if (numpy != null) {
            ret.add(0, numpy)
        }

        val cython = dagPool.values.firstOrNull { it.name.equals("cython", ignoreCase = true) }
        if (cython != null) {
            ret.add(0, cython)
        }
    }

    return ret
}

/**
 * Get the latest version of package
 */
fun PyPIResult.getLatestVersion(): Result<Pair<String, List<PyPIResultRelease>>> {
    var found = false
    var retVersion = "0.0.0"
    var retInfo = listOf(PyPIResultRelease(LocalDateTime.now(), null))

    for ((version, info) in this.releases) {
        if (Version(version) > Version(retVersion)) {
            retVersion = version
            retInfo = info
            found = true
        }
    }

    return if (found) Result.success(retVersion to retInfo) else Result.failure(IllegalStateException())
}

/**
 * Get dependencies of a package. Filtering out the conditional dependencies.
 */
fun PyPIResult.getDependenciesSet(): Set<String> {
    val ret = (this.info.requiresDist ?: listOf()).filter { !it.contains(";") }
        .map { it.split(" ").first() }.toMutableSet()

    // https://github.com/apache/arrow/blob/master/python/setup.py#L40
    if (this.info.name.equals("pyarrow", ignoreCase = true)) {
        ret.add("Cython")
        ret.add("numpy")
    }

    return ret
}

/**
 * Write the dag.
 */
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
