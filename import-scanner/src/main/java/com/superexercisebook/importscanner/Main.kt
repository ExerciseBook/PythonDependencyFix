package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.get
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
            1 -> {
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
                        if (foundInPypi(item)) {
                            foundInPypi.add(item)
                        }
                    }
                }
                println("Found in pypi: $foundInPypi")
            }
            else -> test()
        }
    }

    private suspend fun foundInPypi(item: String): Boolean {
        return try {
            val client = HttpClient(CIO)
            val httpStatement: HttpStatement = client.get("https://pypi.org/pypi/$item/json")
            val response = httpStatement.execute()
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
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
