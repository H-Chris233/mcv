package app.multicardvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VaultEntity::class,
        DeviceSecretEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class McvDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        fun open(context: Context): McvDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    McvDatabase::class.java,
                    "mcv.db",
                ).build()
    }
}
