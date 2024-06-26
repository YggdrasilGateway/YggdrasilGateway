package com.kasukusakura.yggdrasilgateway.yggdrasil.remote

public interface YggdrasilServiceProvider {
    public val priority: Int get() = 1000

    public fun apply(basePath: String): YggdrasilService?
}