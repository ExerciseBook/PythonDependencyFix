package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import java.io.File
import java.util.*
import kotlin.collections.HashSet

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.size) {
            1, 2 -> {
                val notResolvedImports = HashSet<String>()
                scanDirectory(args[0]) {
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
                        val pending = (i.value.info.requires_dist ?: listOf()).filter { !it.contains(";") }
                            .map { it.split(" ").first() }.toSet()

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

                println("Failed: $failed")
                println("Dependencies: ${dependencies.map{it.key + "==" + it.value.info.version}}")

                if (args.size == 2) {
                    File(args[1]).printWriter().use { c ->
                        for (item in foundInPypi) {
                            c.println(item)
                        }
                    }
                }
            }
            else -> help()
        }
    }

    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                isLenient = true
                ignoreUnknownKeys = true
            })

        }
    }

    val pypiPackageSpecification = mapOf(
        "attr" to "attrs",
        "skimage" to "scikit-image",
        "sklearn" to "scikit-learn",
        "cv2" to "opencv-python",
        "OpenSSL" to "pyOpenSSL",
    )

    private suspend fun findInPypi(item: String): Result<PyPIResult> =
        pypiPackageSpecification.getOrDefault(item, item).let {
            try {
                val result: PyPIResult = httpClient.get("https://pypi.org/pypi/$it/json")
                if (result.isAcceptable()) {
                    Result.success(result)
                } else {
                    Result.failure(IllegalStateException("Not acceptable"))
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
