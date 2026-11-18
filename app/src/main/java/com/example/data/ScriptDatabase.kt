package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scripts")
data class ScriptEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val title: String,
  val category: String,
  val code: String,
  val updatedAt: Long = System.currentTimeMillis(),
  val isBuiltIn: Boolean = false,
)

@Dao
interface ScriptDao {
  @Query("SELECT * FROM scripts ORDER BY isBuiltIn DESC, updatedAt DESC, id ASC")
  fun observeAll(): Flow<List<ScriptEntity>>

  @Query("SELECT COUNT(*) FROM scripts")
  suspend fun count(): Int

  @Query("SELECT * FROM scripts WHERE id = :id LIMIT 1")
  suspend fun getById(id: Int): ScriptEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(script: ScriptEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(scripts: List<ScriptEntity>)

  @Query("DELETE FROM scripts WHERE id = :id")
  suspend fun deleteById(id: Int)
}

@Database(entities = [ScriptEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun scriptDao(): ScriptDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
              context.applicationContext,
              AppDatabase::class.java,
              "bloxlua.db",
            )
              .build()
              .also { instance = it }
        }
  }
}

class ScriptRepository(private val dao: ScriptDao) {
  val allScripts: Flow<List<ScriptEntity>> = dao.observeAll()

  suspend fun ensureSeeded() {
    if (dao.count() == 0) {
      dao.insertAll(SampleScripts.presets)
    } else {
      for (preset in SampleScripts.presets) {
        val existing = dao.getById(preset.id)
        if (existing == null || (existing.isBuiltIn && existing.updatedAt < preset.updatedAt)) {
          dao.upsert(preset)
        }
      }
    }
  }

  suspend fun saveScript(script: ScriptEntity): Int = dao.upsert(script).toInt()

  suspend fun deleteScript(id: Int) = dao.deleteById(id)
}
