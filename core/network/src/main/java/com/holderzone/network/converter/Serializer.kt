package com.holderzone.network.converter

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody

@Suppress("UNCHECKED_CAST") // Widening T to Any
sealed class Serializer {
    abstract fun <T> fromResponseBody(loader: DeserializationStrategy<T>, body: ResponseBody): T
    abstract fun <T> toRequestBody(contentType: MediaType, saver: SerializationStrategy<T>, value: T): RequestBody

    class FromString(private val format: StringFormat, ) : Serializer() {

        override fun <T> fromResponseBody(loader: DeserializationStrategy<T>, body: ResponseBody): T {
            val string = body.string()
            try {
                return format.decodeFromString(loader, string)
            } catch (e: Exception) {
                throw e
            }
        }

        /**
         * #1015
         * 修改时间:2019.3.22
         * 修改原因:在解析基础实体时候会存在错误
         * 原始方法,在原始kotlin基础上进行的整改。之前的方法备份。原始备份方法，如果存在问题可恢复
         * 修改方式:屏蔽原始代码，使用编号#1016代替本方法的功能
         * 冲突解决:如果出现冲突或其他问题可将#1016方法还原为本方法(#1015)
         */
        override fun <T> toRequestBody(contentType: MediaType, saver: SerializationStrategy<T>, value: T): RequestBody {
            val string = format.encodeToString(saver, value)
//            LogUtils.i("converter, 执行了toRequestBodyFromString，当前body:$string")
//            val str = checkDeviceType(string)
//            LogUtils.i("converter, 最终的toRequestBodyFromString，当前body:$str")
            return RequestBody.create(contentType, string)
        }

    }

    class FromBytes(private val format: BinaryFormat) : Serializer() {
        override fun <T> fromResponseBody(loader: DeserializationStrategy<T>, body: ResponseBody): T {
            val bytes = body.bytes()
            return format.decodeFromByteArray(loader, bytes)
        }

        override fun <T> toRequestBody(contentType: MediaType, saver: SerializationStrategy<T>, value: T): RequestBody {
            val bytes = format.encodeToByteArray(saver, value)
            return RequestBody.create(contentType, bytes)
        }
    }
}