package com.kasukusakura.yggdrasilgateway.api.codesnippet.javac

import com.kasukusakura.yggdrasilgateway.api.codesnippet.CodeSnippetClassLoader
import javax.tools.*

public class CodeSnippetFileManager(
    delegate: StandardJavaFileManager,
) : ForwardingJavaFileManager<StandardJavaFileManager>(
    delegate
) {
    private val codes = mutableMapOf<String, CodeSnippetCompiledCodeObject>()

    override fun getJavaFileForOutput(
        location: JavaFileManager.Location?,
        className: String?,
        kind: JavaFileObject.Kind?,
        sibling: FileObject?
    ): JavaFileObject {
        return CodeSnippetCompiledCodeObject(className ?: error("Missing className"))
            .also { codes[className] = it }
    }

    override fun toString(): String {
        return codes.keys.toString()
    }

    public fun getCompiledClasses(): Map<String, ByteArray> {
        return codes.mapValues { it.value.code.toByteArray() }
    }

    public fun getCompiledClassnames(): Set<String> = codes.keys

    public fun load(parent: ClassLoader, moduleName: String): ClassLoader {
        return CodeSnippetClassLoader(parent, moduleName, getCompiledClasses())
    }

    internal fun load_(parent: ClassLoader, moduleName: String) =
        CodeSnippetClassLoader(parent, moduleName, getCompiledClasses())
}