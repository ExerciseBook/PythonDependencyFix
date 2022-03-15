package com.superexercisebook.importscanner.parser

import com.superexercisebook.importscanner.parser.PythonParser.From_stmtContext
import com.superexercisebook.importscanner.parser.PythonParser.Import_stmtContext

open class PythonImportVisitor : PythonParserBaseVisitor<Unit>() {
    override fun visitImport_stmt(ctx: Import_stmtContext?): Unit {
        if (ctx == null) {
            return
        }

        if (ctx.depth() > 4) {
            return
        }

        println("${ctx.depth()}:${ctx.dotted_as_names().text}")

        return visitChildren(ctx)
    }

    override fun visitFrom_stmt(ctx: From_stmtContext?): Unit {
        if (ctx == null) {
            return
        }

        if (ctx.depth() > 4) {
            return
        }

        println("${ctx.depth()}:${ctx.dotted_name().text}")

        return visitChildren(ctx)
    }
}