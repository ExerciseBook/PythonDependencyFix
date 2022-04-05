package com.superexercisebook.importscanner.parser

/**
 * Flatten left recursion
 */
internal fun PythonParser.Dotted_nameContext.toNamePath(): List<String>
        = (this.dotted_name()?.toNamePath() ?: listOf()) + this.name().text
