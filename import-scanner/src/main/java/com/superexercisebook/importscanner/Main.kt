package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class Main

fun main() {
    val eval = PythonImportVisitor()

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
    val lexer = PythonLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = PythonParser(tokens)
    val tree: ParseTree = parser.file_input()

    eval.visit(tree)
}
