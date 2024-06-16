package com.kasukusakura.yggdrasilgateway.api.tracking

public class TrackingException(
    public val callingStack: CallingStack,
    cause: Throwable,
) : RuntimeException(cause) {
    override val message: String
        get() {
            return "Exception in context " + callingStack.fullToString() + ": " + super.message
        }
}