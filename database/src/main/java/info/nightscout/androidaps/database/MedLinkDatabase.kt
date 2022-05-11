package info.nightscout.androidaps.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import info.nightscout.androidaps.database.daos.MedLinkDao
import info.nightscout.androidaps.database.entities.*

@Database(
    entities = [MedLinkConfig::class],
    exportSchema = true,
    version = MedLinkDatabase.VERSION
)
@TypeConverters(Converters::class)
internal abstract class MedLinkDatabase : RoomDatabase() {


    companion object {

        const val VERSION = 1
    //
    //     fun build(context: Context) =
    //         Room.databaseBuilder(
    //             context.applicationContext,
    //             MedLinkDatabase::class.java,
    //             "med_link_database.db"
    //         )
    //             .fallbackToDestructiveMigration()
    //             .build()
    }

    abstract val medLinkConfigDao: MedLinkDao
}
