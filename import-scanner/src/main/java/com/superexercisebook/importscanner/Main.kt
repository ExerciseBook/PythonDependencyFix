package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import java.io.File

class Main

fun main(args: Array<String>) {
    when (args.size) {
        1 -> scanDirectory(args[0])
        else -> test()
    }
}

fun scanDirectory(s: String) = scanDirectory(File(s))

fun scanDirectory(s: File) {
    s.walk().forEach {
        if (s == it) return@forEach

        if (it.isFile && it.extension == "py") {
            scanFile(it)
        } else if (it.isDirectory) {
            scanDirectory(it)
        }
    }
}

fun scanFile(file: File) {
    println(file.toString())
    val input = CharStreams.fromFileName(file.toString())
    scanCharStream(input)
}

fun scanCharStream(stream: CharStream) {
    val eval = PythonImportVisitor()
    val lexer = PythonLexer(stream)
    val tokens = CommonTokenStream(lexer)
    val parser = PythonParser(tokens)
    val tree: ParseTree = parser.file_input()
    eval.visit(tree)
}

fun test() {
    val input: CharStream
    input = CharStreams.fromString("""import A
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
    """.trimMargin())
    scanCharStream(input)
}
