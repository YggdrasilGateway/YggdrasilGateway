package com.kasukusakura.yggdrasilgateway.core.module.codesnippets

import com.kasukusakura.yggdrasilgateway.api.codesnippet.CodeSnippetProvider
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.PrintWriter
import java.io.StringWriter

@EventSubscriber
private object HttpAccess {
    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() {
        if (!authorization) return

        route.createRouteFromPath("/code-snippets").mount()
    }

    private fun Route.mount() {
        get {
            call.respond(ApiSuccessDataResponse(buildJsonArray {
                CodeSnippetModule.snippets.values.forEach { snip ->
                    +buildJsonObject {
                        "key"(snip.codeSnippetId)
                        "name"(snip.codeSnippetName)
                    }
                }
            }))
        }

        get("{snippet}") {
            val snippetId = call.parameters["snippet"] ?: throw ApiRejectedException("No snippet argument found")
            val snippet = CodeSnippetModule.snippets[snippetId]
                ?: throw ApiRejectedException("Snippet $snippetId not exists")

            call.respond(ApiSuccessDataResponse {
                "key"(snippet.codeSnippetId)
                "name"(snippet.codeSnippetName)
                "defaultCode"(snippet.codeDefaultCode)
                "currentCode"(withContext(Dispatchers.IO) { CodeSnippetModule.queryCode(snippetId) })
            })
        }

        patch("{snippet}") {
            val snippetId = call.parameters["snippet"] ?: throw ApiRejectedException("No snippet argument found")
            val snippet = CodeSnippetModule.snippets[snippetId]
                ?: throw ApiRejectedException("Snippet $snippetId not exists")

            @Serializable
            class Req(
                val code: String?,
            )

            val req = call.receive<Req>()
            if (req.code.isNullOrBlank()) {
                snippet.loadedSnippet = null
                withContext(Dispatchers.IO) {
                    mysqlConnectionSource.connection.use { conn ->
                        conn.prepareStatement("DELETE FROM codesnippets where snippetId = ?").use { stmt ->
                            stmt.setString(1, snippetId)
                            stmt.executeUpdate()
                        }
                    }
                }

                call.respond(ApiSuccessDataResponse { "status"("passed") })
                return@patch
            }

            val compileLog = StringWriter()
            val compileResult = kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    val compiledInstance = CodeSnippetProvider.compileCodeSnippet(
                        snippet.codeSnippetName,
                        req.code,
                        snippet.samInterface,
                        logOutput = compileLog,
                    )

                    mysqlConnectionSource.connection.use { conn ->
                        conn.prepareStatement("REPLACE INTO codesnippets(snippetId, snippetCode) VALUES (?,?)")
                            .use { stmt ->
                                stmt.setString(1, snippetId)
                                stmt.setString(2, req.code)
                                stmt.executeUpdate()
                            }
                    }

                    @Suppress("UNCHECKED_CAST")
                    (snippet as CodeSnippet<Any>).loadedSnippet = compiledInstance
                }
            }

            call.respond(ApiSuccessDataResponse {
                "status"(if (compileResult.isFailure) "failure" else "passed")
                if (compileResult.isFailure) {
                    "error"(StringWriter().also { writer ->
                        compileResult.exceptionOrNull()?.printStackTrace(PrintWriter(writer))
                    }.toString())
                }
                "log"(compileLog.toString())
            })
        }
    }
}