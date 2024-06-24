package com.kasukusakura.yggdrasilgateway.core

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.PropertiesReloadEvent
import com.kasukusakura.yggdrasilgateway.api.events.system.RootCoroutineScopeInitializeEvent
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilGatewayBootstrapEvent
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import com.kasukusakura.yggdrasilgateway.api.util.childrenContext
import com.kasukusakura.yggdrasilgateway.api.util.eventFire
import com.kasukusakura.yggdrasilgateway.core.config.HttpServerProperties
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

@EventSubscriber
internal object YggdrasilGatewayCore {
    private fun handleCoroutineException(coroutineContext: CoroutineContext, throwable: Throwable) {}

    private val log = LoggerFactory.getLogger(YggdrasilGatewayCore::class.java)
    lateinit var coroutineScope: CoroutineScope

    @EventSubscriber.Handler
    fun YggdrasilGatewayBootstrapEvent.boot() {
        log.info("Initializing kotlin coroutine...")
        coroutineScope = CoroutineScope(
            Dispatchers.IO
                + Job()
                + CoroutineName("YggdrasilGateway")
                + CoroutineExceptionHandler(::handleCoroutineException)
        )

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        com.kasukusakura.yggdrasilgateway.api.internal.GlobalEventBusCoroutineScopeDelegate.coroutineContext =
            coroutineScope.coroutineContext.childrenContext(
                name = "Monitor Event Handlers",
                supervisor = false,
            )
        runBlocking { RootCoroutineScopeInitializeEvent(coroutineScope).eventFire() }



        log.info("Starting process holder...")
        thread(
            start = true,
            isDaemon = false,
            name = "Process Holder",
        ) { runBlocking { coroutineScope.coroutineContext.job.join() } }




        log.info("Reloading properties")
        runBlocking { PropertiesReloadEvent.eventFire() }


        log.info("Initializing Database Connections")
        DatabaseConnectionManager.mysqlDatabase
        DatabaseConnectionManager.mysqlConnectionSource.connection.close()
        runBlocking { DatabaseInitializationEvent.eventFire() }

        log.info("Initializing Http Server...")
        embeddedServer(
            Netty,
            environment = applicationEngineEnvironment {
                this.parentCoroutineContext =
                    coroutineScope.coroutineContext.childrenContext(name = "Gateway Http Server")
                this.log = YggdrasilGatewayCore.log
                this.module {
                    runBlocking {
                        YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent(this@module).eventFire()
                    }
                }

                runBlocking {
                    YggdrasilHttpServerInitializeEvent.EnvironmentInitializeEvent(this@applicationEngineEnvironment)
                        .eventFire()
                }
            }
        ).start(wait = false)
    }

    @EventSubscriber.Handler
    private fun YggdrasilHttpServerInitializeEvent.EnvironmentInitializeEvent.handle() {
        envBuilder.connector {
            host = HttpServerProperties.host
            port = HttpServerProperties.port
        }
    }
}