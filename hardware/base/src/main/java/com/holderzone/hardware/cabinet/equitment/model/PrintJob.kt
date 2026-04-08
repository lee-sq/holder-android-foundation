package com.holderzone.hardware.cabinet.equitment.model

/**
 * 打印任务模型。
 *
 * @param title 可选标题（打印机可能用于抬头）
 * @param lines 文本行内容，按顺序打印
 * @param barcode 可选条码内容
 * @param qrCode 可选二维码内容
 * @param copies 份数，默认 1
 * @param height 高度
 * @param width 宽度
 */

data class PrintJob(
    val title: String? = null,
    val lines: List<String> = emptyList(),
    val barcode: String? = null,
    val qrCode: String? = null,
    val copies: Int = 1,
    val height: Int = 40,
    val width: Int = 55
)