package com.holderzone.hardware.cabinet.equitment.api

/**
 * Optional heartbeat support for hardware providers.
 * Implementations should update the heartbeat timestamp whenever data is received.
 */
interface HeartbeatProvider {
    fun lastHeartbeatAtMs(): Long
}
