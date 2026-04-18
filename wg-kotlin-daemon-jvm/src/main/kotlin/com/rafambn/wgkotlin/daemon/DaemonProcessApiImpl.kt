package com.rafambn.wgkotlin.daemon

import com.rafambn.wgkotlin.daemon.command.CommandFailed
import com.rafambn.wgkotlin.daemon.command.StartFailure
import com.rafambn.wgkotlin.daemon.command.TimeoutFailure
import com.rafambn.wgkotlin.daemon.planner.PlatformAdapter
import com.rafambn.wgkotlin.daemon.planner.PlatformAdapterFactory
import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.DaemonErrorKind
import com.rafambn.wgkotlin.daemon.protocol.DaemonProcessApi
import com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig
import com.rafambn.wgkotlin.daemon.protocol.response.PingResponse
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val MAX_PACKET_FRAME_SIZE: Int = 65535

class DaemonProcessApiImpl internal constructor(
    private val adapter: PlatformAdapter = PlatformAdapterFactory.fromOs(),
) : DaemonProcessApi {
    private val activeSessionLock = Any()
    private val activeSessions = mutableSetOf<String>()

    override suspend fun ping(): CommandResult<PingResponse> {
        return CommandResult.success(PingResponse)
    }

    override fun startSession(
        config: TunSessionConfig,
        outgoingPackets: Flow<ByteArray>,
    ): Flow<ByteArray> = channelFlow {
        DaemonPayloadValidator.validate(config)

        synchronized(activeSessionLock) {
            if (activeSessions.contains(config.interfaceName)) {
                throw IllegalStateException("Session already active for ${config.interfaceName}")
            }
            activeSessions.add(config.interfaceName)
        }

        val handle = try {
            adapter.startSession(config)
        } catch (failure: Throwable) {
            synchronized(activeSessionLock) {
                activeSessions.remove(config.interfaceName)
            }
            throw failure
        }

        val writerJob = launch {
            outgoingPackets.collect { packet ->
                if (packet.size > MAX_PACKET_FRAME_SIZE) {
                    throw IllegalArgumentException("Oversized packet: ${packet.size}")
                }
                if (packet.isNotEmpty()) {
                    handle.writePacket(packet)
                }
            }
        }

        try {
            while (isActive) {
                val packet = handle.readPacket() ?: continue
                if (packet.size > MAX_PACKET_FRAME_SIZE) {
                    throw IllegalStateException("Oversized packet from TUN: ${packet.size}")
                }
                if (packet.isNotEmpty()) {
                    send(packet)
                }
            }
        } finally {
            runCatching { writerJob.cancelAndJoin() }
            runCatching { handle.close() }
            synchronized(activeSessionLock) {
                activeSessions.remove(config.interfaceName)
            }
        }
    }

    private fun <S> toFailureResult(
        commandType: String,
        failure: Throwable,
    ): CommandResult<S> {
        return when (failure) {
            is PayloadValidationException -> CommandResult.failure(
                kind = DaemonErrorKind.VALIDATION_ERROR,
                message = failure.message ?: "Invalid payload",
            )

            is StartFailure -> CommandResult.failure(
                kind = DaemonErrorKind.PROCESS_START_FAILURE,
                message = failure.message ?: "Failed to start privileged process",
                detail = com.rafambn.wgkotlin.daemon.protocol.DaemonFailureDetail(executable = failure.executable),
            )

            is TimeoutFailure -> CommandResult.failure(
                kind = DaemonErrorKind.PROCESS_TIMEOUT,
                message = failure.message ?: "Privileged process timed out",
                detail = com.rafambn.wgkotlin.daemon.protocol.DaemonFailureDetail(executable = failure.executable),
            )

            is CommandFailed -> CommandResult.failure(
                kind = DaemonErrorKind.COMMAND_FAILED,
                message = failure.message ?: "Privileged command failed",
                detail = failure.detail,
            )

            else -> CommandResult.failure(
                kind = DaemonErrorKind.INTERNAL_ERROR,
                message = "Unexpected daemon failure in `$commandType`: ${failure.message ?: "unknown"}",
            )
        }
    }
}
