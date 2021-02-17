package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;

/**
 * Created by Dirceu on 03/02/21.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AAPSLogger.class})
public class MedlinkProfileParserTest {

    @Mock HasAndroidInjector injector;
    @Mock AndroidInjector inj;
    @Mock AAPSLogger aapsLogger;

    @Test
    public void testTempBasalIncreaseOnePeriod() throws Exception {
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        MedLinkProfileParser parser = new MedLinkProfileParser(injector, aapsLogger);
        String profile = "Bolus wizard settings:\n" +
                "Max. bolus: 15.0u\n" +
                "Easy bolus step: 0.1u\n" +
                "Carb ratios:\n" +
                "Rate 1: 12gr/u from 00:00\n" +
                "Rate 2: 09gr/u from 16:00\n" +
                "Rate 3: 10gr/u from 18:00\n" +
                "Insulin sensitivities:\n" +
                "Rate 1:  40mg/dl from 00:00\n" +
                "Rate 2:  30mg/dl from 09:00\n" +
                "Rate 3:  40mg/dl from 18:00\n" +
                "BG targets:\n" +
                "Rate 1: 70‑140 from 00:00\n" +
                "Rate 2: 70‑120 from 06:00\n" +
                "Rate 3: 70‑140 from 10:00\n" +
                "Rate 4: 70‑120 from 15:00\n" +
                "Rate 5: 70‑140 from 21:00\n" +
                "Ready\n";


        BasalProfile basalProfile = new BasalProfile(aapsLogger);
        List<BasalProfileEntry> list = new ArrayList<>();
        list.add(new MedLinkBasalProfileEntry(0.3, 0, 30));
        list.add(new MedLinkBasalProfileEntry(0.5, 10, 30));
        basalProfile.listEntries = list ;
        parser.parseProfile(new MedLinkStandardReturn<>(() -> Arrays.stream(profile.toLowerCase().split("\\n")), basalProfile));
    }
}
