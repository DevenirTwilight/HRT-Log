package net.plainnotes.app.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.math.BigDecimal
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Strict, bounded JSON input. Numeric lexemes are not rounded through Double before validation. */
/** Frozen352308b3 full-DOM oracle, test-only. */
internal object FrozenRestoreJsonReader {
    private val integerToken=Regex("-?(0|[1-9][0-9]*)")
    fun read(bytes:ByteArray):JSONObject {
        if(bytes.size>BackupLimits.MAX_JSON_BYTES)throw BackupCodec.TooLarge()
        fun reader()=InputStreamReader(ByteArrayInputStream(bytes),Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        // Consume the strict UTF-8/lexical pass completely, including malformed trailing bytes.
        try {reader().use{BackupLimits.checkJson(it)}}
        catch(_:CharacterCodingException){throw BackupCodec.BadFile("invalid UTF-8")}
        return parse(reader())
    }
    fun read(text:String):JSONObject {
        BackupLimits.checkJson(text)
        return parse(StringReader(text))
    }
    private fun parse(input:Reader):JSONObject {
        try {
            JsonReader(input).use { reader ->
                reader.isLenient=false
                var arrays=0;var objects=0
                fun number(token:String):Number {
                    if(token!="-0" && integerToken.matches(token)) {
                        token.toLongOrNull()?.let {return if(it in Int.MIN_VALUE..Int.MAX_VALUE)it.toInt() else it}
                    }
                    return BigDecimal(token)
                }
                fun value(depth:Int):Any {
                    if(depth>32)throw BackupCodec.TooLarge()
                    return when(reader.peek()) {
                        JsonToken.BEGIN_OBJECT->{
                            if(++objects>BackupLimits.MAX_ROWS+256)throw BackupCodec.TooLarge()
                            reader.beginObject();val result=JSONObject();var fields=0
                            while(reader.hasNext()) {
                                if(++fields>256)throw BackupCodec.TooLarge()
                                val name=reader.nextName();require(!result.has(name)) {"Duplicate JSON field"}
                                result.put(name,value(depth+1))
                            }
                            reader.endObject();result
                        }
                        JsonToken.BEGIN_ARRAY->{
                            reader.beginArray();val result=JSONArray()
                            while(reader.hasNext()) {
                                if(++arrays>BackupLimits.MAX_ROWS)throw BackupCodec.TooLarge()
                                result.put(value(depth+1))
                            }
                            reader.endArray();result
                        }
                        JsonToken.STRING->reader.nextString()
                        JsonToken.NUMBER->number(reader.nextString())
                        JsonToken.BOOLEAN->reader.nextBoolean()
                        JsonToken.NULL->{reader.nextNull();JSONObject.NULL}
                        else->error("Invalid JSON token")
                    }
                }
                val root=value(0);require(root is JSONObject && reader.peek()==JsonToken.END_DOCUMENT)
                return root
            }
        } catch(e:BackupCodec.TooLarge){throw e}
        catch(e:Exception){throw BackupCodec.BadFile("invalid JSON")}
    }
}
