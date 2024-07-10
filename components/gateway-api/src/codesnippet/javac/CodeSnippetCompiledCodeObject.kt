package com.kasukusakura.yggdrasilgateway.api.codesnippet.javac

import com.kasukusakura.yggdrasilgateway.api.util.AccessibleByteArrayOutputStream
import java.io.*
import java.net.URI
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject

internal class CodeSnippetCompiledCodeObject(
    val className: String
) : JavaFileObject {
    val code = AccessibleByteArrayOutputStream()

    override fun toUri(): URI = URI.create("snippet://target/$className.java")
    override fun getName(): String = "$className.class"
    override fun openOutputStream(): OutputStream = code
    override fun openWriter(): Writer = code.writer()


    override fun openInputStream(): InputStream =
        throw FileNotFoundException("Write only file")

    override fun openReader(ignoreEncodingErrors: Boolean): Reader =
        throw FileNotFoundException("Write only file")

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
        throw FileNotFoundException("Write only file")

    override fun getLastModified(): Long = 0
    override fun delete(): Boolean = false
    override fun getKind(): JavaFileObject.Kind = JavaFileObject.Kind.CLASS

    override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean {
        return simpleName == className && kind == JavaFileObject.Kind.CLASS
    }

    override fun getNestingKind(): NestingKind = NestingKind.LOCAL

    override fun getAccessLevel(): Modifier = Modifier.DEFAULT
}