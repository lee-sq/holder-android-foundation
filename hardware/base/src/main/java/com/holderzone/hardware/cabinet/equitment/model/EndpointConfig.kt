package com.holderzone.hardware.cabinet.equitment.model

/**
 * 网络端点配置（TCP/HTTP 等）。
 *
 * @param host 主机地址
 * @param port 端口号
 * @param path 可选路径（HTTP）
 * @param secure 是否安全（HTTPS/TLS）
 */

data class EndpointConfig(
    val host: String,
    val port: Int,
    val path: String? = null,
    val secure: Boolean = false,
)