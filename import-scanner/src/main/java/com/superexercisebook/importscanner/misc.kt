package com.superexercisebook.importscanner

import io.github.g00fy2.versioncompare.Version
import java.time.LocalDateTime
import java.util.*

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
        if (release.isAcceptable(pythonVersion, LocalDateTime.now()).isSuccess) {
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

enum class AcceptableState {
    PYTHON_VERSION_COMPATIBLE_MAJOR,
    PYTHON_VERSION_COMPATIBLE_MINOR,
    PYTHON_SOURCE,
    RELEASE_BEFORE_CONSTRAINT,
}

/**
 * Check release (version) is acceptable or not
 */
fun PyPIResultRelease.isAcceptable(
    constrainPythonVersionStr: String,
    releaseBefore: LocalDateTime,
): Result<EnumSet<AcceptableState>> {
    val constrainPythonVersionStep = Version(constrainPythonVersionStr)

    val constrainPythonVersion = constrainPythonVersionStep.let { "${it.major}.${it.minor}" }
    val constrainPythonVersionNoDots = constrainPythonVersionStep.let { "${it.major}${it.minor}" }
    val constrainPythonVersionMajorVersion = constrainPythonVersionStep.let { "${it.major}" }

    val acceptableVersionMajor = listOf(
        constrainPythonVersionMajorVersion,
        "py$constrainPythonVersionMajorVersion",
        "cp$constrainPythonVersionMajorVersion",
        "source",
    )

    val acceptableVersionMinor = listOf(
        constrainPythonVersion,
        constrainPythonVersionNoDots,
        "py$constrainPythonVersion",
        "py$constrainPythonVersionNoDots",
        "cp$constrainPythonVersion",
        "cp$constrainPythonVersionNoDots",
    )

    val acceptableVersionSourceRelease = listOf(
        "source",
    )

    val ret = EnumSet.noneOf(AcceptableState::class.java)

    val packagePythonVersion = this.pythonVersion
    if (this.uploadTime < releaseBefore) {
        ret.add(AcceptableState.RELEASE_BEFORE_CONSTRAINT)
    }
    if (packagePythonVersion != null) {
        if (this.uploadTime < LocalDateTime.of(2017, 1, 1, 0, 0, 0)) {
            return Result.failure(IllegalStateException())
        }

        packagePythonVersion.split(".").forEach { version ->
            if (acceptableVersionMajor.any { it.equals(version, ignoreCase = true) }) {
                ret.add(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR)
            }

            if (acceptableVersionMinor.any { it.equals(version, ignoreCase = true) }) {
                ret.add(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR)
            }

            if (acceptableVersionSourceRelease.any { it.equals(version, ignoreCase = true) }) {
                ret.add(AcceptableState.PYTHON_SOURCE)
            }
        }
    }

    if (ret.isNotEmpty()) {
        return Result.success(ret)
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
                val ret = mutableListOf<Pair<PyPIResultRelease, EnumSet<AcceptableState>>>()

                for (pypiReleaseItem in pypiReleases) {
                    val t = pypiReleaseItem.isAcceptable(constrainPythonVersionStr = pythonVersion,
                            releaseBefore = releaseBefore)
                    if (t.isSuccess) {
                        ret.add(pypiReleaseItem to t.getOrNull()!!)
                    }
                }

                ret.sortWith { a, b ->
                    if (a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR) && !b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR)) {
                        -1
                    } else if (!a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR) && b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR)) {
                        1
                    } else if (a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR) && b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MINOR)) {
                        a.first.uploadTime.compareTo(b.first.uploadTime)
                    } else
                    if (a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR) && !b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR)) {
                        -1
                    } else if (!a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR) && b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR)) {
                        1
                    } else if (a.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR) && b.second.contains(AcceptableState.PYTHON_VERSION_COMPATIBLE_MAJOR)) {
                        a.first.uploadTime.compareTo(b.first.uploadTime)
                    } else  {
                        a.first.uploadTime.compareTo(b.first.uploadTime)
                    }
                 }

                releaseList.addAll(ret.map { it.first }.toMutableList())
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

    return dagPool.filter { it.value.precursorCount == 0 }.map { it.value }.toMutableList()
}


fun Map<String, PyPIResult>.getBuildTimeDependencies(): List<PyPIResult> {
    val ret = mutableListOf<PyPIResult>()

    if (this.any { it.value.info.name.equals("pyarrow", ignoreCase = true) }) {
        val numpy = this["numpy"]
        if (numpy != null) {
            ret.add(0, numpy)
        }
    }

    if (this.any { it.value.info.name.equals("numpy", ignoreCase = true) }) {
        val cython = this["Cython"]
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
