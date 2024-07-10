package com.kasukusakura.yggdrasilgateway.core.module.codesnippets

public class CodeSnippet<T>
internal constructor(
    public val samInterface: Class<T>,
    public val defaultInstance: T,
    public val codeSnippetId: String,
    public val codeSnippetName: String,
    public val codeDefaultCode: String,
) {
    internal var loadedSnippet: T? = null
    internal var customCode: String = ""

    public fun getCodeSnippet(): T {
        return loadedSnippet ?: defaultInstance
    }
}