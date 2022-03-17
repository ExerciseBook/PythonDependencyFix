package com.superexercisebook.importscanner.parser

import com.superexercisebook.importscanner.parser.PythonParser.From_stmtContext
import com.superexercisebook.importscanner.parser.PythonParser.Import_stmtContext
import com.superexercisebook.importscanner.parser.PythonSystemPackage.systemPackage
import java.io.File

open class PythonImportVisitor(private val projectDirectory: File, private val filePath: File) : PythonParserBaseVisitor<Unit>() {

    private val fileDirectory = filePath.parentFile

    val imports = HashSet<String>()
    val notResolvedImport = HashSet<String>()

    override fun visitImport_stmt(ctx: Import_stmtContext?): Unit {
        if (ctx == null) {
            return
        }

        if (ctx.depth() > 4) {
            return
        }

        val dottedAsNames = ctx.dotted_as_names() ?: return
        dottedAsNames.dotted_as_name().forEach {
            resolveDottedName(it.dotted_name())
        }

        return visitChildren(ctx)
    }

    override fun visitFrom_stmt(ctx: From_stmtContext?): Unit {
        if (ctx == null) {
            return
        }

        if (ctx.depth() > 4) {
            return
        }

        val dottedName = ctx.dotted_name() ?: return
        resolveDottedName(dottedName)

        return visitChildren(ctx)
    }

    private fun resolveDottedName(dottedName: PythonParser.Dotted_nameContext?): Boolean {
        if (dottedName == null) {
            return false
        }

        val dottedNamePath = dottedName.toNamePath()
        imports.add(dottedNamePath.joinToString("."))

        if (resolveSystemImport(dottedNamePath)) {
            print("\u001b[30m")
            println("Found system package: $dottedNamePath")
            return true
        }

        if (resolveRelativePath(dottedNamePath)) {
            print("\u001b[30m")
            println("Found relative path: $dottedNamePath")
            return true
        }

        if (resolveProjectPath(dottedNamePath)) {
            print("\u001b[30m")
            println("Found project path: $dottedNamePath")
            return true
        }

        if (resolveSubPath(dottedNamePath)) {
            print("\u001b[30m")
            println("Found step path: $dottedNamePath")
            return true
        }

        print("\u001b[31m")
        println("Could not resolve $dottedNamePath")
        notResolvedImport.add(dottedNamePath.joinToString("."))
        return false
    }

    private fun resolveSystemImport(dottedNamePath: List<String>): Boolean {
        if (dottedNamePath.isEmpty()) return false
        if (systemPackage.contains(dottedNamePath.joinToString("."))) return true
        return false
    }

    private fun resolvePath(dottedName: List<String>, path: File): Boolean {
        when {
            dottedName.isEmpty() -> {
                return false
            }
            dottedName.size == 1 -> {
                val subDirectory = File(path, dottedName[0])
                if (subDirectory.exists()) {
                    return true
                }

                val file = File(path, dottedName[0] + ".py")
                if (file.exists()) {
                    return true
                }
            }
            else -> {
                val subDirectory = File(path, dottedName[0])
                if (subDirectory.exists()) {
                    val subDottedName = dottedName.subList(1, dottedName.size)
                    return resolvePath(subDottedName, subDirectory)
                }
            }
        }
        return false
    }

    private fun resolveRelativePath(dottedName: List<String>): Boolean =
        resolvePath(dottedName, fileDirectory)


    private fun resolveProjectPath(dottedName: List<String>): Boolean =
        resolvePath(dottedName, projectDirectory)

    private fun resolveSubPath(dottedName: List<String>): Boolean {
        if (projectDirectory == fileDirectory) {
            return false
        }

        var subDirectory = fileDirectory.parentFile
        while (subDirectory != projectDirectory) {
            if (resolvePath(dottedName, subDirectory)) {
                return true
            }
            subDirectory = subDirectory.parentFile
        }

        return false
    }
}
