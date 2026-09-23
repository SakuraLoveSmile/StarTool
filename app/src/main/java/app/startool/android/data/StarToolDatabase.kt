package app.startool.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HourlyEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StarToolDatabase : RoomDatabase() {

    abstract fun hourlyEntryDao(): HourlyEntryDao

    companion object {
        private const val DB_NAME = "startool.db"

        @Volatile
        private var INSTANCE: StarToolDatabase? = null

        fun getInstance(context: Context): StarToolDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StarToolDatabase::class.java,
                    DB_NAME,
                )
                    // 禁止 fallbackToDestructiveMigration
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
