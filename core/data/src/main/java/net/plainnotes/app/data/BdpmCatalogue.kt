package net.plainnotes.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class BdpmComposition(val element:String,val code:String,val name:String,val strength:String,val basis:String,val kind:String,val link:String) {
    // Preserve SA/ST and their linkage as separate facts. Never sum or normalize doses.
    fun display()="$name — $strength / $basis [$kind; $link; $code; $element]"
}
data class BdpmPackage(val cip7:String,val description:String,val cip13:String)
data class BdpmProduct(val cis:String,val name:String,val form:String,val routes:List<String>,val composition:List<BdpmComposition>,val packages:List<BdpmPackage>)
data class BdpmPage(val entries:List<BdpmProduct>,val limited:Boolean,val metadata:String)

/** Public reference data only. Independent read-only framework SQLite; never opens the encrypted user DB. */
class BdpmCatalogue(context:Context) {
    private val context=context.applicationContext
    companion object {
        fun fold(value:String)=Normalizer.normalize(value.lowercase(Locale.ROOT).replace("œ","oe").replace("æ","ae"),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        private val lock=Any()
    }
    private fun open():SQLiteDatabase=synchronized(lock) {
        val manifest=JSONObject(context.assets.open("bdpm/manifest.json").bufferedReader().use{it.readText()})
        val expected=manifest.getString("database_sha256")
        val folder=File(context.noBackupFilesDir,"bdpm").apply{check(mkdirs() || isDirectory)}
        val file=File(folder,"$expected.sqlite")
        fun digest(file:File):String {
            val md=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val bytes=ByteArray(32768);while(true){val count=input.read(bytes);if(count<0)break;md.update(bytes,0,count)}}
            return md.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
        }
        // Validate every open, permitting a retry to repair an incomplete/corrupted public copy.
        if(!file.exists() || digest(file)!=expected) {
            val temporary=File(folder,"copy.tmp")
            try {
                context.assets.open("bdpm/catalogue.sqlite").use{source->temporary.outputStream().use{source.copyTo(it)}}
                check(digest(temporary)==expected){"Catalogue digest mismatch"}
                check(!file.exists() || file.delete());check(temporary.renameTo(file))
            }finally{temporary.delete()}
        }
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }
    suspend fun search(query:String):BdpmPage=withContext(Dispatchers.IO) {
        require(query.length<=256)
        val words=fold(query.trim()).split(Regex("\\s+")).filter{it.isNotEmpty()}.take(16)
        if(words.isEmpty())return@withContext BdpmPage(emptyList(),false,metadata())
        open().use {db->
            val where=words.joinToString(" AND "){"instr(search,?)>0"}
            val entries=mutableListOf<BdpmProduct>()
            db.rawQuery("SELECT cis,name,form,routes FROM product WHERE $where ORDER BY name,cis LIMIT 51",words.toTypedArray()).use{c->
                while(c.moveToNext())entries+=product(db,c.getString(0),c.getString(1),c.getString(2),c.getString(3))
            }
            BdpmPage(entries.take(50),entries.size>50,metadata())
        }
    }
    private fun metadata()=context.assets.open("bdpm/manifest.json").bufferedReader().use{it.readText()}
    private fun product(db:SQLiteDatabase,cis:String,name:String,form:String,routes:String):BdpmProduct {
        val composition=mutableListOf<BdpmComposition>()
        db.rawQuery("SELECT element,substance_code,name,strength,basis,kind,link FROM composition WHERE cis=? ORDER BY rowid",arrayOf(cis)).use{c->
            while(c.moveToNext())composition+=BdpmComposition(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6))
        }
        val packages=mutableListOf<BdpmPackage>()
        db.rawQuery("SELECT cip7,description,cip13 FROM package WHERE cis=? ORDER BY rowid",arrayOf(cis)).use{c->while(c.moveToNext())packages+=BdpmPackage(c.getString(0),c.getString(1),c.getString(2))}
        return BdpmProduct(cis,name,form,routes.split(';').filter{it.isNotBlank()},composition,packages)
    }
    suspend fun snapshot(product:BdpmProduct,pack:BdpmPackage?):UserReferenceProduct=withContext(Dispatchers.IO) {
        open().use{db->
            db.rawQuery("SELECT name,form,routes FROM product WHERE cis=?",arrayOf(product.cis)).use{c->
                require(c.moveToFirst())
                require(product(db,product.cis,c.getString(0),c.getString(1),c.getString(2))==product){"Stale catalogue selection"}
            }
        }
        require(pack==null || pack in product.packages)
        val m=JSONObject(metadata());val inputs=m.getJSONObject("inputs")
        val dates=listOf("CIS_bdpm.txt","CIS_CIP_bdpm.txt","CIS_COMPO_bdpm.txt").map{inputs.getJSONObject(it).getString("source_updated_on")}.distinct()
        UserReferenceProduct(product.name,product.composition.map{it.display()},null,product.form,product.routes,product.cis,
            pack?.cip13?.takeIf{it.isNotEmpty()},null,m.getString("catalogue_version"),m.getString("downloaded_on"),
            BdpmProvenance(m.getString("database_sha256"),dates.joinToString(" / "),pack?.cip7,pack?.description,m.getString("download_page")))
            .also {ReferencePayload.userProduct(it)}
    }
}
