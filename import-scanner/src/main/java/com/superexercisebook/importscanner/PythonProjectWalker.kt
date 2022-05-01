package com.superexercisebook.importscanner

import com.superexercisebook.importscanner.parser.PythonImportVisitor
import com.superexercisebook.importscanner.parser.PythonLexer
import com.superexercisebook.importscanner.parser.PythonParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import java.io.File

object PythonProjectWalker {
    fun scanDirectory(s: String, notResolvedImport: (Set<String>) -> Unit = {}) =
        scanDirectory(File(s), File(s), notResolvedImport)

    fun scanDirectory(s: File, projectDirectory: File = File("."), notResolvedImport: (Set<String>) -> Unit = {}) {
        s.walk().forEach {
            if (s == it) return@forEach

            if (it.isFile && it.extension == "py") {
                scanFile(it, projectDirectory, notResolvedImport)
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
}