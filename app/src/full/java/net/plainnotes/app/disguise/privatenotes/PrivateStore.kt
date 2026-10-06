package net.plainnotes.app.disguise.privatenotes

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ordinary notes only. No HRT entities, repository, domain, transfer or reminder dependency. */
data class PrivateNote(val id:String,val title:String,val body:String,val createdAt:Long,val updatedAt:Long)

internal interface PrivateKeys {
    fun get(create:Boolean):SecretKey
    fun destroy()
}
internal class AndroidPrivateKeys : PrivateKeys {
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    override fun get(create:Boolean):SecretKey {
        (store().getKey(PrivateStore.KEY_ALIAS,null) as? SecretKey)?.let{return it}
        check(create) { "Storage unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(PrivateStore.KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    override fun destroy() { store().deleteEntry(PrivateStore.KEY_ALIAS) }
}

/** One small authenticated encrypted document in noBackupFilesDir, atomically replaced; no plaintext sidecar/cache. */
class PrivateStore internal constructor(context:Context,private val keys:PrivateKeys) {
    constructor(context:Context):this(context,AndroidPrivateKeys())
    private val file=AtomicFile(File(context.noBackupFilesDir,FILE_NAME))
    companion object {
        const val FILE_NAME="private.bin"
        const val KEY_ALIAS="notes.private"
        private const val MAX_BYTES=10*1024*1024
        private val lock=Any() // Serializes read/modify/write across Activity/settings instances.
        private val aad="net.plainnotes.app.private.v1".toByteArray(Charsets.UTF_8)
    }
    fun list():List<PrivateNote> = synchronized(lock) { read().sortedByDescending{it.updatedAt} }
    fun save(id:String?,title:String,body:String,now:Long=System.currentTimeMillis()):PrivateNote = synchronized(lock) {
        require(title.isNotBlank() || body.isNotBlank())
        val notes=read().toMutableList()
        val prior=id?.let{key->notes.single{it.id==key}}
        val note=PrivateNote(prior?.id ?: UUID.randomUUID().toString(),title,body,prior?.createdAt ?: now,maxOf(now,prior?.createdAt ?: now))
        notes.removeAll{it.id==note.id};notes.add(note);write(notes);note
    }
    fun delete(id:String) = synchronized(lock) {
        val notes=read();require(notes.any{it.id==id});write(notes.filterNot{it.id==id})
    }
    /** Also removes AtomicFile backup/new files and the non-exportable key. Never touches other storage. */
    fun destroy() = synchronized(lock) {
        file.delete();File(file.baseFile.path+".new").delete()
        keys.destroy()
        check(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())
    }
    private fun read():List<PrivateNote> {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return emptyList()
        require(maxOf(file.baseFile.length(),File(file.baseFile.path+".bak").length())<=MAX_BYTES+64)
        val bytes=file.readFully();require(bytes.size>=29 && bytes[0]==1.toByte())
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,keys.get(false),GCMParameterSpec(128,bytes.copyOfRange(1,13)));cipher.updateAAD(aad)
        val plain=cipher.doFinal(bytes.copyOfRange(13,bytes.size))
        try {
            val json=JSONObject(plain.toString(Charsets.UTF_8));require(json.getInt("version")==1)
            val rows=json.getJSONArray("notes")
            val notes=(0 until rows.length()).map{i->rows.getJSONObject(i).let{r->
                PrivateNote(r.getString("id"),r.getString("title"),r.getString("body"),r.getLong("createdAt"),r.getLong("updatedAt"))}}
            require(notes.map{it.id}.distinct().size==notes.size && notes.all{it.id.isNotBlank() && it.updatedAt>=it.createdAt})
            return notes
        } finally { plain.fill(0) }
    }
    private fun write(notes:List<PrivateNote>) {
        val rows=JSONArray();notes.forEach{n->rows.put(JSONObject().put("id",n.id).put("title",n.title).put("body",n.body).put("createdAt",n.createdAt).put("updatedAt",n.updatedAt))}
        val plain=JSONObject().put("version",1).put("notes",rows).toString().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size<=MAX_BYTES)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.get(true));cipher.updateAAD(aad)
            val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(plain)
            val out=file.startWrite()
            try { out.write(bytes);file.finishWrite(out) } catch(e:Exception) { file.failWrite(out);throw e }
        } finally { plain.fill(0) }
    }
}
