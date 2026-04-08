package com.holderzone.utils.file

import android.util.Log
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 共享的后台文件删除队列：
 * - 调用 enqueue(path) 入队待删除路径
 * - 后台线程阻塞消费，无任务时休眠
 * - 支持停止/启动与统计
 */
object FileDeleteQueue {
    private const val TAG = "FileDeleteQueue"

    private val queue = LinkedBlockingQueue<String>()
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    /**
     * 启动后台删除线程（幂等）
     */
    @Synchronized
    fun start() {
        if (running.compareAndSet(false, true)) {
            worker = Thread({
                Log.i(TAG, "Delete worker started.")
                try {
                    // 主循环：无任务时 take() 阻塞（休眠）
                    while (running.get()) {
                        val path = try {
                            queue.take() // 队列空则阻塞等待
                        } catch (ie: InterruptedException) {
                            // 被 stop() 中断，退出循环
                            if (!running.get()) break
                            continue
                        }

                        runCatching { deletePath(path) }
                            .onFailure { e ->
                                Log.e(TAG, "Delete failed: $path -> ${e.message}", e)
                            }
                    }
                } finally {
                    Log.i(TAG, "Delete worker stopped.")
                }
            }, "FileDeleteWorker").apply {
                isDaemon = true // 随进程退出
                start()
            }
        }
    }

    /**
     * 停止后台删除线程（幂等）
     */
    @Synchronized
    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    /**
     * 入队一个待删除路径（文件或目录）
     */
    fun enqueue(path: String) {
        start() // 确保已启动
        queue.offer(path)
    }

    /**
     * 批量入队
     */
    fun enqueueAll(paths: Iterable<String>) {
        start()
        for (p in paths) queue.offer(p)
    }

    /**
     * 当前是否空闲（队列为空）
     */
    fun isIdle(): Boolean = queue.isEmpty()

    /**
     * 未处理任务数量
     */
    fun pendingCount(): Int = queue.size

    private fun deletePath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Path not exists, skip: $path")
            return
        }
        val ok = if (file.isDirectory) {
            // 递归删除目录
            file.deleteRecursively()
        } else {
            file.delete()
        }
        if (ok) {
            Log.i(TAG, "Deleted: $path")
        } else {
            // 某些外部存储路径可能需要权限或使用 SAF/ContentResolver
            Log.w(TAG, "Delete returned false: $path")
        }
    }
}