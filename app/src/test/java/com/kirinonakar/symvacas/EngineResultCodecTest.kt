package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.calculator.EngineResultCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class EngineResultCodecTest {
    @Test fun maximumDensitySurfaceFitsBinderAndRestoresEveryCoordinate() {
        val mesh=JSONArray()
        for(row in 0..96) {
            val points=JSONArray()
            for(col in 0..96) {
                val x=(col+.1234567890123)/Math.PI
                val y=(row+.2345678901234)/Math.E
                points.put(JSONArray(listOf(x,y,sin(x+y))))
            }
            mesh.put(points)
        }
        val result=JSONObject().put("ok",true).put("surface",mesh).toString()
        // String parcels would exceed Binder's shared 1 MiB transaction buffer.
        assertTrue(result.toByteArray(Charsets.UTF_16LE).size>1024*1024)
        val compressed=EngineResultCodec.compress(result)!!
        assertTrue(compressed.size<256*1024)
        assertEquals(result,EngineResultCodec.decode(null,compressed))
        val restored=JSONObject(EngineResultCodec.decode(null,compressed)).getJSONArray("surface")
        assertEquals(97,restored.length())
        assertEquals(mesh.getJSONArray(96).getJSONArray(96).getDouble(2),restored.getJSONArray(96).getJSONArray(96).getDouble(2),0.0)
    }

    @Test fun compressionPreservesLargeUnicodeResults() {
        val result="계산 결과 · π · 😀".repeat(10000)
        assertEquals(result,EngineResultCodec.decode(null,EngineResultCodec.compress(result)))
    }
}
