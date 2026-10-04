package com.kirinonakar.symvacas.calculator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Large JSON strings consume twice their length in Binder's UTF-16 parcels. */
internal object EngineResultCodec {
    private const val TEXT_LIMIT = 64 * 1024

    fun compress(result:String):ByteArray? {
        if(result.length<=TEXT_LIMIT)return null
        val output=ByteArrayOutputStream()
        GZIPOutputStream(output).use {it.write(result.toByteArray(Charsets.UTF_8))}
        return output.toByteArray()
    }

    fun decode(text:String?,compressed:ByteArray?):String =
        if(compressed==null)text ?: "{}"
        else GZIPInputStream(ByteArrayInputStream(compressed)).use {it.readBytes().toString(Charsets.UTF_8)}
}
