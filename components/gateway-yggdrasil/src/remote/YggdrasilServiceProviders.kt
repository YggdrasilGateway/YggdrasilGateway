package com.kasukusakura.yggdrasilgateway.yggdrasil.remote

import java.util.*
import java.util.concurrent.ConcurrentHashMap

public object YggdrasilServiceProviders {
    public val providers: Set<YggdrasilServiceProvider> by lazy {
        Collections.unmodifiableSet(_providers.keys)
    }

    private val _providers = ConcurrentHashMap<YggdrasilServiceProvider, Boolean>()


    public fun register(provider: YggdrasilServiceProvider) {
        _providers[provider] = true
    }

    public fun unregister(provider: YggdrasilServiceProvider) {
        _providers.remove(provider)
    }

    public fun constructService(basepath: String): YggdrasilService {
        return _providers.keys.asSequence()
            .sortedWith(Comparator.comparingInt { it.priority })
            .mapNotNull { it.apply(basepath) }
            .firstOrNull() ?: error("No service provider supported for $basepath")
    }
}
