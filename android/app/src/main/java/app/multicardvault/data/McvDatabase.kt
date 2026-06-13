package app.multicardvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VaultEntity::class,
        CardInventoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class McvDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    abstract fun cardInventoryDao(): CardInventoryDao

    companion object {
        fun open(context: Context): McvDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    McvDatabase::class.java,
                    "mcv.db",
                ).addMigrations(MIGRATION_2_3)
                .build()

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `card_inventory` (
                            `id` TEXT NOT NULL,
                            `vaultIdHex` TEXT NOT NULL,
                            `schemeIdHex` TEXT NOT NULL,
                            `shareIndex` INTEGER NOT NULL,
                            `threshold` INTEGER NOT NULL,
                            `total` INTEGER NOT NULL,
                            `label` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `firstSeenAt` INTEGER NOT NULL,
                            `lastSeenAt` INTEGER NOT NULL,
                            `lastCheckMessage` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS
                            `index_card_inventory_vaultIdHex_schemeIdHex_shareIndex`
                        ON `card_inventory` (`vaultIdHex`, `schemeIdHex`, `shareIndex`)
                        """.trimIndent(),
                    )
                }
            }
    }
}
