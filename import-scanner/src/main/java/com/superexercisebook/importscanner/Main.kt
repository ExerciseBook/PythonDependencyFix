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

                val foundInPypi = HashSet<String>()
                for (item in guessImports) {
                    runBlocking {
                        val result = foundInPypi(item)
                        if (result.isSuccess) {
                            foundInPypi.add(result.getOrThrow())
                        }
                    }
                }
                println("Found in pypi: $foundInPypi")

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

    private suspend fun foundInPypi(item: String): Result<String> =
        when (item) {
            "attr" -> Result.success("attrs")
            "skimage" -> Result.success("skicit-image")
            "sklearn" -> Result.success("scikit-learn")
            "cv2" -> Result.success("opencv-python")
            "OpenSSL" -> Result.success("pyOpenSSL")
            else -> try {
                val result: PyPIResult = httpClient.get("https://pypi.org/pypi/$item/json")
                if (result.isAcceptable()) {
                    Result.success(item)
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

    fun test() {
        val input: CharStream
        input = CharStreams.fromString("""
            |import A
            |from B import C
            |from D import E, F
            |
            |if 1 == 2:
            |    import G
            |else:
            |    import H
            |
            |import I
            |from J import K, L, M, N
            |
            |import O.P.Q
            |from R.S.T import U, V, W
            |
        """.trimMargin())
        scanCharStream(input)
    }
}
