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
internal object BackupJsonReader {
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
    /** Owns authenticated plaintext until restore completes. Never creates a decoded whole-document String/DOM. */
    fun indexed(bytes:ByteArray,checkActive:()->Unit={},beforeParse:()->Unit={}):IndexedBackupDocument {
        try {
            beforeParse();checkActive()
            if(bytes.size>BackupLimits.MAX_JSON_BYTES)throw BackupCodec.TooLarge()
            fun reader()=InputStreamReader(ByteArrayInputStream(bytes),Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
            try {reader().use{BackupLimits.checkJson(it)}}
            catch(_:CharacterCodingException){throw BackupCodec.BadFile("invalid UTF-8")}
            val header=parse(reader(),releaseTableRows=true,checkActive=checkActive)
            return IndexedBackupDocument(bytes,header,checkActive)
        }catch(t:Throwable){bytes.fill(0);throw t}
    }
    internal fun objectSlice(bytes:ByteArray,start:Int,end:Int):JSONObject = parse(InputStreamReader(
        ByteArrayInputStream(bytes,start,end-start),Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))
    fun read(text:String):JSONObject {
        BackupLimits.checkJson(text)
        return parse(StringReader(text))
    }
    /** Same strict parser and global budgets. Only direct table rows may be released after parsing. */
    private fun parse(input:Reader,releaseTableRows:Boolean=false,checkActive:()->Unit={}):JSONObject {
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
                fun value(depth:Int,isTables:Boolean=false,isRows:Boolean=false,retain:Boolean=true):Any {
                    checkActive()
                    if(depth>32)throw BackupCodec.TooLarge()
                    return when(reader.peek()) {
                        JsonToken.BEGIN_OBJECT->{
                            if(++objects>BackupLimits.MAX_ROWS+256)throw BackupCodec.TooLarge()
                            reader.beginObject();val result=JSONObject();var fields=0
                            while(reader.hasNext()) {
                                if(++fields>256)throw BackupCodec.TooLarge()
                                val name=reader.nextName();require(!result.has(name)) {"Duplicate JSON field"}
                                val child=value(depth+1,isTables=releaseTableRows && depth==0 && name=="tables",isRows=isTables,
                                    retain=retain && (!releaseTableRows || depth!=0 || name in setOf("format","schema","tables")))
                                result.put(name,if(retain)child else JSONObject.NULL)
                            }
                            reader.endObject();result
                        }
                        JsonToken.BEGIN_ARRAY->{
                            reader.beginArray();val result=JSONArray()
                            while(reader.hasNext()) {
                                if(++arrays>BackupLimits.MAX_ROWS)throw BackupCodec.TooLarge()
                                val row=value(depth+1,retain=retain && (!releaseTableRows || !isRows))
                                if(retain && (!releaseTableRows || !isRows))result.put(row)
                            }
                            reader.endArray();result
                        }
                        JsonToken.STRING->reader.nextString().let{if(retain)it else JSONObject.NULL}
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
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){throw BackupCodec.BadFile("invalid JSON")}
    }
}
