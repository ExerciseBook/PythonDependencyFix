package com.superexercisebook.importscanner

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*


object Main {

    val df2: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.size) {
            1, 2 -> {
                val projectRoot = args[0]

                val headTime = try {
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

                val notResolvedImports = HashSet<String>()
                scanDirectory(projectRoot) {
                    notResolvedImports.addAll(it)
                }
                println("Not resolved imports: $notResolvedImports")
                val guessImports = notResolvedImports.mapNotNull { it.split(".").firstOrNull() }.toSet()
                println("Guess: $guessImports")

                val failed = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
                val dependencies = TreeMap<String, PyPIResult>(String.CASE_INSENSITIVE_ORDER)

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

                var loop = true
                while (loop) {
                    loop = false
                    for (i in dependencies) {
                        val pending = i.value.getDependenciesSet()

                        for (item in pending) {
                            if (dependencies[item] != null || failed.contains(item)) {
                                continue
                            }

                            val newDependency = runBlocking {
                                findInPypi(item)
                            }
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

                val cleanedDependencies = dependencies.getAcceptableVersion(releaseBefore = headTime)

                println("Failed: $failed")
                println("Dependencies: ${dependencies.map { it.key + "==" + it.value.info.version }}")

                val dependenciesDag = cleanedDependencies.toDag()
                println("Dependencies DAG: \r\n${dependenciesDag.print()}")

                println("Clean dependencies: ${
                    cleanedDependencies.map {
                        it.value.getLatestVersion().let { c ->
                            if (c.isSuccess) {
                                it.key + "<=" + c.getOrThrow().first
                            } else {
                                it.key
                            }
                        }
                    }
                }")

                if (args.size == 2) {
                    File(args[1], "scanned_import.txt").printWriter().use { c ->
                        for (item in foundInPypi) {
                            c.println(item)
                        }
                    }

                    File(args[1], "scanned_dependencies_version.json").printWriter().use { c ->
                        jsonMapper.writeValue(c, cleanedDependencies)
                    }

                    run {
                        val v = File(args[1], "scanned_dependencies_requirements_with_version.txt").printWriter()
                        val nv = File(args[1], "scanned_dependencies_requirements_without_version.txt").printWriter()

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
            else -> help()
        }
    }

    val jsonMapper = jsonMapper {
        addModule(ParameterNamesModule())
        addModule(Jdk8Module())
        addModule(JavaTimeModule())
        addModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer(jsonMapper)
        }
    }

    val pypiPackageSpecification = mapOf(
        "attr" to "attrs",
        "skimage" to "scikit-image",
        "sklearn" to "scikit-learn",
        "cv2" to "opencv-python",
        "OpenSSL" to "pyOpenSSL",
        "pydispatch" to "PyDispatcher",
    )

    private suspend fun findInPypi(item: String): Result<PyPIResult> =
        pypiPackageSpecification.getOrDefault(item, item).let {
            try {
                val result: PyPIResult = httpClient.get("https://pypi.org/pypi/$it/json")
                val acceptable = result.isAcceptable()
                if (acceptable.isSuccess) {
                    Result.success(result)
                } else {
                    Result.failure(acceptable.exceptionOrNull()!!)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun scanDirectory(s: String, notResolvedImport: (Set<String>) -> Unit = {}) =
        scanDirectory(File(s), File(s), notResolvedImport)

    fun scanDirectory(s: File, projectDirectory: File = File("."), notResolvedImport: (Set<String>) -> Unit = {}) {
        s.walk().forEach {
            if (s == it) return@forEach

            if (it.isFile && it.extension == "py") {
                scanFile(it, projectDirectory, notResolvedImport)
            } else if (it.isDirectory) {
                scanDirectory(it, projectDirectory, notResolvedImport)
            }
        }
    }

    fun scanFile(file: File, projectDirectory: File = File("."), notResolvedImport: (Set<String>) -> Unit = {}) {
        println(file.toString())
        val input = CharStreams.fromFileName(file.toString())
        scanCharStream(input, projectDirectory, file, notResolvedImport)
    }

    fun scanCharStream(
        stream: CharStream,
        projectDirectory: File = File("."),
        filePath: File = File("a.py"),
        notResolvedImport: (Set<String>) -> Unit = {},
    ) {
        val eval = PythonImportVisitor(projectDirectory, filePath)
        val lexer = PythonLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = PythonParser(tokens)
        val tree: ParseTree = parser.file_input()
        eval.visit(tree)
        notResolvedImport(eval.notResolvedImport)
    }

    fun help() {
        println("""
            Usage:
            java -jar <jar> <directory>
        """.trimIndent())
    }
}
