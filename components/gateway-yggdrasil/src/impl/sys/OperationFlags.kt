package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

internal class OperationFlags {
    var processMode = ProcessMode.COMPLETE_TEST
    var autoResolveName = true
    var autoResolveUuidConflict = true
    var enchantedErrorRejection = true
    var prohibitMode = false

    enum class ProcessMode {
        FIRST_SUCCESS,
        ERROR_SKIP,
        COMPLETE_TEST,
    }
}