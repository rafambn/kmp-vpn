package com.rafambn.wgkotlin.daemon.command

import com.rafambn.wgkotlin.daemon.protocol.DaemonFailureDetail

internal class CommandFailed(
    operationLabel: String,
    executable: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : RuntimeException(
    "Command `$operationLabel` failed with exit code $exitCode: ${selectOutputDetail(stdout = stdout, stderr = stderr) ?: "no output"}",
) {
    val detail: DaemonFailureDetail = DaemonFailureDetail(
        executable = executable,
        exitCode = exitCode,
    )
}

private fun selectOutputDetail(stdout: String, stderr: String): String? {
    return when {
        stderr.isNotBlank() -> stderr
        stdout.isNotBlank() -> stdout
        else -> null
    }
}
