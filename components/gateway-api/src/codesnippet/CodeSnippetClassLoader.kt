package com.kasukusakura.yggdrasilgateway.api.codesnippet

internal class CodeSnippetClassLoader(
    parent: ClassLoader,
    moduleName: String,
    private val code: Map<String, ByteArray>,
) : ClassLoader(moduleName, parent) {
    @JvmField
    internal var additional: Map<String, ByteArray> = emptyMap()

    override fun findClass(name: String): Class<*> {
        (additional[name] ?: code[name])?.let { bc ->
            return defineClass(name, bc, 0, bc.size)
        }

        throw ClassNotFoundException(name)
    }
}