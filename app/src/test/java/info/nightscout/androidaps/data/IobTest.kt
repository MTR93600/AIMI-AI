package info.nightscout.androidaps.data

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class IobTest {

    @Test fun plusTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        a.plus(b)
        Assert.assertEquals(4.0, a.iobContrib, 0.01)
        Assert.assertEquals(6.0, a.activityContrib, 0.01)
    }

    @Test fun equalTest() {
        val a1 = Iob().iobContrib(1.0).activityContrib(2.0)
        val a2 = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        Assert.assertTrue(a1 == a1)
        Assert.assertTrue(a1 == a2)
        Assert.assertFalse(a1 == b)
        Assert.assertFalse(a1 == null)
        Assert.assertFalse(a1 == Any())
    }

    @Test fun hashCodeTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        Assert.assertNotEquals(0, a.hashCode().toLong())
    }
}