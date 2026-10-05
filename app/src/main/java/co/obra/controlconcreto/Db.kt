package co.obra.controlconcreto

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Base de datos SQLite dentro del teléfono. Cada cambio se guarda al instante. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "concreto.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE jornadas(id TEXT PRIMARY KEY, fecha TEXT NOT NULL, turno TEXT NOT NULL, nombre TEXT, frente TEXT, tramo TEXT, creada TEXT, origen TEXT NOT NULL DEFAULT 'Mis datos')")
        db.execSQL("CREATE TABLE mixers(id TEXT PRIMARY KEY, jornada_id TEXT NOT NULL, orden INTEGER NOT NULL, codigo TEXT, llegada TEXT, inicio TEXT, fin TEXT, cant TEXT, as_planta TEXT, as_obra TEXT, temp TEXT, loc TEXT, obs TEXT)")
        db.execSQL("CREATE INDEX idx_mixers_jornada ON mixers(jornada_id)")
        db.execSQL("CREATE INDEX idx_jornadas_origen ON jornadas(origen)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) { db.execSQL("ALTER TABLE jornadas ADD COLUMN origen TEXT NOT NULL DEFAULT 'Mis datos'"); db.execSQL("CREATE INDEX IF NOT EXISTS idx_jornadas_origen ON jornadas(origen)") }
    }
    private fun Cursor.s(i: Int): String = getString(i) ?: ""
    fun jornadas(): List<Jornada> {
        val db=readableDatabase; val mix=HashMap<String,MutableList<Mixer>>()
        db.rawQuery("SELECT id,jornada_id,orden,codigo,llegada,inicio,fin,cant,as_planta,as_obra,temp,loc,obs FROM mixers ORDER BY jornada_id,orden",null).use { c -> while(c.moveToNext()) { val m=Mixer(c.s(0),c.s(1),c.getInt(2),c.s(3),c.s(4),c.s(5),c.s(6),c.s(7),c.s(8),c.s(9),c.s(10),c.s(11),c.s(12)); mix.getOrPut(m.jornadaId){mutableListOf()}.add(m) } }
        val out=ArrayList<Jornada>()
        db.rawQuery("SELECT id,fecha,turno,nombre,frente,tramo,creada,origen FROM jornadas ORDER BY fecha DESC,turno ASC,creada ASC",null).use { c -> while(c.moveToNext()) { val id=c.s(0); out.add(Jornada(id,c.s(1),c.s(2),c.s(3),c.s(4),c.s(5),c.s(6),c.s(7).ifBlank{Excel.ORIGEN_LOCAL},mix[id]?:emptyList())) } }
        return out
    }
    private fun valores(j:Jornada)=ContentValues().apply { put("id",j.id);put("fecha",j.fecha);put("turno",j.turno);put("nombre",j.nombre);put("frente",j.frente);put("tramo",j.tramo);put("creada",j.creada);put("origen",j.origen) }
    private fun valores(m:Mixer)=ContentValues().apply { put("id",m.id);put("jornada_id",m.jornadaId);put("orden",m.orden);put("codigo",m.codigo);put("llegada",m.llegada);put("inicio",m.inicio);put("fin",m.fin);put("cant",m.cant);put("as_planta",m.asPlanta);put("as_obra",m.asObra);put("temp",m.temp);put("loc",m.loc);put("obs",m.obs) }
    fun guardarJornada(j:Jornada){writableDatabase.insertWithOnConflict("jornadas",null,valores(j),SQLiteDatabase.CONFLICT_REPLACE)}
    fun eliminarJornada(id:String){val db=writableDatabase;db.beginTransaction();try{db.delete("mixers","jornada_id = ?",arrayOf(id));db.delete("jornadas","id = ?",arrayOf(id));db.setTransactionSuccessful()}finally{db.endTransaction()}}
    fun siguienteOrden(jornadaId:String):Int=readableDatabase.rawQuery("SELECT COALESCE(MAX(orden),0)+1 FROM mixers WHERE jornada_id = ?",arrayOf(jornadaId)).use{if(it.moveToFirst())it.getInt(0)else 1}
    fun guardarMixer(m:Mixer){writableDatabase.insertWithOnConflict("mixers",null,valores(m),SQLiteDatabase.CONFLICT_REPLACE)}
    fun eliminarMixer(m:Mixer){val db=writableDatabase;db.beginTransaction();try{db.delete("mixers","id = ?",arrayOf(m.id));db.execSQL("UPDATE mixers SET orden=orden-1 WHERE jornada_id=? AND orden>?",arrayOf<Any>(m.jornadaId,m.orden));db.setTransactionSuccessful()}finally{db.endTransaction()}}
    fun importar(lista:List<Jornada>){val db=writableDatabase;db.beginTransaction();try{lista.forEach{j->db.insertWithOnConflict("jornadas",null,valores(j),SQLiteDatabase.CONFLICT_REPLACE);db.delete("mixers","jornada_id = ?",arrayOf(j.id));j.mixers.forEach{db.insertWithOnConflict("mixers",null,valores(it),SQLiteDatabase.CONFLICT_REPLACE)}};db.setTransactionSuccessful()}finally{db.endTransaction()}}
}
