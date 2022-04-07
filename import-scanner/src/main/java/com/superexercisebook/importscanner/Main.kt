package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.PyPIUtils.findInPypi
import com.superexercisebook.importscanner.PythonProjectWalker.scanDirectory
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.collections.set


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        when {
            (args.size == 2 || args.size == 3) && (args[0] == "scan-project") -> {
                // project root
                val projectRoot = args[1]

                // commit time of current commit
                // if no commit time is given, use current time
                val headTime = getProjectHeadTime(projectRoot)

                // get all not resolved imports
                val notResolvedImports = HashSet<String>()
                scanDirectory(projectRoot) {
                    notResolvedImports.addAll(it)
                }
                println("Not resolved imports: $notResolvedImports")

                // extra the first elements from every element in [notResolvedImports]
                // e.g. A.B.C in notResolvedImports, A in guessImports
                val guessImports = notResolvedImports.mapNotNull { it.split(".").firstOrNull() }.toSet()
                println("Guess: $guessImports")

                // fail to find in PyPI, for skipping retry
                val failed = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
                // found in PyPI
                val dependencies = TreeMap<String, PyPIResult>(String.CASE_INSENSITIVE_ORDER)

                // find all dependencies in PyPI
                // package name found in PyPI
                val foundInPypi = HashSet<String>()
                for (item in guessImports) {
                    runBlocking {
                        val result = findInPypi(item)
                        if (result.isSuccess) {
                            val dependency = result.getOrThrow()
                            dependencies[dependency.info.name] = dependency
                            foundInPypi.add(dependency.info.name)
                        } else {
                            println("Failed to find $item in pypi: ${result.exceptionOrNull()}")
                            failed.add(item)
                        }
                    }
                }
                println("Found in pypi: $foundInPypi")

                // find all dependencies in PyPI
                var loop = true
                while (loop) {
                    loop = false
                    for ((_, value) in dependencies) {
                        // get all dependencies of current dependency
                        val pending = value.getDependenciesSet()

                        for (item in pending) {
                            // filter out the dependencies that are already found in PyPI or failed to find in PyPI
                            if (dependencies[item] != null || failed.contains(item)) {
                                continue
                            }

                            // try to search in PyPI
                            val newDependency = runBlocking {
                                findInPypi(item)
                            }

                            // if found, add to dependencies
                            if (newDependency.isSuccess) {
                                val dependency = newDependency.getOrThrow()
                                dependencies[dependency.info.name] = dependency
                                loop = true
                            } else {
                                println("Failed to find $item in pypi: ${newDependency.exceptionOrNull()}")
                                failed.add(item)
                            }
                        }

                        if (loop) {
                            break
                        }
                    }
                }

                // clean dependencies
                // in some cases we could get some strange dependencies like "mail", which did not update for a long time
                val cleanedDependencies = dependencies.getAcceptableVersion(releaseBefore = headTime)

                println("Failed: $failed")
                println("Dependencies: ${dependencies.map { it.key + "==" + it.value.info.version }}")

                // construct a dag by dependencies we found
                // in some cases a program may import package A, and package A depends on package B,
                // but this program will also import B and use it,
                // we could filter out the dependency B, in this step
                val dependenciesDag = cleanedDependencies.toDag()
                println("Dependencies DAG: \r\n${dependenciesDag.print()}")

                println("Clean dependencies: ${
                    cleanedDependencies.map {
                        val c = it.value.getLatestVersion()
                        if (c.isSuccess) {
                            it.key + "<=" + c.getOrThrow().first
                        } else {
                            it.key
                        }
                    }
                }")

                // write to file
                if (args.size == 3) {
                    File(args[2], "scanned_import.txt").printWriter().use { c ->
                        for (item in foundInPypi) {
                            c.println(item)
                        }
                    }

                    File(args[2], "scanned_dependencies_version.json").printWriter().use { c ->
                        jsonMapper.writeValue(c, cleanedDependencies)
                    }

                    run {
                        val v = File(args[2], "scanned_dependencies_requirements_with_version.txt").printWriter()
                        val nv = File(args[2], "scanned_dependencies_requirements_without_version.txt").printWriter()

                        dependenciesDag.forEach {
                            v.println(it.name + cleanedDependencies[it.name]!!.getLatestVersion().let { c ->
                                if (c.isSuccess) {
                                    "<=" + c.getOrThrow().first
                                } else {
                                    ""
                                }
                            })

                            nv.println(it.name)
                        }

                        v.close()
                        nv.close()
                    }
                }
            }
            (args.size == 3 || args.size == 4) && (args[0] == "check-package") -> {
                // project root
                val projectRoot = args[1]

                // commit time of current commit
                // if no commit time is given, use current time
                val headTime = getProjectHeadTime(projectRoot)

                val packageName = args[2]
                val newDependency = runBlocking {
                    findInPypi(packageName)
                }

                var selectedVersion: PyPIResult? = null
                var dependency: PyPIResult? = null
                if (newDependency.isSuccess) {
                    dependency = newDependency.getOrThrow()
                    selectedVersion = dependency.getAcceptableVersion(releaseBefore = headTime)
                } else {
                    println("Failed to find $packageName in pypi: ${newDependency.exceptionOrNull()}")
                }

                val latestVersion = selectedVersion?.getLatestVersion() ?: Result.failure(Exception("No version found"))
                if (selectedVersion != null && latestVersion.isSuccess) {
                    println("Selected version: ${latestVersion.getOrThrow().first}")
                } else {
                    println("No version found")
                }

                if (args.size == 3) {
                    if (selectedVersion != null) {
                        File(args[2], "suggest_dependency.json").printWriter().use { c ->
                            jsonMapper.writeValue(c, mapOf(
                                "name" to packageName,
                                "meta" to dependency,
                                "selectedVersion" to selectedVersion,
                                "latestVersion" to latestVersion.getOrNull()?.first
                            ))
                        }

                        File(args[2], "suggest_dependency_with_version.txt").printWriter().use { c ->
                            if ( latestVersion.isSuccess) {
                                c.println("$packageName<=${latestVersion.getOrThrow().first}")
                            } else {
                                c.println(packageName)
                            }
                        }

                        File(args[2], "suggest_dependency_without_version.txt").printWriter().use { c ->
                            c.println(packageName)
                        }
                    }
                }
            }
            else -> help()
        }
    }

    private fun getProjectHeadTime(projectRoot: String): LocalDateTime =
        try {
            Git.open(File(projectRoot)).use { git ->
                val repo = git.repository
                val commitId = repo.resolve(Constants.HEAD)
                println("Current Head: ${commitId.name}")

                val commit = repo.parseCommit(commitId)
                println("Commit Author: ${commit.authorIdent.name}")
                val time = Date.from(commit.authorIdent.whenAsInstant)
                println("Commit Time: ${df2.format(time)}")
                time
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Date()
        }.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()


    fun help() {
        println("""
            Usage:
            java -jar <jar> <project directory> <output directory>
        """.trimIndent())
    }
}
