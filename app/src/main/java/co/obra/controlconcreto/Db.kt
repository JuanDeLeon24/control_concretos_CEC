package co.obra.controlconcreto

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Base de datos SQLite dentro del teléfono. Cada cambio se guarda al instante. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "concreto.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE jornadas(id TEXT PRIMARY KEY, fecha TEXT NOT NULL, turno TEXT NOT NULL, " +
                "nombre TEXT, frente TEXT, tramo TEXT, creada TEXT, catalogo INTEGER DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE mixers(id TEXT PRIMARY KEY, jornada_id TEXT NOT NULL, orden INTEGER NOT NULL, " +
                "codigo TEXT, llegada TEXT, inicio TEXT, fin TEXT, cant TEXT, as_planta TEXT, as_obra TEXT, " +
                "temp TEXT, loc TEXT, obs TEXT)"
        )
        db.execSQL("CREATE INDEX idx_mixers_jornada ON mixers(jornada_id)")
        crearCatalogo(db)
    }

    private fun crearCatalogo(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS catalogo(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "frente TEXT NOT NULL, tramo TEXT, abscisa TEXT, modulo TEXT, orden INTEGER)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS elementos(_id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, orden INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 -> v2: solo se agrega la tabla del catalogo. Jornadas y mixers quedan intactos.
        if (oldVersion < 2) {
            crearCatalogo(db)
            db.execSQL("ALTER TABLE jornadas ADD COLUMN catalogo INTEGER DEFAULT 0")
        }
    }

    private fun Cursor.s(i: Int): String = getString(i) ?: ""

    fun jornadas(): List<Jornada> {
        val db = readableDatabase
        val mix = HashMap<String, MutableList<Mixer>>()
        db.rawQuery(
            "SELECT id, jornada_id, orden, codigo, llegada, inicio, fin, cant, as_planta, as_obra, temp, loc, obs " +
                "FROM mixers ORDER BY jornada_id, orden", null
        ).use { c ->
            while (c.moveToNext()) {
                val m = Mixer(
                    id = c.s(0), jornadaId = c.s(1), orden = c.getInt(2), codigo = c.s(3),
                    llegada = c.s(4), inicio = c.s(5), fin = c.s(6), cant = c.s(7),
                    asPlanta = c.s(8), asObra = c.s(9), temp = c.s(10), loc = c.s(11), obs = c.s(12)
                )
                mix.getOrPut(m.jornadaId) { mutableListOf() }.add(m)
            }
        }
        val out = ArrayList<Jornada>()
        db.rawQuery(
            "SELECT id, fecha, turno, nombre, frente, tramo, creada, catalogo FROM jornadas " +
                "ORDER BY fecha DESC, turno ASC, creada ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.s(0)
                out.add(
                    Jornada(
                        id = id, fecha = c.s(1), turno = c.s(2), nombre = c.s(3), frente = c.s(4),
                        tramo = c.s(5), creada = c.s(6), mixers = mix[id] ?: emptyList(),
                        usaCatalogo = c.getInt(7) == 1
                    )
                )
            }
        }
        return out
    }

    private fun valores(j: Jornada) = ContentValues().apply {
        put("id", j.id); put("fecha", j.fecha); put("turno", j.turno); put("nombre", j.nombre)
        put("frente", j.frente); put("tramo", j.tramo); put("creada", j.creada)
        put("catalogo", if (j.usaCatalogo) 1 else 0)
    }

    private fun valores(m: Mixer) = ContentValues().apply {
        put("id", m.id); put("jornada_id", m.jornadaId); put("orden", m.orden); put("codigo", m.codigo)
        put("llegada", m.llegada); put("inicio", m.inicio); put("fin", m.fin); put("cant", m.cant)
        put("as_planta", m.asPlanta); put("as_obra", m.asObra); put("temp", m.temp)
        put("loc", m.loc); put("obs", m.obs)
    }

    fun guardarJornada(j: Jornada) {
        writableDatabase.insertWithOnConflict("jornadas", null, valores(j), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun eliminarJornada(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("mixers", "jornada_id = ?", arrayOf(id))
            db.delete("jornadas", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun siguienteOrden(jornadaId: String): Int =
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(orden), 0) + 1 FROM mixers WHERE jornada_id = ?", arrayOf(jornadaId)
        ).use { if (it.moveToFirst()) it.getInt(0) else 1 }

    fun guardarMixer(m: Mixer) {
        writableDatabase.insertWithOnConflict("mixers", null, valores(m), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun eliminarMixer(m: Mixer) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("mixers", "id = ?", arrayOf(m.id))
            db.execSQL(
                "UPDATE mixers SET orden = orden - 1 WHERE jornada_id = ? AND orden > ?",
                arrayOf<Any>(m.jornadaId, m.orden)
            )
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Restaura jornadas desde una copia de seguridad (reemplaza las que tengan el mismo id). */
    fun importar(lista: List<Jornada>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            lista.forEach { j ->
                db.insertWithOnConflict("jornadas", null, valores(j), SQLiteDatabase.CONFLICT_REPLACE)
                db.delete("mixers", "jornada_id = ?", arrayOf(j.id))
                j.mixers.forEach { db.insertWithOnConflict("mixers", null, valores(it), SQLiteDatabase.CONFLICT_REPLACE) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------- Catalogo de frentes y tramos ----------

    fun catalogo(): List<ItemCatalogo> {
        val out = ArrayList<ItemCatalogo>()
        readableDatabase.rawQuery(
            "SELECT frente, tramo, abscisa, modulo FROM catalogo ORDER BY orden, _id", null
        ).use { c ->
            while (c.moveToNext()) out.add(ItemCatalogo(c.s(0), c.s(1), c.s(2), c.s(3)))
        }
        return out
    }

    fun elementos(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT nombre FROM elementos ORDER BY orden, _id", null).use { c ->
            while (c.moveToNext()) out.add(c.s(0))
        }
        return out
    }

    fun hayCatalogo(): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM catalogo LIMIT 1", null).use { it.moveToFirst() } ||
            readableDatabase.rawQuery("SELECT 1 FROM elementos LIMIT 1", null).use { it.moveToFirst() }

    /** Reemplaza TODO el catalogo por la lista nueva (borra lo anterior primero). */
    fun reemplazarCatalogo(items: List<ItemCatalogo>, elementos: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("catalogo", null, null)
            db.delete("elementos", null, null)
            elementos.forEachIndexed { i, e ->
                db.insert("elementos", null, ContentValues().apply { put("nombre", e); put("orden", i) })
            }
            items.forEachIndexed { i, it ->
                val v = ContentValues().apply {
                    put("frente", it.frente); put("tramo", it.tramo)
                    put("abscisa", it.abscisa); put("modulo", it.modulo); put("orden", i)
                }
                db.insert("catalogo", null, v)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun borrarCatalogo() {
        writableDatabase.delete("catalogo", null, null)
        writableDatabase.delete("elementos", null, null)
    }
}
