package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Storage encoding only; LEGACY does not infer a physical unit or official product identity. */
internal object InventoryEncoding {
    const val LEGACY="LEGACY_SCALAR"
    const val EXACT="INTEGER_QUANTA_V1"
    fun present(db:SupportSQLiteDatabase)=db.query("PRAGMA table_info(supply_container)").use { c ->
        var found=false;while(c.moveToNext())if(c.getString(1)=="inventory_encoding")found=true;found
    }
    fun install(db:SupportSQLiteDatabase) {
        if(!present(db)) {require(db.version<12);return} // Published old-schema migration fixtures only.
        for(operation in listOf("INSERT","UPDATE")) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS inventory_encoding_${operation.lowercase()} BEFORE $operation ON supply_container WHEN NEW.inventory_encoding NOT IN ('$LEGACY','$EXACT') BEGIN SELECT RAISE(ABORT,'Invalid inventory encoding'); END")
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS inventory_encoding_immutable BEFORE UPDATE ON supply_container WHEN NEW.inventory_encoding!=OLD.inventory_encoding BEGIN SELECT RAISE(ABORT,'Immutable inventory encoding'); END")
    }
    fun validate(db:SupportSQLiteDatabase) {
        require(present(db))
        db.query("SELECT 1 FROM supply_container WHERE inventory_encoding NOT IN ('$LEGACY','$EXACT') LIMIT 1").use{require(!it.moveToFirst())}
    }
    fun legacyOnly(db:SupportSQLiteDatabase) {
        validate(db)
        db.query("SELECT 1 FROM supply_container WHERE inventory_encoding!='$LEGACY' LIMIT 1").use{require(!it.moveToFirst()) {"Exact inventory storage not yet enabled"}}
    }
}
