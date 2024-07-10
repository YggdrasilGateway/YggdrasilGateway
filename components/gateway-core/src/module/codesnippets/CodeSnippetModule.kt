package com.kasukusakura.yggdrasilgateway.core.module.codesnippets

import com.kasukusakura.yggdrasilgateway.api.codesnippet.CodeSnippetProvider
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.RootCoroutineScopeInitializeEvent
import com.kasukusakura.yggdrasilgateway.api.util.childrenScope
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.util.ensureDatabaseAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@EventSubscriber
public object CodeSnippetModule {
    private lateinit var coroutineScope: CoroutineScope
    internal val snippets = ConcurrentHashMap<String, CodeSnippet<*>>()
    private val log = LoggerFactory.getLogger(CodeSnippetModule::class.java)

    @EventSubscriber.Handler
    private fun coroutineInit(evt: RootCoroutineScopeInitializeEvent) {
        coroutineScope = evt.coroutineScope.childrenScope("Code Snippet")

        snippets.values.forEach { s ->
            coroutineScope.launch { loadSnippet(s) }
        }
    }

    internal fun queryCode(id: String): String {
        mysqlConnectionSource.connection.use codeQuery@{ connection ->
            connection.prepareStatement("SELECT snippetCode from codesnippets where snippetId = ?").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getString(1).orEmpty()
                    }
                }
            }
        }
        return ""
    }

    public fun <T> registerSnippet(
        samInterface: Class<T>,
        codeSnippetId: String,
        codeSnippetName: String,
        @Language("java") codeDefaultCode: String,
        defaultInstance: T,
    ): CodeSnippet<T> {
        val snippet = CodeSnippet(
            samInterface = samInterface,
            defaultInstance = defaultInstance,
            codeSnippetId = codeSnippetId,
            codeSnippetName = codeSnippetName,
            codeDefaultCode = codeDefaultCode,
        )
        if (snippets.putIfAbsent(codeSnippetId, snippet) != null) {
            error("Snippet $codeSnippetId already registered")
        }

        if (::coroutineScope.isInitialized) {
            coroutineScope.launch {
                loadSnippet(snippet)
            }
        }

        return snippet
    }

    private suspend fun loadSnippet(snippet: CodeSnippet<*>) = withContext(Dispatchers.IO) {
        ensureDatabaseAvailable()
        val code = queryCode(snippet.codeSnippetId)

        if (code.isNotBlank()) {
            kotlin.runCatching {
                @Suppress("UNCHECKED_CAST")
                (snippet as CodeSnippet<Any>).loadedSnippet = CodeSnippetProvider.compileCodeSnippet(
                    snippet.codeSnippetName,
                    code,
                    snippet.samInterface,
                )
            }.onFailure { err ->
                log.warn("Exception when loading code snippet {} with code:\n{}", snippet.codeSnippetId, code, err)
            }
        }
    }
}