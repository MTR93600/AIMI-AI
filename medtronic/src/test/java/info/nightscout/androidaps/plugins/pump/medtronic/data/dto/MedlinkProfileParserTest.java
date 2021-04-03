package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import android.content.Context;

import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDateTime;
import org.joda.time.chrono.ISOChronology;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedLinkMedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by Dirceu on 03/02/21.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AAPSLogger.class})
public class MedlinkProfileParserTest {

    @Mock HasAndroidInjector injector;
    @Mock AndroidInjector inj;
    @Mock AAPSLogger aapsLogger;

    private String validProfile = "{\"dia\":\"6\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"10\"},{\"time\":\"2:00\",\"value\":\"11\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";

    @Mock RxBusWrapper rxBus;
    @Mock Context context;
    @Mock ResourceHelper resourceHelper;
    @Mock ActivePluginProvider activePlugin;
    @Mock SP sp;
    @Mock CommandQueueProvider commandQueue;
    @Mock MedLinkMedtronicUtil medtronicUtil;
    @Mock MedLinkMedtronicPumpStatus medtronicPumpStatus;
    @Mock MedLinkMedtronicHistoryData medtronicHistoryData;
    @Mock MedLinkServiceData rileyLinkServiceData;
    @Mock DateUtil dateUtil;
    @Mock ServiceTaskExecutor serviceTaskExecutor;
    @Mock Profile profile;
    @Mock ISOChronology iso;
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    //    @InjectMocks MedLinkMedtronicPumpPlugin plugin;
    @Mock PumpEnactResult result;

    private LocalDateTime buildTime() {
        return new LocalDateTime(2020, 8, 10, 1, 00);

    }
    private MedLinkMedtronicPumpPlugin buildPlugin(){
        LocalDateTime time = buildTime();
        DateTimeUtils.setCurrentMillisFixed(1597064400000l);

        MedLinkMedtronicPumpPlugin plugin = new MedLinkMedtronicPumpPlugin(injector, aapsLogger,
                rxBus, context, resourceHelper, activePlugin, sp, commandQueue, null, medtronicUtil,
                medtronicPumpStatus, medtronicHistoryData, rileyLinkServiceData, serviceTaskExecutor, dateUtil) {
            @Override public LocalDateTime getCurrentTime() {
                return time;
            }

            @Override protected PumpEnactResult buildPumpEnactResult() {
                return result;
            }


        };
        PowerMockito.when(result.toString()).thenReturn("Mocked enactresult");
        Assert.assertNotNull("Plugin is null", plugin);
        return plugin;
    }

    @Test
    public void testTempBasalIncreaseOnePeriod() throws Exception {
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        MedLinkProfileParser parser = new MedLinkProfileParser(injector, aapsLogger, buildPlugin());
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
        parser.parseProfile(() -> Arrays.stream(profile.toLowerCase().split("\\n")));
    }
}
