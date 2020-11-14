package info.nightscout.androidaps.plugins.pump.medtronic;


import android.content.Context;

import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.chrono.ISOChronology;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusPair;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicrobolusOperations;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;


@RunWith(PowerMockRunner.class)
@PrepareForTest({MedLinkMedtronicPumpPlugin.class, AAPSLogger.class, RxBusWrapper.class,
        Context.class, ResourceHelper.class, android.util.Base64.class, ActivePluginProvider.class,
        SP.class, ISOChronology.class, DateTimeZone.class})
public class MedLinkMedtronicPumpPluginTest {

    private String validProfile = "{\"dia\":\"6\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"10\"},{\"time\":\"2:00\",\"value\":\"11\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";


    @Mock AAPSLogger aapsLogger;
    @Mock RxBusWrapper rxBus;
    @Mock Context context;
    @Mock ResourceHelper resourceHelper;
    @Mock ActivePluginProvider activePlugin;
    @Mock SP sp;
    @Mock CommandQueueProvider commandQueue;
    @Mock MedtronicUtil medtronicUtil;
    @Mock MedtronicPumpStatus medtronicPumpStatus;
    @Mock MedtronicHistoryData medtronicHistoryData;
    @Mock MedLinkServiceData rileyLinkServiceData;
    @Mock DateUtil dateUtil;
    @Mock ServiceTaskExecutor serviceTaskExecutor;
    @Mock Profile profile;
    @Mock ISOChronology iso;
    @Mock HasAndroidInjector injector;
    @Mock AndroidInjector inj;
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    //    @InjectMocks MedLinkMedtronicPumpPlugin plugin;
    @Mock PumpEnactResult result;
    //     HasAndroidInjector injector = new HasAndroidInjector() {
//         @Override public AndroidInjector<Object> androidInjector() {
//              new AndroidInjector {
//                 if (it is BgReading) {
//                     it.aapsLogger = aapsLogger;
//                     it.resourceHelper = resourceHelper;
//                     it.defaultValueHelper = defaultValueHelper;
//                     it.profileFunction = profileFunction
//                 }
//             }
//
//             return null;
//         }
//     }

    private LocalDateTime buildTime() {
        return new LocalDateTime(2020, 8, 10, 1, 00);

    }

    @Before
    public void buildResult(){
        String testComment = "MedlinkTest";
        PowerMockito.when(resourceHelper.gs(R.string.medtronic_cmd_desc_set_tbr)).thenReturn(testComment);
        PowerMockito.when(result.success(true)).thenReturn(result);
        PowerMockito.when(result.comment(testComment)).thenReturn(result);
    }

    private MedLinkMedtronicPumpPlugin buildPlugin(LocalDateTime time) {
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
    public void testInjector() {
        Assert.assertNotNull("Injector is null", injector);
    }

    @Test
    public void testTempBasalIncreaseOnePeriod() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[4];
        basalValues[0] = profile.new ProfileValue(7200, 0.5d);
        basalValues[1] = profile.new ProfileValue(14400, 1d);
        basalValues[2] = profile.new ProfileValue(3600, 1.5d);
        basalValues[3] = profile.new ProfileValue(14400, 2d);
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);

