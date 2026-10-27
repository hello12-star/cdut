package com.yh.schedule.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_events")
data class CustomEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 开始日期 ISO yyyy-MM-dd */
    val date: String,
    /** 结束日期 ISO yyyy-MM-dd（支持跨天） */
    val endDate: String = "",
    /** 开始时间 HH:mm */
    val startTime: String = "08:00",
    /** 结束时间 HH:mm */
    val endTime: String = "09:00",
    /** 用户填写的备注 / 描述 */
    val note: String = "",
    /** SCHEDULE / DEADLINE */
    val type: String = "SCHEDULE",
    /** 自动生成的时间描述，如「第8周 周三 08:00」 */
    val remark: String = "",
)

@Entity(tableName = "exam_records")
data class ExamRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val course: String,
    val examDate: String,
    val timeRange: String,
    val room: String,
)

@Dao
interface CustomEventDao {
    @Query("SELECT * FROM custom_events ORDER BY date ASC")
    fun all(): Flow<List<CustomEventEntity>>

    @Query("SELECT * FROM custom_events WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun range(from: String, to: String): Flow<List<CustomEventEntity>>

    @Insert
    suspend fun insert(e: CustomEventEntity): Long

    @Update
    suspend fun update(e: CustomEventEntity)

    @Delete
    suspend fun delete(e: CustomEventEntity)

    @Query("DELETE FROM custom_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ExamRecordDao {
    @Query("SELECT * FROM exam_records ORDER BY examDate ASC")
    suspend fun all(): List<ExamRecordEntity>

    @Insert
    suspend fun insertAll(list: List<ExamRecordEntity>)

    @Query("DELETE FROM exam_records")
    suspend fun clear()
}

@Database(
    entities = [CustomEventEntity::class, ExamRecordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun customEventDao(): CustomEventDao
    abstract fun examRecordDao(): ExamRecordDao

    companion object {
        @Volatile private var inst: AppDb? = null

        /** v2 → v3：日程表新增 endDate / type / remark，保留已有数据 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_events ADD COLUMN endDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE custom_events ADD COLUMN type TEXT NOT NULL DEFAULT 'SCHEDULE'")
                db.execSQL("ALTER TABLE custom_events ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(context, AppDb::class.java, "yh_schedule.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                .also { inst = it }
        }
    }
}
