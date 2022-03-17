package com.superexercisebook.importscanner.parser

internal fun PythonParser.Dotted_nameContext.toNamePath(): List<String>
        = (this.dotted_name()?.toNamePath() ?: listOf()) + this.name().text
