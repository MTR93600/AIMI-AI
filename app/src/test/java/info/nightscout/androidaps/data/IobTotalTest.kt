package info.nightscout.androidaps.data

import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class IobTotalTest {

    var now = DateUtil.now()
    @Test fun copytest() {
        val a = IobTotal(now)
        a.iob = 10.0
        val b = a.copy()
        Assert.assertEquals(a.iob, b.iob, 0.01)
    }

    @Test fun plusTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 10.0
        a.bolussnooze = 10.0
        a.basaliob = 10.0
        a.netbasalinsulin = 10.0
        a.hightempinsulin = 10.0
        a.netInsulin = 10.0
        a.extendedBolusInsulin = 10.0
        a.plus(a.copy())
        Assert.assertEquals(20.0, a.iob, 0.01)
        Assert.assertEquals(20.0, a.activity, 0.01)
        Assert.assertEquals(20.0, a.bolussnooze, 0.01)
        Assert.assertEquals(20.0, a.basaliob, 0.01)
        Assert.assertEquals(20.0, a.netbasalinsulin, 0.01)
        Assert.assertEquals(20.0, a.hightempinsulin, 0.01)
        Assert.assertEquals(20.0, a.netInsulin, 0.01)
        Assert.assertEquals(20.0, a.extendedBolusInsulin, 0.01)
    }

    @Test fun combineTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        val b = a.copy()
        val c = IobTotal.combine(a, b)
        Assert.assertEquals(a.time.toDouble(), c.time.toDouble(), 0.01)
        Assert.assertEquals(23.0, c.iob, 0.01)
        Assert.assertEquals(22.0, c.activity, 0.01)
        Assert.assertEquals(12.0, c.bolussnooze, 0.01)
        Assert.assertEquals(26.0, c.basaliob, 0.01)
        Assert.assertEquals(28.0, c.netbasalinsulin, 0.01)
        Assert.assertEquals(30.0, c.hightempinsulin, 0.01)
        Assert.assertEquals(32.0, c.netInsulin, 0.01)
        Assert.assertEquals(34.0, c.extendedBolusInsulin, 0.01)
    }

    @Test fun roundTest() {
        val a = IobTotal(now)
        a.iob = 1.1111111111111
        a.activity = 1.1111111111111
        a.bolussnooze = 1.1111111111111
        a.basaliob = 1.1111111111111
        a.netbasalinsulin = 1.1111111111111
        a.hightempinsulin = 1.1111111111111
        a.netInsulin = 1.1111111111111
        a.extendedBolusInsulin = 1.1111111111111
        a.round()
        Assert.assertEquals(1.111, a.iob, 0.00001)
        Assert.assertEquals(1.1111, a.activity, 0.00001)
        Assert.assertEquals(1.1111, a.bolussnooze, 0.00001)
        Assert.assertEquals(1.111, a.basaliob, 0.00001)
        Assert.assertEquals(1.111, a.netbasalinsulin, 0.00001)
        Assert.assertEquals(1.111, a.hightempinsulin, 0.00001)
        Assert.assertEquals(1.111, a.netInsulin, 0.00001)
        Assert.assertEquals(1.111, a.extendedBolusInsulin, 0.00001)
    }

    @Test fun jsonTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        try {
            val j = a.json()
            Assert.assertEquals(a.iob, j.getDouble("iob"), 0.0000001)
            Assert.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001)
            Assert.assertEquals(a.activity, j.getDouble("activity"), 0.0000001)
            Assert.assertEquals(now.toFloat(), DateUtil.fromISODateString(j.getString("time")).time.toFloat(), 1000f)
        } catch (e: Exception) {
            Assert.fail("Exception: " + e.message)
        }
    }

    @Test fun determineBasalJsonTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        a.iobWithZeroTemp = IobTotal(now)
        try {
            val j = a.determineBasalJson()
            Assert.assertEquals(a.iob, j.getDouble("iob"), 0.0000001)
            Assert.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001)
            Assert.assertEquals(a.bolussnooze, j.getDouble("bolussnooze"), 0.0000001)
            Assert.assertEquals(a.activity, j.getDouble("activity"), 0.0000001)
            Assert.assertEquals(0, j.getLong("lastBolusTime"))
            Assert.assertEquals(now.toFloat(), DateUtil.fromISODateString(j.getString("time")).time.toFloat(), 1000f)
            Assert.assertNotNull(j.getJSONObject("iobWithZeroTemp"))
        } catch (e: Exception) {
            Assert.fail("Exception: " + e.message)
        }
    }
}