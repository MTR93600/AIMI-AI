package info.nightscout.androidaps.plugins.pump.omnipod.eros.manager

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FirmwareVersion
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class AapsErosPodStateManagerTest : TestBase() {

    @Mock lateinit var sp: SP

    private val rxBus = RxBus(TestAapsSchedulers(), aapsLogger)

    @Test fun times() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x0)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        Assert.assertEquals(now, podStateManager.time)
        Assert.assertEquals(
            Duration.standardHours(1)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @Test fun changeSystemTimeZoneWithoutChangingPodTimeZone() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x0)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        val newTimeZone = DateTimeZone.forOffsetHours(2)
        DateTimeZone.setDefault(newTimeZone)

        // The system time zone has been updated, but the pod session state's time zone hasn't
        // So the pods time should not have been changed
        Assert.assertEquals(now, podStateManager.time)
        Assert.assertEquals(
            Duration.standardHours(1)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @Test fun changeSystemTimeZoneAndChangePodTimeZone() {
        val timeZone = DateTimeZone.UTC
        DateTimeZone.setDefault(timeZone)
        val now = DateTime(2020, 1, 1, 1, 2, 3, timeZone)
        DateTimeUtils.setCurrentMillisFixed(now.millis)
        val podStateManager = AapsErosPodStateManager(aapsLogger, sp, rxBus)
        podStateManager.initState(0x0)
        podStateManager.setInitializationParameters(
            0, 0, FirmwareVersion(1, 1, 1),
            FirmwareVersion(2, 2, 2), timeZone, PodProgressStatus.ABOVE_FIFTY_UNITS
        )
        val newTimeZone = DateTimeZone.forOffsetHours(2)
        DateTimeZone.setDefault(newTimeZone)
        podStateManager.timeZone = newTimeZone

        // Both the system time zone have been updated
        // So the pods time should have been changed (to +2 hours)
        Assert.assertEquals(now.withZone(newTimeZone), podStateManager.time)
        Assert.assertEquals(
            Duration.standardHours(3)
                .plus(Duration.standardMinutes(2).plus(Duration.standardSeconds(3))),
            podStateManager.scheduleOffset
        )
    }

    @After fun tearDown() {
        DateTimeUtils.setCurrentMillisSystem()
    }
}