        plugin.setTempBasalPercent(150, 50, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 2 operations", 2, operations.operations.size());

        double totaldose = 0d;
        for (TempBasalMicroBolusPair pair : operations.operations) {
            totaldose += pair.getDose().doubleValue();
        }
        Assert.assertEquals("Total dosage should be 0.2ui", totaldose, 0.2, 0d);
        TempBasalMicroBolusPair[] op = new TempBasalMicroBolusPair[2];
        op[0] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time, TempBasalMicroBolusPair.OperationType.BOLUS);
        op[1] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(30), TempBasalMicroBolusPair.OperationType.BOLUS);
        Assert.assertArrayEquals("Operations should be 15mins of distance", operations.operations.toArray(), op);

    }


    @Test
    public void testTempBasalIncrease200PctOnePeriod() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[4];
        basalValues[0] = profile.new ProfileValue(7200, 0.5d);
        basalValues[1] = profile.new ProfileValue(14400, 1d);
        basalValues[2] = profile.new ProfileValue(3600, 1.5d);
        basalValues[3] = profile.new ProfileValue(14400, 2d);
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);

        plugin.setTempBasalPercent(200, 50, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 4 operations", 4, operations.operations.size());

        double totaldose = 0d;
        for (TempBasalMicroBolusPair pair : operations.operations) {
            totaldose += pair.getDose().doubleValue();
        }
        Assert.assertEquals("Total dosage should be 0.4ui", 0.4d, totaldose, 0d);
        Assert.assertEquals("Total size should be 4", 4, operations.operations.size());
        TempBasalMicroBolusPair[] op = new TempBasalMicroBolusPair[4];
        op[0] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time, TempBasalMicroBolusPair.OperationType.BOLUS);
        op[1] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(15), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[2] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(30), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[3] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(45), TempBasalMicroBolusPair.OperationType.BOLUS);
        Assert.assertArrayEquals("Operations should be 15mins of distance", op, operations.operations.toArray());

    }

    @Test
    public void testSameDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        List<Double> operationsList = plugin.buildOperations(10, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.1d);
        Assert.assertArrayEquals("Same doses and operations", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void testTwiceDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(20, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.2d);
        Assert.assertArrayEquals("Twice doses for each operation", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test40PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(4, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0d);
        doseEqualsOperations[0] = 0.1d;
        doseEqualsOperations[3] = 0.1d;
        doseEqualsOperations[6] = 0.1d;
        doseEqualsOperations[9] = 0.1d;
        Assert.assertArrayEquals("Twice doses for each operation", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test90PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(9, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.1d);
        doseEqualsOperations[5] = 0d;
        Assert.assertArrayEquals("90% microdoses", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test80PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(8, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.1d);
        doseEqualsOperations[2] = 0d;
        doseEqualsOperations[7] = 0d;
        Assert.assertArrayEquals("80% of spaces with bolus", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test70PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(7, 10, Collections.emptyList());
        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.1d);
        doseEqualsOperations[3] = 0d;
        doseEqualsOperations[6] = 0d;
        doseEqualsOperations[9] = 0d;
        Assert.assertArrayEquals("70% of spaces with bolus", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test60PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(6, 10, Collections.emptyList());

        Double[] doseEqualsOperations = new Double[10];
        Arrays.fill(doseEqualsOperations, 0.1d);
        doseEqualsOperations[1] = 0d;
        doseEqualsOperations[3] = 0d;
        doseEqualsOperations[6] = 0d;
        doseEqualsOperations[8] = 0d;
        Assert.assertArrayEquals("60% of spaces with bolus", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void test4728PctDoseOperations() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);

        List<Double> operationsList = plugin.buildOperations(47, 28, Collections.emptyList());

        Assert.assertEquals("Size should be 28", 28, operationsList.size());
        Double[] doseEqualsOperations = new Double[28];
        Arrays.fill(doseEqualsOperations, 0.2d);
        doseEqualsOperations[1] = 0.1d;
        doseEqualsOperations[4] = 0.1d;
        doseEqualsOperations[7] = 0.1d;
        doseEqualsOperations[10] = 0.1d;
        doseEqualsOperations[13] = 0.1d;
        doseEqualsOperations[16] = 0.1d;
        doseEqualsOperations[19] = 0.1d;
        doseEqualsOperations[22] = 0.1d;
        doseEqualsOperations[25] = 0.1d;
        Assert.assertArrayEquals("60% of spaces with bolus", doseEqualsOperations, operationsList.toArray());
    }

    @Test
    public void testTempBasalIncrease400PctOnePeriod() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[4];
        basalValues[0] = profile.new ProfileValue(7200, 0.5d);
        basalValues[1] = profile.new ProfileValue(14400, 1d);
        basalValues[2] = profile.new ProfileValue(3600, 1.5d);
        basalValues[3] = profile.new ProfileValue(14400, 2d);
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);

        plugin.setTempBasalPercent(310, 50, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 9 operations", 9, operations.operations.size());

        double totaldose = 0d;
        for (TempBasalMicroBolusPair pair : operations.operations) {
            totaldose += pair.getDose().doubleValue();
        }
        Assert.assertEquals("Total dosage should be 0.9ui", 0.9d, totaldose, 0.02d);

        TempBasalMicroBolusPair[] op = new TempBasalMicroBolusPair[9];
        op[0] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time, TempBasalMicroBolusPair.OperationType.BOLUS);
        op[1] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(5), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[2] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(10), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[3] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(15), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[4] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(20), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[5] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(30), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[6] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(35), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[7] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(40), TempBasalMicroBolusPair.OperationType.BOLUS);
        op[8] = new TempBasalMicroBolusPair(0, 0.1, 0.1, time.plusMinutes(45), TempBasalMicroBolusPair.OperationType.BOLUS);
        Assert.assertArrayEquals("Operations should be 15mins of distance", op, operations.operations.toArray());

    }

    @Test
    public void testTempBasalIncrease400PctTwoPeriods() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[4];
        basalValues[0] = profile.new ProfileValue(0, 0.5d);
        basalValues[1] = profile.new ProfileValue(4200, 1d);
        basalValues[2] = profile.new ProfileValue(13300, 1.5d);
        basalValues[3] = profile.new ProfileValue(14400, 2d);
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);

        plugin.setTempBasalPercent(300, 150, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 30 operations", 30, operations.operations.size());
        BigDecimal totalDose = operations.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("Total dosage should be 4.8ui", 4.8d, totalDose.doubleValue(), 0.05);

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(Comparator.comparing(
                LocalDateTime::toLocalTime)).get();
        Assert.assertEquals("Max operationtime should be 3:25", maxOperationTime, time.plusMinutes(145));

    }

    @Test
    public void testTempBasalIncrease400PctFourPeriods() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(300, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 30 operations", duration / 5, operations.operations.size());
        BigDecimal totalDose = operations.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("Total dosage should be 9.8ui", 9.8, totalDose.doubleValue(), 0.05);

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(Comparator.comparing(
                LocalDateTime::toLocalTime)).get();
        Assert.assertEquals("Max operationtime should be 3:25", maxOperationTime, time.plusMinutes(205));

    }


    @Test
    public void testTempBasalIncrease10PctFourPeriods() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(110, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 5 operations", 5, operations.operations.size());
        BigDecimal totalDose = operations.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("Total dosage should be 0.5ui", 0.5, totalDose.doubleValue(), 0.05);

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(Comparator.comparing(
                LocalDateTime::toLocalTime)).get();
        Assert.assertEquals("Max operationtime should be 4:00", time.plusMinutes(180), maxOperationTime);

    }

    @Test
    public void testTempBasalIncrease10PctTreePeriodsOverMidnight() throws Exception {
        LocalDateTime time = buildTime().plusHours(22);
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(110, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 4 operations", 4, operations.operations.size());
        BigDecimal totalDose = operations.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("Total dosage should be 0.4ui", 0.4d, totalDose.doubleValue(), 0.05);

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 2:10", maxOperationTime, time.plusMinutes(190));

    }

    @Test
    public void testTempBasalIncrease10PctOnePeriodOverMidnight() throws Exception {
        LocalDateTime time = buildTime().plusHours(22);
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[1];
        basalValues[0] = profile.new ProfileValue(0, 0.5d);

        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(110, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 2 operations", 2, operations.operations.size());
        BigDecimal totalDose = operations.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("Total dosage should be 0.4ui", 0.2d, totalDose.doubleValue(), 0.05);

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 1:10", time.plusMinutes(130), maxOperationTime);

    }


    @Test
    public void testClearTempBasalValues() throws Exception {
        LocalDateTime time = buildTime().plusHours(22);
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(110, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 4 operations", 4, operations.operations.size());
        plugin.setTempBasalPercent(100, 100, profile, true);
        Assert.assertEquals("Need to have no operations", 0, operations.operations.size());

    }

    @Test
    public void testTempBasalDecrease50Percent() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(50, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 42 operations", 42, operations.operations.size());

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 3:25", time.plusMinutes(205), maxOperationTime);

    }

    @Test
    public void testTempBasalDecrease10Percent() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 210;
        plugin.setTempBasalPercent(90, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 8 operations", 8, operations.operations.size());

        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 3:50", time.plusMinutes(155), maxOperationTime);

    }

    @Test
    public void testTempBasalDecrease10Percent3Minutes() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 3;
        plugin.setTempBasalPercent(90, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 0 operations", 0, operations.operations.size());
    }

    @Test
    public void testTempBasalDecrease10Percent38Minutes() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 38;
        plugin.setTempBasalPercent(90, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 0 operations", 0, operations.operations.size());
    }

    @Test
    public void testTempBasalDecrease20Percent38Minutes() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 38;
        plugin.setTempBasalPercent(80, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 0 operations", 4, operations.operations.size());
        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 3:50", time.plusMinutes(20), maxOperationTime);

    }

    @Test
    public void testTempBasalDecrease90Percent38Minutes() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 38;
        plugin.setTempBasalPercent(10, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 2 operations", 2, operations.operations.size());
        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 1:35", time.plusMinutes(35), maxOperationTime);

    }

    @Test
    public void testTempBasalDecrease90Percent60Minutes() throws Exception {
        LocalDateTime time = buildTime();
        MedLinkMedtronicPumpPlugin plugin = buildPlugin(time);
        JSONObject json = new JSONObject(validProfile);
        PowerMockito.when(injector, "androidInjector").thenReturn((inj));
        PowerMockito.doNothing().when(inj, "inject", any(PumpEnactResult.class));
        PowerMockito.when(profile.getBasal()).thenReturn(0.1);

        Profile.ProfileValue[] basalValues = buildBasalProfile();
        PowerMockito.when(profile.getBasalValues()).thenReturn(basalValues);
        Assert.assertNotNull("Profile is null", profile);
        int duration = 60;
        plugin.setTempBasalPercent(17, duration, profile, true);
        TempBasalMicrobolusOperations operations = plugin.getTempBasalMicrobolusOperations();
        Assert.assertNotNull("operations is null", operations);
        Assert.assertEquals("Need to have 2 operations", 4, operations.operations.size());
        LocalDateTime maxOperationTime = operations.operations.stream().map(
                TempBasalMicroBolusPair::getReleaseTime).max(
                LocalDateTime::compareTo).get();
        Assert.assertEquals("Max operationtime should be 1:35", time.plusMinutes(55), maxOperationTime);

    }


    private Profile.ProfileValue[] buildBasalProfile() {
        Profile.ProfileValue[] basalValues = new Profile.ProfileValue[4];
        basalValues[0] = profile.new ProfileValue(0, 0.5d);
        basalValues[1] = profile.new ProfileValue(4200, 1d);
        basalValues[2] = profile.new ProfileValue(7200, 1.5d);
        basalValues[3] = profile.new ProfileValue(14400, 2d);
        return basalValues;
    }
}

//TODO fazer teste com periodos com baixa dosagem e veja onde a primeira aplicação se encaixa
//TODO fazer teste com um periodo e crossmidnight
