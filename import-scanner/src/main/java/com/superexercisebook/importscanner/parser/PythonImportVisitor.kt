package com.superexercisebook.importscanner.parser

import com.superexercisebook.importscanner.parser.PythonParser.From_stmtContext
import com.superexercisebook.importscanner.parser.PythonParser.Import_stmtContext
import java.io.File

open class PythonImportVisitor(val projectDirectory: File, val filePath: File) : PythonParserBaseVisitor<Unit>() {

    val fileDirectory = filePath.parentFile

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

        if (resolveSystemImport(dottedNamePath)) {
            println("Found system package: $dottedNamePath")
            return true
        }

        if (resolveRelativePath(dottedNamePath)) {
            println("Found relative path: $dottedNamePath")
            return true
        }

        println("Could not resolve $dottedNamePath")
        return false
    }

    private val systemPackage = HashSet<String>()
    init {
        val t = listOf("__future__", "__main__", "_thread", "abc", "aifc", "argparse", "array", "ast", "asynchat", "asyncio", "asyncore", "atexit", "audioop", "base64", "bdb", "binascii", "binhex", "bisect", "__future__", "__main__", "_thread", "abc", "aifc", "argparse", "array", "ast", "asynchat", "asyncio", "asyncore", "atexit", "audioop", "base64", "bdb", "binascii", "binhex", "bisect", "builtins", "bz2", "calendar", "cgi", "cgitb", "chunk", "cmath", "cmd", "code", "codecs", "codeop", "collections", "collections.abc", "colorsys", "compileall", "concurrent", "concurrent.futures", "configparser", "contextlib", "contextvars", "copy", "copyreg", "cProfile", "crypt ", "csv", "ctypes", "curses ", "curses.ascii", "curses.panel", "curses.textpad", "dataclasses", "datetime", "dbm", "dbm.dumb", "dbm.gnu ", "dbm.ndbm ", "decimal", "difflib", "dis", "distutils", "distutils.archive_util", "distutils.bcppcompiler", "distutils.ccompiler", "distutils.cmd", "distutils.command", "distutils.command.bdist", "distutils.command.bdist_dumb", "distutils.command.bdist_msi", "distutils.command.bdist_packager", "distutils.command.bdist_rpm", "distutils.command.build", "distutils.command.build_clib", "distutils.command.build_ext", "distutils.command.build_py", "distutils.command.build_scripts", "distutils.command.check", "distutils.command.clean", "distutils.command.config", "distutils.command.install", "distutils.command.install_data", "distutils.command.install_headers", "distutils.command.install_lib", "distutils.command.install_scripts", "distutils.command.register", "distutils.command.sdist", "distutils.core", "distutils.cygwinccompiler", "distutils.debug", "distutils.dep_util", "distutils.dir_util", "distutils.dist", "distutils.errors", "distutils.extension", "distutils.fancy_getopt", "distutils.file_util", "distutils.filelist", "distutils.log", "distutils.msvccompiler", "distutils.spawn", "distutils.sysconfig", "distutils.text_file", "distutils.unixccompiler", "distutils.util", "distutils.version", "doctest", "email", "email.charset", "email.contentmanager", "email.encoders", "email.errors", "email.generator", "email.header", "email.headerregistry", "email.iterators", "email.message", "email.mime", "email.parser", "email.policy", "email.utils", "encodings", "encodings.idna", "encodings.mbcs", "encodings.utf_8_sig", "ensurepip", "enum", "errno", "faulthandler", "fcntl ", "filecmp", "fileinput", "fnmatch", "fractions", "ftplib", "functools", "gc", "getopt", "getpass", "gettext", "glob", "graphlib", "grp ", "gzip", "hashlib", "heapq", "hmac", "html", "html.entities", "html.parser", "http", "http.client", "http.cookiejar", "http.cookies", "http.server", "imaplib", "imghdr", "imp", "importlib", "importlib.abc", "importlib.machinery", "importlib.metadata", "importlib.resources", "importlib.util", "inspect", "io", "ipaddress", "itertools", "json", "json.tool", "keyword", "lib2to3", "linecache", "locale", "logging", "logging.config", "logging.handlers", "lzma", "mailbox", "mailcap", "marshal", "math", "mimetypes", "mmap", "modulefinder", "msilib ", "msvcrt ", "multiprocessing", "multiprocessing.connection", "multiprocessing.dummy", "multiprocessing.managers", "multiprocessing.pool", "multiprocessing.shared_memory", "multiprocessing.sharedctypes", "netrc", "nis", "nntplib", "numbers", "operator", "optparse", "os", "os.path", "ossaudiodev", "pathlib", "pdb", "pickle", "pickletools", "pipes ", "pkgutil", "platform", "plistlib", "poplib", "posix ", "pprint", "profile", "pstats", "pty ", "pwd ", "py_compile", "pyclbr", "pydoc", "queue", "quopri", "random", "re", "readline ", "reprlib", "resource ", "rlcompleter", "runpy", "sched", "secrets", "select", "selectors", "shelve", "shlex", "shutil", "signal", "site", "smtpd", "smtplib", "sndhdr", "socket", "socketserver", "spwd ", "sqlite3", "ssl", "stat", "statistics", "string", "stringprep", "struct", "subprocess", "sunau", "symtable", "sys", "sysconfig", "syslog ", "tabnanny", "tarfile", "telnetlib", "tempfile", "termios ", "test", "test.support", "test.support.bytecode_helper", "test.support.import_helper", "test.support.os_helper", "test.support.script_helper", "test.support.socket_helper", "test.support.threading_helper", "test.support.warnings_helper", "textwrap", "threading", "time", "timeit", "tkinter", "tkinter.colorchooser ", "tkinter.commondialog ", "tkinter.dnd ", "tkinter.filedialog ", "tkinter.font ", "tkinter.messagebox ", "tkinter.scrolledtext ", "tkinter.simpledialog ", "tkinter.tix", "tkinter.ttk", "token", "tokenize", "trace", "traceback", "tracemalloc", "tty ", "turtle", "turtledemo", "types", "typing", "unicodedata", "unittest", "unittest.mock", "urllib", "urllib.error", "urllib.parse", "urllib.request", "urllib.response", "urllib.robotparser", "uu", "uuid", "venv", "warnings", "wave", "weakref", "webbrowser", "winreg ", "winsound ", "wsgiref", "wsgiref.handlers", "wsgiref.headers", "wsgiref.simple_server", "wsgiref.util", "wsgiref.validate", "xdrlib", "xml", "xml.dom", "xml.dom.minidom", "xml.dom.pulldom", "xml.etree.ElementTree", "xml.parsers.expat", "xml.parsers.expat.errors", "xml.parsers.expat.model", "xml.sax", "xml.sax.handler", "xml.sax.saxutils", "xml.sax.xmlreader", "xmlrpc", "xmlrpc.client", "xmlrpc.server", "zipapp", "zipfile", "zipimport", "zlib", "zoneinfo",)
        t.forEach { systemPackage.add(it) }
    }

    private fun resolveSystemImport(dottedNamePath: List<String>): Boolean {
        if (dottedNamePath.isEmpty()) return false
        if (systemPackage.contains(dottedNamePath.joinToString("."))) return true
        return false
    }

    private fun resolveRelativePath(dottedName: List<String>): Boolean {
        when {
            dottedName.isEmpty() -> {
                return false
            }
            dottedName.size == 1 -> {
                val file = File(fileDirectory, dottedName[0])
                if (file.exists()) {
                    return true
                }
            }
            dottedName.size > 1 -> {
                val file = File(fileDirectory, dottedName[0])
                if (file.exists()) {
                    val subDottedName = dottedName.subList(1, dottedName.size)
                    return resolveRelativePath(subDottedName)
                } else {
                    return false
                }
            }
        }
        return false
    }
}

private fun PythonParser.Dotted_nameContext.toNamePath(): List<String> {
    return (this.dotted_name()?.toNamePath() ?: listOf()) + this.name().text
}
