package com.areslib.networktables

import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import java.io.ByteArrayOutputStream

/**
 * Spec-compliant NT4 wire-protocol encoder and decoder using MsgPack stream buffers.
 */
object NT4WireProtocol {

    /**
     * Encodes a single topic payload into NT4 MsgPack binary array `[topicId, timestampUs, typeId, value]`.
     */
    fun encodeValueMessage(topicId: Long, timestampUs: Long, typeId: Int, value: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        val packer = MessagePack.newDefaultPacker(out)
        
        // Single message is wrapped in an outer array header of size 1
        packer.packArrayHeader(1)
        packer.packArrayHeader(4)
        packer.packLong(topicId)
        packer.packLong(timestampUs)
        packer.packInt(typeId)
        
        when (value) {
            is Boolean -> packer.packBoolean(value)
            is Number -> packer.packDouble(value.toDouble())
            is String -> packer.packString(value)
            is ByteArray -> {
                packer.packBinaryHeader(value.size)
                packer.writePayload(value)
            }
            else -> packer.packNil()
        }
        
        packer.flush()
        return out.toByteArray()
    }
}
