package info.nightscout.database.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import info.nightscout.androidaps.database.daos.MedLinkDao
import info.nightscout.database.entities.*
import info.nightscout.database.impl.daos.APSResultDao
import info.nightscout.database.impl.daos.APSResultLinkDao
import info.nightscout.database.impl.daos.BolusCalculatorResultDao
import info.nightscout.database.impl.daos.BolusDao
import info.nightscout.database.impl.daos.CarbsDao
import info.nightscout.database.impl.daos.DeviceStatusDao
import info.nightscout.database.impl.daos.EffectiveProfileSwitchDao
import info.nightscout.database.impl.daos.ExtendedBolusDao
import info.nightscout.database.impl.daos.FoodDao
import info.nightscout.database.impl.daos.GlucoseValueDao
import info.nightscout.database.impl.daos.MultiwaveBolusLinkDao
import info.nightscout.database.impl.daos.OfflineEventDao
import info.nightscout.database.impl.daos.PreferenceChangeDao
import info.nightscout.database.impl.daos.ProfileSwitchDao
import info.nightscout.database.impl.daos.TemporaryBasalDao
import info.nightscout.database.impl.daos.TemporaryTargetDao
import info.nightscout.database.impl.daos.TherapyEventDao
import info.nightscout.database.impl.daos.TotalDailyDoseDao
import info.nightscout.database.impl.daos.UserEntryDao
import info.nightscout.database.impl.daos.VersionChangeDao

const val DATABASE_VERSION = 23

@Database(version = DATABASE_VERSION,
          entities = [APSResult::class, Bolus::class, BolusCalculatorResult::class, Carbs::class,
        EffectiveProfileSwitch::class, ExtendedBolus::class, GlucoseValue::class, ProfileSwitch::class,
        TemporaryBasal::class, TemporaryTarget::class, TherapyEvent::class, TotalDailyDose::class, APSResultLink::class,
        MultiwaveBolusLink::class, PreferenceChange::class, VersionChange::class, UserEntry::class,
        Food::class, DeviceStatus::class, OfflineEvent::class, MedLinkConfig::class],
          exportSchema = true)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {

    abstract val glucoseValueDao: GlucoseValueDao

    abstract val therapyEventDao: TherapyEventDao

    abstract val temporaryBasalDao: TemporaryBasalDao

    abstract val bolusDao: BolusDao

    abstract val extendedBolusDao: ExtendedBolusDao

    abstract val multiwaveBolusLinkDao: MultiwaveBolusLinkDao

    abstract val totalDailyDoseDao: TotalDailyDoseDao

    abstract val carbsDao: CarbsDao

    abstract val temporaryTargetDao: TemporaryTargetDao

    abstract val apsResultLinkDao: APSResultLinkDao

    abstract val bolusCalculatorResultDao: BolusCalculatorResultDao

    abstract val effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    abstract val profileSwitchDao: ProfileSwitchDao

    abstract val apsResultDao: APSResultDao

    abstract val versionChangeDao: VersionChangeDao

    abstract val userEntryDao: UserEntryDao

    abstract val preferenceChangeDao: PreferenceChangeDao

    abstract val foodDao: FoodDao

    abstract val deviceStatusDao: DeviceStatusDao

    abstract val offlineEventDao: OfflineEventDao

    abstract val medLinkDao: MedLinkDao

}