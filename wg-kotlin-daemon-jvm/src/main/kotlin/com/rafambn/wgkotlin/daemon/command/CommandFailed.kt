package com.rafambn.wgkotlin.daemon.command

internal class CommandFailed(
    operationLabel: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : RuntimeException(
    "Command `$operationLabel` failed with exit code $exitCode: ${selectOutputDetail(stdout = stdout, stderr = stderr) ?: "no output"}",
)

private fun selectOutputDetail(stdout: String, stderr: String): String? {
    return when {
        stderr.isNotBlank() -> stderr
        stdout.isNotBlank() -> stdout
        else -> null
    }
}
