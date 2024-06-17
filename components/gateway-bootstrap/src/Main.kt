package com.kasukusakura.yggdrasilgateway.bootstrap

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventBus
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilGatewayBootstrapEvent
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.lang.management.ManagementFactory
import java.util.zip.ZipFile
import kotlin.system.exitProcess

public suspend fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Bootstrap")
    log.info("Bootstrap...")
    val classpaths = ManagementFactory.getRuntimeMXBean().classPath
    classpaths.split(File.pathSeparator).forEach { filePath ->
        log.info("Scanning {}", filePath)
        runCatching {
            val file = File(filePath)

            fun loadClass(name: String) = Class.forName(name.replace('/', '.'))
            fun loadInstance(name: String): Any {
                val klass = loadClass(name).kotlin
                return klass.objectInstance ?: klass.java.newInstance()
            }

            fun readingClass(name: String, block: () -> InputStream) {
                val pname = name.replace('\\', '/').removeSuffix(".class")
                runCatching {

                    block().use { stream ->
                        val classNode = ClassNode()
                        ClassReader(stream).accept(classNode, 0)
                        if (classNode.name != pname) return

                        val annos = sequenceOf(
                            classNode.invisibleAnnotations,
                            classNode.visibleAnnotations,
                        ).flatMap { it.orEmpty().asSequence() }.map {
                            it.desc.removeSuffix(";").removePrefix("L")
                        }.toSet()

                        if ("com/kasukusakura/yggdrasilgateway/api/eventbus/EventSubscriber" in annos) {
                            EventBus.GLOBAL_BUS.registerListener(file, loadInstance(pname))
                        }
                    }

                }.onFailure { log.warn("Exception when processing calss {} of {}", pname, filePath, it) }
            }



            if (file.isDirectory) {
                file.walkTopDown()
                    .filter { it.isFile }
                    .filter { it.extension == "class" }
                    .forEach { subfile ->
                        val subpath = subfile.relativeTo(file)
                        readingClass(subpath.path) { subfile.inputStream() }
                    }
            } else if (file.extension == "jar") {
                ZipFile(file).use { zipFile ->
                    zipFile.entries().asSequence()
                        .filter { it.name.endsWith(".class") }
                        .forEach { zipEntry ->
                            readingClass(zipEntry.name) { zipFile.getInputStream(zipEntry) }
                        }
                }
            }
        }.onFailure { log.warn("Exception when walking {}", filePath, it) }
    }

    runCatching {
        EventBus.GLOBAL_BUS.fireEvent(YggdrasilGatewayBootstrapEvent, nofail = false)
    }.onFailure { err ->
        log.error("Failed to bootstrap", err)
        exitProcess(-5)
    }
}

