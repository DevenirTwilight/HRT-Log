package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Storage encoding only; LEGACY does not infer a physical unit or official product identity. */
internal object InventoryEncoding {
    const val ROOM_FORMAT_VERSION=13
    const val LEGACY="LEGACY_SCALAR"
    const val EXACT="INTEGER_QUANTA_V1"
    // Representation contract of the existing SQLite REAL bridge, not a reconstructed legacy precision.
    const val MAX_EXACT_INTEGER=9_007_199_254_740_991L
    fun exactInteger(value:Double):Long {
        require(value.isFinite() && value>=0 && value<=MAX_EXACT_INTEGER && value.toLong().toDouble()==value)
        return value.toLong()
    }
    fun present(db:SupportSQLiteDatabase)=db.query("PRAGMA table_info(supply_container)").use { c ->
        var found=false;while(c.moveToNext())if(c.getString(1)=="inventory_encoding")found=true;found
    }
    fun install(db:SupportSQLiteDatabase) {
        if(!present(db)) {require(db.version<ROOM_FORMAT_VERSION);return} // Published old-schema migration fixtures only.
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
