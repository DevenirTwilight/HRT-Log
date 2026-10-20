package net.plainnotes.app.data

import org.json.JSONObject

/** Compact byte ranges over already authenticated, UTF-8/JSON/budget-validated input.
 * Indexing does not decide JSON validity. BackupJsonReader's unchanged strict parser does so first.
 * No decoded row/string is retained between calls; no plaintext temporary file exists.
 */
internal class IndexedBackupDocument internal constructor(private var bytes:ByteArray,val metadata:JSONObject,private val checkActive:()->Unit={}):AutoCloseable {
    private var closed=false
    private var offset=0
    private val indexes=LinkedHashMap<String,Rows>()
    val tables:BackupTables=object:BackupTables {
        override fun keys():Set<String>{check(!closed);return indexes.keys.toSet()}
        override fun optJSONArray(name:String):BackupRows?{check(!closed);return indexes[name]}
    }
    private inner class Rows:BackupRows {
        private var ranges=IntArray(0)
        private var size=0
        fun add(start:Int,end:Int) {
            require(size<BackupLimits.MAX_ROWS)
            if(size*2==ranges.size)ranges=ranges.copyOf(minOf(BackupLimits.MAX_ROWS*2,maxOf(32,ranges.size*2)))
            ranges[size*2]=start;ranges[size*2+1]=end;size++
        }
        override fun length():Int{check(!closed);return size}
        override fun getJSONObject(index:Int):JSONObject {
            check(!closed);checkActive();require(index in 0 until size)
            return BackupJsonReader.objectSlice(bytes,ranges[index*2],ranges[index*2+1])
        }
        fun clear(){ranges=IntArray(0);size=0}
    }
    private fun space(){while(offset<bytes.size)when(bytes[offset].toInt()){9,10,13,32->offset++;else->return}}
    private fun expect(c:Char){space();require(offset<bytes.size && bytes[offset++].toInt()==c.code)}
    private fun quotedEnd():Int {
        require(bytes[offset++].toInt()==34)
        while(offset<bytes.size)when(bytes[offset++].toInt()) {
            34->return offset
            92->{require(offset<bytes.size);offset++}
        }
        error("Unterminated validated string")
    }
    private fun skipValue() {
        space();require(offset<bytes.size)
        when(bytes[offset].toInt()) {
            34->quotedEnd()
            123,91->{
                offset++;var depth=1
                while(depth>0) {
                    require(offset<bytes.size)
                    when(bytes[offset].toInt()) {
                        34->quotedEnd()
                        123,91->{depth++;offset++}
                        125,93->{depth--;offset++}
                        else->offset++
                    }
                }
            }
            else->while(offset<bytes.size && bytes[offset].toInt() !in listOf(9,10,13,32,44,93,125))offset++
        }
    }
    private fun fields(consume:(String)->Unit) {
        expect('{');space()
        if(bytes[offset].toInt()==125){offset++;return}
        while(true) {
            space();val start=offset;val end=quotedEnd()
            // Key only; escaped names have exactly the same meaning as in the strict parser.
            val literal=String(bytes,start,end-start,Charsets.UTF_8)
            val name=JSONObject("{\"key\":$literal}").getString("key")
            expect(':');space();consume(name);space()
            when(bytes[offset++].toInt()){125->return;44->Unit;else->error("Invalid validated object")}
        }
    }
    init {
        // The old Repository also requires an object named tables before opening the destination.
        metadata.getJSONObject("tables")
        if(bytes.size>=3 && bytes[0]==0xef.toByte() && bytes[1]==0xbb.toByte() && bytes[2]==0xbf.toByte())offset=3
        fields {key->
            if(key!="tables")skipValue()
            else fields {table->
                val rows=Rows();require(indexes.put(table,rows)==null)
                expect('[');space()
                if(bytes[offset].toInt()==93)offset++ else while(true) {
                    checkActive();space();val start=offset;skipValue();rows.add(start,offset);space()
                    when(bytes[offset++].toInt()){93->break;44->Unit;else->error("Invalid validated array")}
                }
            }
        }
        space();require(offset==bytes.size)
        require(indexes.keys==metadata.getJSONObject("tables").keys().asSequence().toSet())
        // Header contains only empty table arrays, never rows or their payload strings.
    }
    override fun close() {
        if(!closed){closed=true;bytes.fill(0);bytes=byteArrayOf();indexes.values.forEach{it.clear()};indexes.clear()}
    }
}
