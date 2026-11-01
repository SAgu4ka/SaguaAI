package ru.sagu4ka.rngai.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sectorCount: Int,
    /** Названия секторов через перевод строки; пусто = номера 0..K-1. */
    val sectorNames: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun names(): List<String> {
        val parts = sectorNames.split("\n").map { it.trim() }
        return List(sectorCount) { i -> parts.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: i.toString() }
    }
}

@Entity(
    tableName = "spins",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")]
)
data class SpinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val sector: Int,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface AppDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun profiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM spins WHERE profileId = :profileId ORDER BY id")
    fun spins(profileId: Long): Flow<List<SpinEntity>>

    @Query("SELECT * FROM spins WHERE profileId = :profileId ORDER BY id")
    suspend fun spinsOnce(profileId: Long): List<SpinEntity>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun profilesOnce(): List<ProfileEntity>

    @Insert suspend fun insertProfile(p: ProfileEntity): Long
    @Update suspend fun updateProfile(p: ProfileEntity)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: Long)

    @Insert suspend fun insertSpin(s: SpinEntity): Long
    @Insert suspend fun insertSpins(list: List<SpinEntity>)
    @Query("UPDATE spins SET sector = :sector WHERE id = :id") suspend fun setSector(id: Long, sector: Int)
    @Query("DELETE FROM spins WHERE id = :id") suspend fun deleteSpin(id: Long)
    @Query("DELETE FROM spins WHERE profileId = :profileId") suspend fun clearSpins(profileId: Long)
    @Query("DELETE FROM spins WHERE profileId = :profileId AND sector >= :k") suspend fun dropOutOfRange(profileId: Long, k: Int)
}

@Database(entities = [ProfileEntity::class, SpinEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "saguka.db").build().also { inst = it }
        }
    }
}
