package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
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
            }
            else -> test()
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
