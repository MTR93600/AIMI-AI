package info.nightscout.androidaps.db;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import info.nightscout.androidaps.dana.comm.RecordTypes;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventReloadProfileSwitchData;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansUploader;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.utils.PercentageSplitter;

/**
 * This Helper contains all resource to provide a central DB management functionality. Only methods handling
 * data-structure (and not the DB content) should be contained in here (meaning DDL and not SQL).
 * <p>
 * This class can safely be called from Services, but should not call Services to avoid circular dependencies.
 * One major issue with this (right now) are the scheduled events, which are put into the service. Therefor all
 * direct calls to the corresponding methods (eg. resetDatabases) should be done by a central service.
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject VirtualPumpPlugin virtualPumpPlugin;
    @Inject OpenHumansUploader openHumansUploader;

    public static final String DATABASE_NAME = "AndroidAPSDb";
    public static final String DATABASE_EXTENDEDBOLUSES = "ExtendedBoluses";
    public static final String DATABASE_DANARHISTORY = "DanaRHistory";
    public static final String DATABASE_DBREQUESTS = "DBRequests";
    public static final String DATABASE_TDDS = "TDDs";

    private static final int DATABASE_VERSION = 13;

    public static Long earliestDataChange = null;

    private static final ScheduledExecutorService tempBasalsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemBasalsPost = null;

    private static final ScheduledExecutorService extendedBolusWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledExtendedBolusPost = null;

    private static final ScheduledExecutorService profileSwitchEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledProfileSwitchEventPost = null;

    private int oldVersion = 0;
    private int newVersion = 0;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        onCreate(getWritableDatabase(), getConnectionSource());
        //onUpgrade(getWritableDatabase(), getConnectionSource(), 1,1);
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            aapsLogger.info(LTag.DATABASE, "onCreate");
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
            TableUtils.createTableIfNotExists(connectionSource, TDD.class);
            TableUtils.createTableIfNotExists(connectionSource, InsightHistoryOffset.class);
            TableUtils.createTableIfNotExists(connectionSource, InsightBolusID.class);
            TableUtils.createTableIfNotExists(connectionSource, InsightPumpID.class);
            TableUtils.createTableIfNotExists(connectionSource, OmnipodHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, OHQueueItem.class);
            database.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_BOLUS_IDS + "\", " + System.currentTimeMillis() + " " +
                    "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_BOLUS_IDS + "\")");
            database.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_PUMP_IDS + "\", " + System.currentTimeMillis() + " " +
                    "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_PUMP_IDS + "\")");
        } catch (SQLException e) {
            aapsLogger.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;

            if (oldVersion < 7) {
                aapsLogger.info(LTag.DATABASE, "onUpgrade");
                TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
                TableUtils.dropTable(connectionSource, DbRequest.class, true);
                TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
                TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
                TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
                onCreate(database, connectionSource);
            } else if (oldVersion < 10) {
                TableUtils.createTableIfNotExists(connectionSource, InsightHistoryOffset.class);
                TableUtils.createTableIfNotExists(connectionSource, InsightBolusID.class);
                TableUtils.createTableIfNotExists(connectionSource, InsightPumpID.class);
                database.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_BOLUS_IDS + "\", " + System.currentTimeMillis() + " " +
                        "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_BOLUS_IDS + "\")");
                database.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_PUMP_IDS + "\", " + System.currentTimeMillis() + " " +
                        "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_PUMP_IDS + "\")");
            } else if (oldVersion < 11) {
                database.execSQL("UPDATE sqlite_sequence SET seq = " + System.currentTimeMillis() + " WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_BOLUS_IDS + "\"");
                database.execSQL("UPDATE sqlite_sequence SET seq = " + System.currentTimeMillis() + " WHERE name = \"" + DatabaseHelperInterface.Companion.DATABASE_INSIGHT_PUMP_IDS + "\"");
            }
            TableUtils.createTableIfNotExists(connectionSource, OHQueueItem.class);
        } catch (SQLException e) {
            aapsLogger.error("Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        aapsLogger.info(LTag.DATABASE, "Do nothing for downgrading...");
        aapsLogger.info(LTag.DATABASE, "oldVersion: {}, newVersion: {}", oldVersion, newVersion);
    }

    public int getOldVersion() {
        return oldVersion;
    }

    public int getNewVersion() {
        return newVersion;
    }

    public long size(String database) {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), database);
    }

    // --------------------- DB resets ---------------------

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.dropTable(connectionSource, DbRequest.class, true);
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
            TableUtils.dropTable(connectionSource, TDD.class, true);
            TableUtils.dropTable(connectionSource, OmnipodHistoryRecord.class, true);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
            TableUtils.createTableIfNotExists(connectionSource, TDD.class);
            TableUtils.createTableIfNotExists(connectionSource, OmnipodHistoryRecord.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        virtualPumpPlugin.setFakingStatus(true);
        scheduleTemporaryBasalChange();
        scheduleExtendedBolusChange();
        scheduleProfileSwitchChange();
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        rxBus.send(new EventRefreshOverview("resetDatabases", false));
                    }
                },
                3000
        );
    }

    public void resetTemporaryBasals() {
        try {
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        virtualPumpPlugin.setFakingStatus(false);
        scheduleTemporaryBasalChange();
    }

    public void resetExtededBoluses() {
        try {
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        scheduleExtendedBolusChange();
    }

    public void resetProfileSwitch() {
        try {
            TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        scheduleProfileSwitchChange();
    }

    public void resetTDDs() {
        try {
            TableUtils.dropTable(connectionSource, TDD.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TDD.class);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    // ------------------ getDao -------------------------------------------

    private Dao<DanaRHistoryRecord, String> getDaoDanaRHistory() throws SQLException {
        return getDao(DanaRHistoryRecord.class);
    }

    private Dao<TDD, String> getDaoTDD() throws SQLException {
        return getDao(TDD.class);
    }

    private Dao<DbRequest, String> getDaoDbRequest() throws SQLException {
        return getDao(DbRequest.class);
    }

    private Dao<TemporaryBasal, Long> getDaoTemporaryBasal() throws SQLException {
        return getDao(TemporaryBasal.class);
    }

    private Dao<ExtendedBolus, Long> getDaoExtendedBolus() throws SQLException {
        return getDao(ExtendedBolus.class);
    }

    private Dao<ProfileSwitch, Long> getDaoProfileSwitch() throws SQLException {
        return getDao(ProfileSwitch.class);
    }

    private Dao<InsightPumpID, Long> getDaoInsightPumpID() throws SQLException {
        return getDao(InsightPumpID.class);
    }

    private Dao<InsightBolusID, Long> getDaoInsightBolusID() throws SQLException {
        return getDao(InsightBolusID.class);
    }

    private Dao<InsightHistoryOffset, String> getDaoInsightHistoryOffset() throws SQLException {
        return getDao(InsightHistoryOffset.class);
    }

    private Dao<OmnipodHistoryRecord, Long> getDaoPodHistory() throws SQLException {
        return getDao(OmnipodHistoryRecord.class);
    }

    private Dao<OHQueueItem, Long> getDaoOpenHumansQueue() throws SQLException {
        return getDao(OHQueueItem.class);
    }

    public long roundDateToSec(long date) {
        long rounded = date - date % 1000;
        if (rounded != date)
            aapsLogger.debug(LTag.DATABASE, "Rounding " + date + " to " + rounded);
        return rounded;
    }

    // -------------------  TDD handling -----------------------
    public void createOrUpdateTDD(TDD tdd) {
        try {
            Dao<TDD, String> dao = getDaoTDD();
            dao.createOrUpdate(tdd);
            openHumansUploader.enqueueTotalDailyDose(tdd);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<TDD> getTDDs() {
        List<TDD> tddList;
        try {
            QueryBuilder<TDD, String> queryBuilder = getDaoTDD().queryBuilder();
            queryBuilder.orderBy("date", false);
            queryBuilder.limit(10L);
            PreparedQuery<TDD> preparedQuery = queryBuilder.prepare();
            tddList = getDaoTDD().query(preparedQuery);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
            tddList = new ArrayList<>();
        }
        return tddList;
    }

    public List<TDD> getAllTDDs() {
        try {
            return getDaoTDD().queryForAll();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    public List<TDD> getTDDsForLastXDays(int days) {
        List<TDD> tddList;
        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.DAY_OF_YEAR, (-1) * days);

        try {
            QueryBuilder<TDD, String> queryBuilder = getDaoTDD().queryBuilder();
            queryBuilder.orderBy("date", false);
            Where<TDD, String> where = queryBuilder.where();
            where.ge("date", gc.getTimeInMillis());
            PreparedQuery<TDD> preparedQuery = queryBuilder.prepare();
            tddList = getDaoTDD().query(preparedQuery);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
            tddList = new ArrayList<>();
        }
        return tddList;
    }

    // ------------- DbRequests handling -------------------

    public void create(DbRequest dbr) throws SQLException {
        getDaoDbRequest().create(dbr);
    }

    public int delete(DbRequest dbr) {
        try {
            return getDaoDbRequest().delete(dbr);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0;
    }

    public int deleteDbRequest(String nsClientId) {
        try {
            return getDaoDbRequest().deleteById(nsClientId);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0;
    }

    public void deleteDbRequestbyMongoId(String action, String id) {
        try {
            QueryBuilder<DbRequest, String> queryBuilder = getDaoDbRequest().queryBuilder();
            // By nsID
            Where where = queryBuilder.where();
            where.eq("_id", id).and().eq("action", action);
            queryBuilder.limit(10L);
            PreparedQuery<DbRequest> preparedQuery = queryBuilder.prepare();
            List<DbRequest> dbList = getDaoDbRequest().query(preparedQuery);
            for (DbRequest r : dbList) delete(r);
            // By nsClientID
            where = queryBuilder.where();
            where.eq("nsClientID", id).and().eq("action", action);
            queryBuilder.limit(10L);
            preparedQuery = queryBuilder.prepare();
            dbList = getDaoDbRequest().query(preparedQuery);
            for (DbRequest r : dbList) delete(r);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void deleteAllDbRequests() {
        try {
            TableUtils.clearTable(connectionSource, DbRequest.class);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public CloseableIterator getDbRequestIterator() {
        try {
            return getDaoDbRequest().closeableIterator();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
            return null;
        }
    }

    public static void updateEarliestDataChange(long newDate) {
        if (earliestDataChange == null) {
            earliestDataChange = newDate;
            return;
        }
        if (newDate < earliestDataChange) {
            earliestDataChange = newDate;
        }
    }

    // ----------------- DanaRHistory handling --------------------

    public void createOrUpdate(DanaRHistoryRecord record) {
        try {
            getDaoDanaRHistory().createOrUpdate(record);

            //If it is a TDD, store it for stats also.
            if (record.recordCode == RecordTypes.RECORD_TYPE_DAILY) {
                createOrUpdateTDD(new TDD(record.recordDate, record.recordDailyBolus, record.recordDailyBasal, 0));
            }

        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        List<DanaRHistoryRecord> historyList;
        try {
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = getDaoDanaRHistory().queryBuilder();
            queryBuilder.orderBy("recordDate", false);
            Where where = queryBuilder.where();
            where.eq("recordCode", type);
            queryBuilder.limit(200L);
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            historyList = getDaoDanaRHistory().query(preparedQuery);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
            historyList = new ArrayList<>();
        }
        return historyList;
    }

    // ------------ TemporaryBasal handling ---------------

    //return true if new record was created
    public boolean createOrUpdate(TemporaryBasal tempBasal) {
        try {
            TemporaryBasal old;
            tempBasal.date = roundDateToSec(tempBasal.date);

            if (tempBasal.source == Source.PUMP) {
                // check for changed from pump change in NS
                QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("pumpId", tempBasal.pumpId);
                PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
                List<TemporaryBasal> trList = getDaoTemporaryBasal().query(preparedQuery);
                if (trList.size() > 0) {
                    // do nothing, pump history record cannot be changed
                    aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: Already exists from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                    return false;
                }

                // search by date (in case its standard record that has become pump record)
                QueryBuilder<TemporaryBasal, Long> queryBuilder2 = getDaoTemporaryBasal().queryBuilder();
                Where where2 = queryBuilder2.where();
                where2.eq("date", tempBasal.date);
                PreparedQuery<TemporaryBasal> preparedQuery2 = queryBuilder2.prepare();
                List<TemporaryBasal> trList2 = getDaoTemporaryBasal().query(preparedQuery2);

                if (trList2.size() > 0) {
                    old = trList2.get(0);

                    old.copyFromPump(tempBasal);
                    old.source = Source.PUMP;

                    aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: Updated record with Pump Data : " + Source.getString(tempBasal.source) + " " + tempBasal.toString());

                    getDaoTemporaryBasal().update(old);
                    openHumansUploader.enqueueTemporaryBasal(old);

                    updateEarliestDataChange(tempBasal.date);
                    scheduleTemporaryBasalChange();

                    return false;
                }

                getDaoTemporaryBasal().create(tempBasal);
                openHumansUploader.enqueueTemporaryBasal(tempBasal);
                aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
            if (tempBasal.source == Source.NIGHTSCOUT) {
                old = getDaoTemporaryBasal().queryForId(tempBasal.date);
                if (old != null) {
                    if (!old.isAbsolute && tempBasal.isAbsolute) { // converted to absolute by "ns_sync_use_absolute"
                        // so far ignore, do not convert back because it may not be accurate
                        return false;
                    }
                    if (!old.isEqual(tempBasal)) {
                        long oldDate = old.date;
                        getDaoTemporaryBasal().delete(old); // need to delete/create because date may change too
                        old.copyFrom(tempBasal);
                        getDaoTemporaryBasal().create(old);
                        openHumansUploader.enqueueTemporaryBasal(old);
                        aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: Updating record by date from: " + Source.getString(tempBasal.source) + " " + old.toString());
                        updateEarliestDataChange(oldDate);
                        updateEarliestDataChange(old.date);
                        scheduleTemporaryBasalChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (tempBasal._id != null) {
                    QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", tempBasal._id);
                    PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
                    List<TemporaryBasal> trList = getDaoTemporaryBasal().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(tempBasal)) {
                            long oldDate = old.date;
                            getDaoTemporaryBasal().delete(old); // need to delete/create because date may change too
                            old.copyFrom(tempBasal);
                            getDaoTemporaryBasal().create(old);
                            openHumansUploader.enqueueTemporaryBasal(old);
                            aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: Updating record by _id from: " + Source.getString(tempBasal.source) + " " + old.toString());
                            updateEarliestDataChange(oldDate);
                            updateEarliestDataChange(old.date);
                            scheduleTemporaryBasalChange();
                            return true;
                        }
                    }
                }
                getDaoTemporaryBasal().create(tempBasal);
                openHumansUploader.enqueueTemporaryBasal(tempBasal);
                aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
            if (tempBasal.source == Source.USER) {
                getDaoTemporaryBasal().create(tempBasal);
                openHumansUploader.enqueueTemporaryBasal(tempBasal);
                aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(TemporaryBasal tempBasal) {
        try {
            getDaoTemporaryBasal().delete(tempBasal);
            openHumansUploader.enqueueTemporaryBasal(tempBasal, true);
            updateEarliestDataChange(tempBasal.date);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        scheduleTemporaryBasalChange();
    }

    public List<TemporaryBasal> getAllTemporaryBasals() {
        try {
            return getDaoTemporaryBasal().queryForAll();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        try {
            List<TemporaryBasal> tempbasals;
            QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            tempbasals = getDaoTemporaryBasal().query(preparedQuery);
            return tempbasals;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<TemporaryBasal>();
    }

    public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long from, long to, boolean ascending) {
        try {
            List<TemporaryBasal> tempbasals;
            QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.between("date", from, to);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            tempbasals = getDaoTemporaryBasal().query(preparedQuery);
            return tempbasals;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<TemporaryBasal>();
    }

    private void scheduleTemporaryBasalChange() {
        class PostRunnable implements Runnable {
            public void run() {
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempBasalChange");
                rxBus.send(new EventReloadTempBasalData());
                rxBus.send(new EventTempBasalChange());
                if (earliestDataChange != null)
                    rxBus.send(new EventNewHistoryData(earliestDataChange));
                earliestDataChange = null;
                scheduledTemBasalsPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemBasalsPost != null)
            scheduledTemBasalsPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTemBasalsPost = tempBasalsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    /*
    {
        "_id": "59232e1ddd032d04218dab00",
        "eventType": "Temp Basal",
        "duration": 60,
        "percent": -50,
        "created_at": "2017-05-22T18:29:57Z",
        "enteredBy": "AndroidAPS",
        "notes": "Basal Temp Start 50% 60.0 min",
        "NSCLIENT_ID": 1495477797863,
        "mills": 1495477797000,
        "mgdl": 194.5,
        "endmills": 1495481397000
    }
    */

    public void createTempBasalFromJsonIfNotExists(JSONObject trJson) {
        try {
            if (trJson.has("originalExtendedAmount")) { // extended bolus uploaded as temp basal
                ExtendedBolus extendedBolus = new ExtendedBolus(StaticInjector.Companion.getInstance())
                        .source(Source.NIGHTSCOUT)
                        .date(trJson.getLong("mills"))
                        .pumpId(trJson.has("pumpId") ? trJson.getLong("pumpId") : 0)
                        .durationInMinutes(trJson.getInt("duration"))
                        .insulin(trJson.getDouble("originalExtendedAmount"))
                        ._id(trJson.getString("_id"));
                // if faking found in NS, adapt AAPS to use it too
                if (!virtualPumpPlugin.getFakingStatus()) {
                    virtualPumpPlugin.setFakingStatus(true);
                    updateEarliestDataChange(0);
                    scheduleTemporaryBasalChange();
                }
                createOrUpdate(extendedBolus);
            } else if (trJson.has("isFakedTempBasal")) { // extended bolus end uploaded as temp basal end
                ExtendedBolus extendedBolus = new ExtendedBolus(StaticInjector.Companion.getInstance());
                extendedBolus.source = Source.NIGHTSCOUT;
                extendedBolus.date = trJson.getLong("mills");
                extendedBolus.pumpId = trJson.has("pumpId") ? trJson.getLong("pumpId") : 0;
                extendedBolus.durationInMinutes = 0;
                extendedBolus.insulin = 0;
                extendedBolus._id = trJson.getString("_id");
                // if faking found in NS, adapt AAPS to use it too
                if (!virtualPumpPlugin.getFakingStatus()) {
                    virtualPumpPlugin.setFakingStatus(true);
                    updateEarliestDataChange(0);
                    scheduleTemporaryBasalChange();
                }
                createOrUpdate(extendedBolus);
            } else {
                TemporaryBasal tempBasal = new TemporaryBasal(StaticInjector.Companion.getInstance())
                        .date(trJson.getLong("mills"))
                        .source(Source.NIGHTSCOUT)
                        .pumpId(trJson.has("pumpId") ? trJson.getLong("pumpId") : 0);
                if (trJson.has("duration")) {
                    tempBasal.durationInMinutes = trJson.getInt("duration");
                }
                if (trJson.has("percent")) {
                    tempBasal.percentRate = trJson.getInt("percent") + 100;
                    tempBasal.isAbsolute = false;
                }
                if (trJson.has("absolute")) {
                    tempBasal.absoluteRate = trJson.getDouble("absolute");
                    tempBasal.isAbsolute = true;
                }
                tempBasal._id = trJson.getString("_id");
                createOrUpdate(tempBasal);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception: " + trJson.toString(), e);
        }
    }

    public void deleteTempBasalById(String _id) {
        TemporaryBasal stored = findTempBasalById(_id);
        if (stored != null) {
            aapsLogger.debug(LTag.DATABASE, "TEMPBASAL: Removing TempBasal record from database: " + stored.toString());
            delete(stored);
            updateEarliestDataChange(stored.date);
            scheduleTemporaryBasalChange();
        }
    }

    public TemporaryBasal findTempBasalById(String _id) {
        try {
            QueryBuilder<TemporaryBasal, Long> queryBuilder = null;
            queryBuilder = getDaoTemporaryBasal().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            List<TemporaryBasal> list = getDaoTemporaryBasal().query(preparedQuery);

            if (list.size() != 1) {
                return null;
            } else {
                return list.get(0);
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }


    public TemporaryBasal findTempBasalByPumpId(Long pumpId) {
        try {
            QueryBuilder<TemporaryBasal, Long> queryBuilder = null;
            queryBuilder = getDaoTemporaryBasal().queryBuilder();
            queryBuilder.orderBy("date", false);
            Where where = queryBuilder.where();
            where.eq("pumpId", pumpId);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            List<TemporaryBasal> list = getDaoTemporaryBasal().query(preparedQuery);

            if (list.size() > 0)
                return list.get(0);
            else
                return null;

        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }


    // ------------ ExtendedBolus handling ---------------

    public boolean createOrUpdate(ExtendedBolus extendedBolus) {
        try {
            aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: createOrUpdate: " + Source.getString(extendedBolus.source) + " " + extendedBolus.log());

            ExtendedBolus old;
            extendedBolus.date = roundDateToSec(extendedBolus.date);

            if (extendedBolus.source == Source.PUMP) {
                // if pumpId == 0 do not check for existing pumpId
                // used with pumps without history
                // and insight where record as added first without pumpId
                // and then is record updated with pumpId
                if (extendedBolus.pumpId == 0) {
                    getDaoExtendedBolus().createOrUpdate(extendedBolus);
                    openHumansUploader.enqueueExtendedBolus(extendedBolus);
                } else {
                    QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("pumpId", extendedBolus.pumpId);
                    PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                    List<ExtendedBolus> trList = getDaoExtendedBolus().query(preparedQuery);
                    if (trList.size() > 1) {
                        aapsLogger.error("EXTENDEDBOLUS: Multiple records found for pumpId: " + extendedBolus.pumpId);
                        return false;
                    }
                    getDaoExtendedBolus().createOrUpdate(extendedBolus);
                    openHumansUploader.enqueueExtendedBolus(extendedBolus);
                }
                aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.log());
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
            if (extendedBolus.source == Source.NIGHTSCOUT) {
                old = getDaoExtendedBolus().queryForId(extendedBolus.date);
                if (old != null) {
                    if (!old.isEqual(extendedBolus)) {
                        long oldDate = old.date;
                        getDaoExtendedBolus().delete(old); // need to delete/create because date may change too
                        old.copyFrom(extendedBolus);
                        getDaoExtendedBolus().create(old);
                        aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: Updating record by date from: " + Source.getString(extendedBolus.source) + " " + old.log());
                        openHumansUploader.enqueueExtendedBolus(old);
                        updateEarliestDataChange(oldDate);
                        updateEarliestDataChange(old.date);
                        scheduleExtendedBolusChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (extendedBolus._id != null) {
                    QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", extendedBolus._id);
                    PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                    List<ExtendedBolus> trList = getDaoExtendedBolus().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(extendedBolus)) {
                            long oldDate = old.date;
                            getDaoExtendedBolus().delete(old); // need to delete/create because date may change too
                            old.copyFrom(extendedBolus);
                            getDaoExtendedBolus().create(old);
                            aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: Updating record by _id from: " + Source.getString(extendedBolus.source) + " " + old.log());
                            openHumansUploader.enqueueExtendedBolus(old);
                            updateEarliestDataChange(oldDate);
                            updateEarliestDataChange(old.date);
                            scheduleExtendedBolusChange();
                            return true;
                        }
                    }
                }
                getDaoExtendedBolus().create(extendedBolus);
                aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.log());
                openHumansUploader.enqueueExtendedBolus(extendedBolus);
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
            if (extendedBolus.source == Source.USER) {
                getDaoExtendedBolus().create(extendedBolus);
                aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.log());
                openHumansUploader.enqueueExtendedBolus(extendedBolus);
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return false;
    }

    public List<ExtendedBolus> getAllExtendedBoluses() {
        try {
            return getDaoExtendedBolus().queryForAll();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    public ExtendedBolus getExtendedBolusByPumpId(long pumpId) {
        try {
            return getDaoExtendedBolus().queryBuilder()
                    .where().eq("pumpId", pumpId)
                    .queryForFirst();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    public void delete(ExtendedBolus extendedBolus) {
        try {
            getDaoExtendedBolus().delete(extendedBolus);
            openHumansUploader.enqueueExtendedBolus(extendedBolus, true);
            updateEarliestDataChange(extendedBolus.date);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        scheduleExtendedBolusChange();
    }

    public List<ExtendedBolus> getExtendedBolusDataFromTime(long mills, boolean ascending) {
        try {
            List<ExtendedBolus> extendedBoluses;
            QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            extendedBoluses = getDaoExtendedBolus().query(preparedQuery);
            return extendedBoluses;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<ExtendedBolus>();
    }

    public List<ExtendedBolus> getExtendedBolusDataFromTime(long from, long to, boolean ascending) {
        try {
            List<ExtendedBolus> extendedBoluses;
            QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.between("date", from, to);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            extendedBoluses = getDaoExtendedBolus().query(preparedQuery);
            return extendedBoluses;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<ExtendedBolus>();
    }

    public void deleteExtendedBolusById(String _id) {
        ExtendedBolus stored = findExtendedBolusById(_id);
        if (stored != null) {
            aapsLogger.debug(LTag.DATABASE, "EXTENDEDBOLUS: Removing ExtendedBolus record from database: " + stored.toString());
            delete(stored);
            updateEarliestDataChange(stored.date);
            scheduleExtendedBolusChange();
        }
    }

    public ExtendedBolus findExtendedBolusById(String _id) {
        try {
            QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
            queryBuilder = getDaoExtendedBolus().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    /*
{
    "_id": "5924898d577eb0880e355337",
    "eventType": "Combo Bolus",
    "duration": 120,
    "splitNow": 0,
    "splitExt": 100,
    "enteredinsulin": 1,
    "relative": 1,
    "created_at": "2017-05-23T19:12:14Z",
    "enteredBy": "AndroidAPS",
    "NSCLIENT_ID": 1495566734628,
    "mills": 1495566734000,
    "mgdl": 106
}
     */

    public void createExtendedBolusFromJsonIfNotExists(JSONObject json) {
        ExtendedBolus extendedBolus = ExtendedBolus.createFromJson(StaticInjector.Companion.getInstance(), json);
        if (extendedBolus != null)
            createOrUpdate(extendedBolus);
    }

    private void scheduleExtendedBolusChange() {
        class PostRunnable implements Runnable {
            public void run() {
                aapsLogger.debug(LTag.DATABASE, "Firing EventExtendedBolusChange");
                rxBus.send(new EventReloadTreatmentData(new EventExtendedBolusChange()));
                if (earliestDataChange != null)
                    rxBus.send(new EventNewHistoryData(earliestDataChange));
                earliestDataChange = null;
                scheduledExtendedBolusPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledExtendedBolusPost != null)
            scheduledExtendedBolusPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledExtendedBolusPost = extendedBolusWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    // ---------------- ProfileSwitch handling ---------------

    public List<ProfileSwitch> getProfileSwitchData(long from, boolean ascending) {
        try {
            Dao<ProfileSwitch, Long> daoProfileSwitch = getDaoProfileSwitch();
            List<ProfileSwitch> profileSwitches;
            QueryBuilder<ProfileSwitch, Long> queryBuilder = daoProfileSwitch.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            queryBuilder.limit(100L);
            Where where = queryBuilder.where();
            where.ge("date", from);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            profileSwitches = daoProfileSwitch.query(preparedQuery);
            //add last one without duration
            ProfileSwitch last = getLastProfileSwitchWithoutDuration();
            if (last != null) {
                if (!isInList(profileSwitches, last))
                    profileSwitches.add(last);
            }
            return profileSwitches;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    boolean isInList(List<ProfileSwitch> profileSwitches, ProfileSwitch last) {
        for (ProfileSwitch ps : profileSwitches) {
            if (ps.isEqual(last)) return true;
        }
        return false;
    }

    public List<ProfileSwitch> getAllProfileSwitches() {
        try {
            return getDaoProfileSwitch().queryForAll();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    @Nullable
    private ProfileSwitch getLastProfileSwitchWithoutDuration() {
        try {
            Dao<ProfileSwitch, Long> daoProfileSwitch = getDaoProfileSwitch();
            List<ProfileSwitch> profileSwitches;
            QueryBuilder<ProfileSwitch, Long> queryBuilder = daoProfileSwitch.queryBuilder();
            queryBuilder.orderBy("date", false);
            queryBuilder.limit(1L);
            Where where = queryBuilder.where();
            where.eq("durationInMinutes", 0);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            profileSwitches = daoProfileSwitch.query(preparedQuery);
            if (profileSwitches.size() > 0)
                return profileSwitches.get(0);
            else
                return null;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    public List<ProfileSwitch> getProfileSwitchEventsFromTime(long mills, boolean ascending) {
        try {
            Dao<ProfileSwitch, Long> daoProfileSwitch = getDaoProfileSwitch();
            List<ProfileSwitch> profileSwitches;
            QueryBuilder<ProfileSwitch, Long> queryBuilder = daoProfileSwitch.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            queryBuilder.limit(100L);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            profileSwitches = daoProfileSwitch.query(preparedQuery);
            return profileSwitches;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public List<ProfileSwitch> getProfileSwitchEventsFromTime(long from, long to, boolean ascending) {
        try {
            Dao<ProfileSwitch, Long> daoProfileSwitch = getDaoProfileSwitch();
            List<ProfileSwitch> profileSwitches;
            QueryBuilder<ProfileSwitch, Long> queryBuilder = daoProfileSwitch.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            queryBuilder.limit(100L);
            Where where = queryBuilder.where();
            where.between("date", from, to);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            profileSwitches = daoProfileSwitch.query(preparedQuery);
            return profileSwitches;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public boolean createOrUpdate(ProfileSwitch profileSwitch) {
        try {
            ProfileSwitch old;
            profileSwitch.date = roundDateToSec(profileSwitch.date);

            if (profileSwitch.source == Source.NIGHTSCOUT) {
                old = getDaoProfileSwitch().queryForId(profileSwitch.date);
                if (old != null) {
                    if (!old.isEqual(profileSwitch)) {
                        profileSwitch.source = old.source;
                        profileSwitch.profileName = old.profileName; // preserver profileName to prevent multiple CPP extension
                        getDaoProfileSwitch().delete(old); // need to delete/create because date may change too
                        getDaoProfileSwitch().create(profileSwitch);
                        aapsLogger.debug(LTag.DATABASE, "PROFILESWITCH: Updating record by date from: " + Source.getString(profileSwitch.source) + " " + old.toString());
                        openHumansUploader.enqueueProfileSwitch(profileSwitch);
                        scheduleProfileSwitchChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (profileSwitch._id != null) {
                    QueryBuilder<ProfileSwitch, Long> queryBuilder = getDaoProfileSwitch().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", profileSwitch._id);
                    PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
                    List<ProfileSwitch> trList = getDaoProfileSwitch().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(profileSwitch)) {
                            getDaoProfileSwitch().delete(old); // need to delete/create because date may change too
                            old.copyFrom(profileSwitch);
                            getDaoProfileSwitch().create(old);
                            aapsLogger.debug(LTag.DATABASE, "PROFILESWITCH: Updating record by _id from: " + Source.getString(profileSwitch.source) + " " + old.toString());
                            openHumansUploader.enqueueProfileSwitch(old);
                            scheduleProfileSwitchChange();
                            return true;
                        }
                    }
                }
                // look for already added percentage from NS
                profileSwitch.profileName = PercentageSplitter.pureName(profileSwitch.profileName);
                getDaoProfileSwitch().create(profileSwitch);
                aapsLogger.debug(LTag.DATABASE, "PROFILESWITCH: New record from: " + Source.getString(profileSwitch.source) + " " + profileSwitch.toString());
                openHumansUploader.enqueueProfileSwitch(profileSwitch);
                scheduleProfileSwitchChange();
                return true;
            }
            if (profileSwitch.source == Source.USER) {
                getDaoProfileSwitch().create(profileSwitch);
                aapsLogger.debug(LTag.DATABASE, "PROFILESWITCH: New record from: " + Source.getString(profileSwitch.source) + " " + profileSwitch.toString());
                openHumansUploader.enqueueProfileSwitch(profileSwitch);
                scheduleProfileSwitchChange();
                return true;
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(ProfileSwitch profileSwitch) {
        try {
            getDaoProfileSwitch().delete(profileSwitch);
            openHumansUploader.enqueueProfileSwitch(profileSwitch, true);
            scheduleProfileSwitchChange();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    private void scheduleProfileSwitchChange() {
        class PostRunnable implements Runnable {
            public void run() {
                aapsLogger.debug(LTag.DATABASE, "Firing EventProfileNeedsUpdate");
                rxBus.send(new EventReloadProfileSwitchData());
                rxBus.send(new EventProfileNeedsUpdate());
                scheduledProfileSwitchEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledProfileSwitchEventPost != null)
            scheduledProfileSwitchEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledProfileSwitchEventPost = profileSwitchEventWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

 /*
{
    "_id":"592fa43ed97496a80da913d2",
    "created_at":"2017-06-01T05:20:06Z",
    "eventType":"Profile Switch",
    "profile":"2016 +30%",
    "units":"mmol",
    "enteredBy":"sony",
    "NSCLIENT_ID":1496294454309,
}
  */

    public void createProfileSwitchFromJsonIfNotExists(ActivePluginProvider activePluginProvider, NSUpload nsUpload, JSONObject trJson) {
        try {
            ProfileSwitch profileSwitch = new ProfileSwitch(StaticInjector.Companion.getInstance());
            profileSwitch.date = trJson.getLong("mills");
            if (trJson.has("duration"))
                profileSwitch.durationInMinutes = trJson.getInt("duration");
            profileSwitch._id = trJson.getString("_id");
            profileSwitch.profileName = trJson.getString("profile");
            profileSwitch.isCPP = trJson.has("CircadianPercentageProfile");
            profileSwitch.source = Source.NIGHTSCOUT;
            if (trJson.has("timeshift"))
                profileSwitch.timeshift = trJson.getInt("timeshift");
            if (trJson.has("percentage"))
                profileSwitch.percentage = trJson.getInt("percentage");
            if (trJson.has("profileJson"))
                profileSwitch.profileJson = trJson.getString("profileJson");
            else {
                ProfileInterface profileInterface = activePluginProvider.getActiveProfileInterface();
                ProfileStore store = profileInterface.getProfile();
                if (store != null) {
                    Profile profile = store.getSpecificProfile(profileSwitch.profileName);
                    if (profile != null) {
                        profileSwitch.profileJson = profile.getData().toString();
                        aapsLogger.debug(LTag.DATABASE, "Profile switch prefilled with JSON from local store");
                        // Update data in NS
                        nsUpload.updateProfileSwitch(profileSwitch);
                    } else {
                        aapsLogger.debug(LTag.DATABASE, "JSON for profile switch doesn't exist. Ignoring: " + trJson.toString());
                        return;
                    }
                } else {
                    aapsLogger.debug(LTag.DATABASE, "Store for profile switch doesn't exist. Ignoring: " + trJson.toString());
                    return;
                }
            }
            if (trJson.has("profilePlugin"))
                profileSwitch.profilePlugin = trJson.getString("profilePlugin");
            createOrUpdate(profileSwitch);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception: " + trJson.toString(), e);
        }
    }

    public void deleteProfileSwitchById(String _id) {
        ProfileSwitch stored = findProfileSwitchById(_id);
        if (stored != null) {
            aapsLogger.debug(LTag.DATABASE, "PROFILESWITCH: Removing ProfileSwitch record from database: " + stored.toString());
            delete(stored);
            scheduleProfileSwitchChange();
        }
    }

    public ProfileSwitch findProfileSwitchById(String _id) {
        try {
            QueryBuilder<ProfileSwitch, Long> queryBuilder = getDaoProfileSwitch().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            List<ProfileSwitch> list = getDaoProfileSwitch().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    // ---------------- Insight history handling ---------------

    public void createOrUpdate(InsightHistoryOffset offset) {
        try {
            getDaoInsightHistoryOffset().createOrUpdate(offset);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public InsightHistoryOffset getInsightHistoryOffset(String pumpSerial) {
        try {
            return getDaoInsightHistoryOffset().queryForId(pumpSerial);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    public void createOrUpdate(InsightBolusID bolusID) {
        try {
            getDaoInsightBolusID().createOrUpdate(bolusID);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public InsightBolusID getInsightBolusID(String pumpSerial, int bolusID, long timestamp) {
        try {
            return getDaoInsightBolusID().queryBuilder()
                    .where().eq("pumpSerial", pumpSerial)
                    .and().eq("bolusID", bolusID)
                    .and().between("timestamp", timestamp - 259200000, timestamp + 259200000)
                    .queryForFirst();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    public void createOrUpdate(InsightPumpID pumpID) {
        try {
            getDaoInsightPumpID().createOrUpdate(pumpID);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public InsightPumpID getPumpStoppedEvent(String pumpSerial, long before) {
        try {
            return getDaoInsightPumpID().queryBuilder()
                    .orderBy("timestamp", false)
                    .where().eq("pumpSerial", pumpSerial)
                    .and().in("eventType", "PumpStopped", "PumpPaused")
                    .and().lt("timestamp", before)
                    .queryForFirst();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

    // ---------------- Food handling ---------------

    // ---------------- PodHistory handling ---------------

    public void createOrUpdate(OmnipodHistoryRecord omnipodHistoryRecord) {
        try {
            getDaoPodHistory().createOrUpdate(omnipodHistoryRecord);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimeStamp(long from, boolean ascending) {
        try {
            Dao<OmnipodHistoryRecord, Long> daoPodHistory = getDaoPodHistory();
            List<OmnipodHistoryRecord> podHistories;
            QueryBuilder<OmnipodHistoryRecord, Long> queryBuilder = daoPodHistory.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            //queryBuilder.limit(100L);
            Where where = queryBuilder.where();
            where.ge("date", from);
            PreparedQuery<OmnipodHistoryRecord> preparedQuery = queryBuilder.prepare();
            podHistories = daoPodHistory.query(preparedQuery);
            return podHistories;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public OmnipodHistoryRecord findOmnipodHistoryRecordByPumpId(long pumpId) {
        try {
            Dao<OmnipodHistoryRecord, Long> daoPodHistory = getDaoPodHistory();
            QueryBuilder<OmnipodHistoryRecord, Long> queryBuilder = daoPodHistory.queryBuilder();
            queryBuilder.orderBy("date", false);
            Where<OmnipodHistoryRecord, Long> where = queryBuilder.where();
            where.eq("pumpId", pumpId);
            PreparedQuery<OmnipodHistoryRecord> preparedQuery = queryBuilder.prepare();
            return daoPodHistory.queryForFirst(preparedQuery);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

/*
    TODO implement again for database branch    // Copied from xDrip+
    String calculateDirection(BgReading bgReading) {
        // Rework to get bgreaings from internal DB and calculate on that base

        List<BgReading> bgReadingsList = MainApp.getDbHelper().getAllBgreadingsDataFromTime(bgReading.date - T.mins(10).msecs(), false);
        if (bgReadingsList == null || bgReadingsList.size() < 2)
            return "NONE";
        BgReading current = bgReadingsList.get(1);
        BgReading previous = bgReadingsList.get(0);

        if (bgReadingsList.get(1).date < bgReadingsList.get(0).date) {
            current = bgReadingsList.get(0);
            previous = bgReadingsList.get(1);
        }

        double slope;

        // Avoid division by 0
        if (current.date == previous.date)
            slope = 0;
        else
            slope = (previous.value - current.value) / (previous.date - current.date);

//        aapsLogger.error(LTag.GLUCOSE, "Slope is :" + slope + " delta " + (previous.value - current.value) + " date difference " + (current.date - previous.date));

        double slope_by_minute = slope * 60000;
        String arrow = "NONE";

        if (slope_by_minute <= (-3.5)) {
            arrow = "DoubleDown";
        } else if (slope_by_minute <= (-2)) {
            arrow = "SingleDown";
        } else if (slope_by_minute <= (-1)) {
            arrow = "FortyFiveDown";
        } else if (slope_by_minute <= (1)) {
            arrow = "Flat";
        } else if (slope_by_minute <= (2)) {
            arrow = "FortyFiveUp";
        } else if (slope_by_minute <= (3.5)) {
            arrow = "SingleUp";
        } else if (slope_by_minute <= (40)) {
            arrow = "DoubleUp";
        }
//        aapsLogger.error(LTag.GLUCOSE, "Direction set to: " + arrow);
        return arrow;
    }
*/
    // ---------------- Open Humans Queue handling ---------------

    public void clearOpenHumansQueue() {
        try {
            TableUtils.clearTable(connectionSource, OHQueueItem.class);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void createOrUpdate(OHQueueItem item) {
        try {
            getDaoOpenHumansQueue().createOrUpdate(item);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void removeAllOHQueueItemsWithIdSmallerThan(long id) {
        try {
            DeleteBuilder<OHQueueItem, Long> deleteBuilder = getDaoOpenHumansQueue().deleteBuilder();
            deleteBuilder.where().le("id", id);
            deleteBuilder.delete();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<OHQueueItem> getAllOHQueueItems(Long maxEntries) {
        try {
            return getDaoOpenHumansQueue()
                    .queryBuilder()
                    .orderBy("id", true)
                    .limit(maxEntries)
                    .query();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    public long getOHQueueSize() {
        try {
            return getDaoOpenHumansQueue().countOf();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0L;
    }

    public long getCountOfAllRows() {
        try {
            return getDaoExtendedBolus().countOf()
                    + getDaoProfileSwitch().countOf()
                    + getDaoTDD().countOf()
                    + getDaoTemporaryBasal().countOf();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0L;
    }
}
