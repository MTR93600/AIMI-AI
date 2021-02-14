package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule;

import org.joda.time.Duration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BasalScheduleTest {
    @Test
    public void testRateAt() {
        List<BasalScheduleEntry> entries = new ArrayList<>();
        entries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        entries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        entries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        entries.add(new BasalScheduleEntry(4.0d, Duration.standardHours(20)));
        BasalSchedule schedule = new BasalSchedule(entries);

        assertEquals(1.0d, schedule.rateAt(Duration.ZERO), 0.00001);
        assertEquals(1.0d, schedule.rateAt(Duration.standardHours(6).minus(Duration.standardSeconds(1))), 0.00001);
        assertEquals(2.0d, schedule.rateAt(Duration.standardHours(6)), 0.00001);
        assertEquals(2.0d, schedule.rateAt(Duration.standardHours(6).plus(Duration.standardMinutes(30))), 0.00001);
        assertEquals(2.0d, schedule.rateAt(Duration.standardHours(7).minus(Duration.standardSeconds(1))), 0.00001);
        assertEquals(3.0d, schedule.rateAt(Duration.standardHours(7)), 0.00001);
        assertEquals(3.0d, schedule.rateAt(Duration.standardHours(19)), 0.00001);
        assertEquals(3.0d, schedule.rateAt(Duration.standardHours(20).minus(Duration.standardSeconds(1))), 0.00001);
        assertEquals(4.0d, schedule.rateAt(Duration.standardHours(20)), 0.00001);
        assertEquals(4.0d, schedule.rateAt(Duration.standardHours(24).minus(Duration.standardSeconds(1))), 0.00001);
    }

    @Test
    public void testEquals() {
        List<BasalScheduleEntry> entries = new ArrayList<>();
        entries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        entries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        entries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        entries.add(new BasalScheduleEntry(4.0d, Duration.standardHours(20)));
        BasalSchedule schedule = new BasalSchedule(entries);

        List<BasalScheduleEntry> otherEntries = new ArrayList<>();
        otherEntries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        otherEntries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        otherEntries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        otherEntries.add(new BasalScheduleEntry(4.0d, Duration.standardHours(20)));
        BasalSchedule otherschedule = new BasalSchedule(otherEntries);

        assertEquals(schedule, otherschedule);
    }

    @Test
    public void testNotEquals() {
        List<BasalScheduleEntry> entries = new ArrayList<>();
        entries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        entries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        entries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        entries.add(new BasalScheduleEntry(4.0d, Duration.standardHours(20)));
        BasalSchedule schedule = new BasalSchedule(entries);

        List<BasalScheduleEntry> otherEntries = new ArrayList<>();
        otherEntries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        otherEntries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        otherEntries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        otherEntries.add(new BasalScheduleEntry(4.1d, Duration.standardHours(20)));
        BasalSchedule otherschedule = new BasalSchedule(otherEntries);

        assertNotEquals(schedule, otherschedule);
    }

    @Test
    public void testNotEquals2() {
        List<BasalScheduleEntry> entries = new ArrayList<>();
        entries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        entries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        entries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        entries.add(new BasalScheduleEntry(4.0d, Duration.standardHours(20)));
        BasalSchedule schedule = new BasalSchedule(entries);

        List<BasalScheduleEntry> otherEntries = new ArrayList<>();
        otherEntries.add(new BasalScheduleEntry(1.0d, Duration.ZERO));
        otherEntries.add(new BasalScheduleEntry(2.0d, Duration.standardHours(6)));
        otherEntries.add(new BasalScheduleEntry(3.0d, Duration.standardHours(7)));
        BasalSchedule otherschedule = new BasalSchedule(otherEntries);

        assertNotEquals(schedule, otherschedule);
    }

}