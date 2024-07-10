package com.kasukusakura.yggdrasilgateway.api.codesnippet.javac

import org.intellij.lang.annotations.Language
import java.io.*
import java.net.URI
import java.net.URLEncoder
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject

public class CodeSnippetSourceCodeObject(
    private val fileName: String,
    @Language("java") private val code: String,
) : JavaFileObject {
    override fun toUri(): URI = URI.create("snippet://source/${URLEncoder.encode(fileName, Charsets.UTF_8)}")
    override fun getName(): String = fileName

    override fun openInputStream(): InputStream = code.byteInputStream()

    override fun openOutputStream(): OutputStream = throw IOException("Readonly file")

    override fun openReader(ignoreEncodingErrors: Boolean): Reader = code.reader()

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code

    override fun openWriter(): Writer = throw IOException("Readonly file")

    override fun getLastModified(): Long = 0

    override fun delete(): Boolean = false

    override fun getKind(): JavaFileObject.Kind = JavaFileObject.Kind.SOURCE

    override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean {
        return true
    }

    override fun getNestingKind(): NestingKind = NestingKind.TOP_LEVEL

    override fun getAccessLevel(): Modifier = Modifier.PUBLIC
}