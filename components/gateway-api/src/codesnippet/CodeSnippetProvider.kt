package com.kasukusakura.yggdrasilgateway.api.codesnippet

import com.kasukusakura.yggdrasilgateway.api.codesnippet.javac.CodeSnippetFileManager
import com.kasukusakura.yggdrasilgateway.api.codesnippet.javac.CodeSnippetSourceCodeObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import java.io.StringWriter
import java.io.Writer
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Modifier
import java.util.*
import javax.tools.ToolProvider

public object CodeSnippetProvider {
    private const val ACCESSOR_CLASS = "com.kasukusakura.yggdrasilgateway.api.codesnippet.accessor.Accessor"

    public suspend fun <T> compileCodeSnippet(
        snippetName: String,
        @Language("java") snippet: String,
        invoker: Class<T>,
        logOutput: Writer? = null,
        options: Collection<String> = listOf(),
        loader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): T {
        val compiler = ToolProvider.getSystemJavaCompiler() ?: error("No Java compiler found")

        val sfm = compiler.getStandardFileManager(null, Locale.ENGLISH, Charsets.UTF_8)
        val cfm = CodeSnippetFileManager(sfm)
        val monitorWriter = StringWriter()

        val compileStatus = withContext(Dispatchers.IO) {
            compiler.getTask(
                logOutput ?: monitorWriter, cfm, null,
                options,
                listOf(),
                listOf(
                    CodeSnippetSourceCodeObject(snippetName, snippet),
                    CodeSnippetSourceCodeObject(
                        "Accessor.java", """
                        package com.kasukusakura.yggdrasilgateway.api.codesnippet.accessor;
                        import java.lang.invoke.MethodHandles;
                        public class Accessor {
                            public static MethodHandles.Lookup access() {
                                return MethodHandles.lookup();
                            }
                        }
                    """.trimIndent()
                    ),
                )
            ).call()
        }

        if (compileStatus != true) {
            error(monitorWriter.toString().let { err ->
                if (err.isBlank()) "Code snippets failed to compile"
                else "Code snippets failed to compile:\n$err"
            })
        }

        val toplevelClasses = cfm.getCompiledClassnames().asSequence()
            .filter { it != ACCESSOR_CLASS }
            .map { it.substringBeforeLast('\$') }
            .toSet()
        when (toplevelClasses.size) {
            0 -> error("Mo class was compiled")
            1 -> {}
            else -> error("To many top-level classes: $toplevelClasses")
        }

        val invokerSam = invoker.methods.asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { Modifier.isAbstract(it.modifiers) }
            .filterNot { it.isBridge }
            .filterNot { it.isSynthetic }
            .first()

        val ccl = cfm.load_(parent = loader, moduleName = snippetName)
        val impl = ccl.loadClass(toplevelClasses.first())

        val implements = impl.methods.asSequence()
            .filter { Modifier.isStatic(it.modifiers) }
            .filter { Modifier.isPublic(it.modifiers) }
            .filterNot { it.isBridge }
            .filterNot { it.isSynthetic }
            .filter { it.returnType == invokerSam.returnType }
            .filter { it.parameterTypes contentEquals invokerSam.parameterTypes }
            .toList()

        val samRawDescription = buildString {
            append('(')
            invokerSam.parameterTypes.joinTo(this, ",") { it.name }
            append(')')
            append(invokerSam.returnType.name)
        }

        if (implements.isEmpty()) {
            error("No suitable implementation found in $impl as $samRawDescription")
        } else if (implements.size != 1) {
            error("To many implementation found in $impl as $samRawDescription:\n$implements")
        }

        val samDescription = MethodType.methodType(invokerSam.returnType, invokerSam.parameterTypes)

        val forwardedLookup = ccl.loadClass(ACCESSOR_CLASS).getMethod("access").invoke(null) as MethodHandles.Lookup

        @Suppress("UNCHECKED_CAST")
        return LambdaMetafactory.metafactory(
            forwardedLookup,
            invokerSam.name,
            MethodType.methodType(invoker),
            samDescription,
            forwardedLookup.unreflect(implements[0]),
            samDescription,
        ).target.invoke() as T
    }
}