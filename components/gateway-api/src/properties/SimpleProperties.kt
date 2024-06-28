package com.kasukusakura.yggdrasilgateway.api.properties

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.PropertiesReloadEvent
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

public open class SimpleProperties(
    public val name: String,
    public val file: File = File("config/$name.properties"),
) {
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    public annotation class Property(
        @get:JvmName("value")
        public val name: String,
    )


    private val properties = mutableMapOf<String, Field>()

    init {
        generateSequence<Class<*>>(javaClass) { it.superclass }
            .takeWhile { it != SimpleProperties::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { acceptable(it) }
            .onEach { it.isAccessible = true }
            .forEach { field ->
                val prop = toPropertyName(field)
                properties.putIfAbsent(prop, field)?.let { old ->
                    error("Duplicated field: $prop, $field <-> $old")
                }
            }
    }

    protected open fun toPropertyName(field: Field): String {
        field.getDeclaredAnnotation(Property::class.java)?.let { return it.name }

        val name = field.name
        return name.asSequence()
            .map { char ->
                if (char.isUpperCase()) {
                    "." + char.lowercaseChar()
                } else {
                    char.toString()
                }
            }.joinToString("")
    }

    protected open fun acceptable(field: Field): Boolean {
        // the INSTANCE field
        if (field.type == field.declaringClass) return false

        return !field.isSynthetic && !Modifier.isTransient(field.modifiers)
    }

    protected open fun toEnvironmentName(propertyName: String, field: Field): String {
        return (this.name + "_" + propertyName)
            .replace('.', '_')
            .replace('-', '_')
            .uppercase(Locale.getDefault())
    }

    public open fun reload() {
        file.parentFile?.mkdirs()

        val currentProperties = Properties()
        properties.forEach { (name, field) ->
            currentProperties[name] = serializeValue(field, field.get(this))
        }

        val loadedProperties = Properties()
        file.takeIf { it.exists() }?.let { f ->
            f.bufferedReader().use { loadedProperties.load(it) }
        }
        properties.forEach { (property, field) ->
            val environmentName = toEnvironmentName(property, field)
            System.getenv(environmentName)?.let { loadedProperties.setProperty(property, it) }

            System.getenv(environmentName + "_FILE")?.let { targetFile ->
                kotlin.runCatching {
                    loadedProperties.setProperty(property, File(targetFile).readText())
                }.onFailure { err ->
                    LoggerFactory.getLogger(javaClass)
                        .warn("Exception when loading property {} using {}", property, targetFile, err)
                }
            }
        }

        var saveRequired = false
        currentProperties.forEach { (name, value) ->
            if (!loadedProperties.containsKey(name)) {
                saveRequired = true
                loadedProperties[name] = value
            }
        }
        loadedProperties.forEach { (name, value) ->
            val field = this.properties[name] ?: return@forEach
            kotlin.runCatching {
                field.set(this, deserializeValue(field, value.toString()))
            }.onFailure { err ->
                LoggerFactory.getLogger(javaClass)
                    .warn("Exception when loading property {} with value {}", name, value, err)
            }
        }

        if (saveRequired) {
            file.bufferedWriter().use { loadedProperties.store(it, null) }
        }
    }

    protected open fun serializeValue(field: Field, value: Any?): String = value.toString()
    protected open fun deserializeValue(field: Field, value: String): Any? {
        return when (field.type) {
            java.lang.Byte.TYPE -> value.toByte()
            java.lang.Short.TYPE -> value.toShort()
            Integer.TYPE -> value.toInt()
            java.lang.Long.TYPE -> value.toLong()

            java.lang.Float.TYPE -> value.toFloat()
            java.lang.Double.TYPE -> value.toDouble()

            java.lang.Boolean.TYPE -> value.toBoolean()

            BigInteger::class.java -> value.toBigInteger()
            BigDecimal::class.java -> value.toBigDecimal()

            String::class.java -> value

            else -> error("Unknown field type: ${field.type}")
        }
    }

    @EventSubscriber.Handler
    private fun PropertiesReloadEvent.handle() = reload()

}