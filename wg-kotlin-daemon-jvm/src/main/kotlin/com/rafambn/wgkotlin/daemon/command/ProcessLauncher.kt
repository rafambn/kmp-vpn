package com.rafambn.wgkotlin.daemon.command

internal fun interface ProcessLauncher {
    fun run(invocation: ProcessInvocationModel): ProcessOutputModel
}
