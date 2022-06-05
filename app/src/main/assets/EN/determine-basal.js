/*
  Determine Basal

  Released under MIT license. See the accompanying LICENSE.txt file for
  full terms and conditions

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
*/


//var round_basal = require('../round-basal')

// Escape the text for < &lt; & > &gt;
function esc_text(text)
{
    return (text.replace("<", "&lt;").replace(">", "&gt;").replace("+B+", "<b>").replace("-B-", "</b>"));
}

// Fix the round_basal issue?
function round_basal(basal, profile)
{
    profile = 2; // force number of decimal places for the pump
    return round(basal, profile);
}

// Rounds value to 'digits' decimal places
function round(value, digits)
{
    if (! digits) { digits = 0; }
    var scale = Math.pow(10, digits);
    return Math.round(value * scale) / scale;
}

// we expect BG to rise or fall at the rate of BGI,
// adjusted by the rate at which BG would need to rise /
// fall to get eventualBG to target over 2 hours
function calculate_expected_delta(target_bg, eventual_bg, bgi) {
    // (hours * mins_per_hour) / 5 = how many 5 minute periods in 2h = 24
    var five_min_blocks = (2 * 60) / 5;
    var target_delta = target_bg - eventual_bg;
    return /* expectedDelta */ round(bgi + (target_delta / five_min_blocks), 1);
}

function convert_bg(value, profile)
{
    if (profile.out_units === "mmol/L")
    {
        return round(value / 18, 1).toFixed(1);
    }
    else
    {
        return Math.round(value);
    }
}

function enable_smb(
    profile,
    microBolusAllowed,
    meal_data,
    target_bg
) {
    // disable SMB when a high temptarget is set
    if (! microBolusAllowed) {
        console.error("SMB disabled (!microBolusAllowed)");
        return false;
    } else if (! profile.allowSMB_with_high_temptarget && profile.temptargetSet && target_bg > profile.normal_target_bg) {
        console.error("SMB disabled due to high temptarget of",target_bg);
        return false;
    } else if (meal_data.bwFound === true && profile.A52_risk_enable === false) {
        console.error("SMB disabled due to Bolus Wizard activity in the last 6 hours.");
        return false;
    }

    // enable SMB/UAM if always-on (unless previously disabled for high temptarget)
    if (profile.enableSMB_always === true) {
        if (meal_data.bwFound) {
            console.error("Warning: SMB enabled within 6h of using Bolus Wizard: be sure to easy bolus 30s before using Bolus Wizard");
        } else {
            console.error("SMB enabled due to enableSMB_always");
        }
        return true;
    }

    // enable SMB/UAM (if enabled in preferences) while we have COB
    if (profile.enableSMB_with_COB === true && meal_data.mealCOB) {
        if (meal_data.bwCarbs) {
            console.error("Warning: SMB enabled with Bolus Wizard carbs: be sure to easy bolus 30s before using Bolus Wizard");
        } else {
            console.error("SMB enabled for COB of",meal_data.mealCOB);
        }
        return true;
    }

    // enable SMB/UAM (if enabled in preferences) for a full 6 hours after any carb entry
    // (6 hours is defined in carbWindow in lib/meal/total.js)
    if (profile.enableSMB_after_carbs === true && meal_data.carbs ) {
        if (meal_data.bwCarbs) {
            console.error("Warning: SMB enabled with Bolus Wizard carbs: be sure to easy bolus 30s before using Bolus Wizard");
        } else {
            console.error("SMB enabled for 6h after carb entry");
        }
        return true;
    }

    // enable SMB/UAM (if enabled in preferences) if a low temptarget is set
    if (profile.enableSMB_with_temptarget === true && (profile.temptargetSet && target_bg < profile.normal_target_bg)) {
        if (meal_data.bwFound) {
            console.error("Warning: SMB enabled within 6h of using Bolus Wizard: be sure to easy bolus 30s before using Bolus Wizard");
        } else {
            console.error("SMB enabled for temptarget of",convert_bg(target_bg, profile));
        }
        return true;
    }

    console.error("SMB disabled (no enableSMB preferences active or no condition satisfied)");
    return false;
}

var determine_basal = function determine_basal(glucose_status, currenttemp, iob_data, profile, autosens_data, meal_data, tempBasalFunctions, microBolusAllowed, reservoir_data, currentTime, isSaveCgmSource) {
    var rT = {}; //short for requestedTemp

    var deliverAt = new Date();
    if (currentTime) {
        deliverAt = new Date(currentTime);
    }

    if (typeof profile === 'undefined' || typeof profile.current_basal === 'undefined') {
        rT.error ='Error: could not get current basal rate';
        return rT;
    }
    var profile_current_basal = round_basal(profile.current_basal, profile);
    var basal = profile_current_basal;

    var systemTime = new Date();
    if (currentTime) {
        systemTime = currentTime;
    }
    var bgTime = new Date(glucose_status.date);
    var minAgo = round( (systemTime - bgTime) / 60 / 1000 ,1);

    var bg = glucose_status.glucose;
    var noise = glucose_status.noise;
    // 38 is an xDrip error state that usually indicates sensor failure
    // all other BG values between 11 and 37 mg/dL reflect non-error-code BG values, so we should zero temp for those
    if (bg <= 10 || bg === 38 || noise >= 3) {  //Dexcom is in ??? mode or calibrating, or xDrip reports high noise
        rT.reason = "CGM is calibrating, in ??? state, or noise is high";
    }
    if (minAgo > 12 || minAgo < -5) { // Dexcom data is too old, or way in the future
        rT.reason = "If current system time "+systemTime+" is correct, then BG data is too old. The last BG data was read "+minAgo+"m ago at "+bgTime;
    // if BG is too old/noisy, or is changing less than 1 mg/dL/5m for 45m, cancel any high temps and shorten any long zero temps
    //cherry pick from oref upstream dev cb8e94990301277fb1016c778b4e9efa55a6edbc
    } else if ( bg > 60 && glucose_status.delta == 0 && glucose_status.short_avgdelta > -1 && glucose_status.short_avgdelta < 1 && glucose_status.long_avgdelta > -1 && glucose_status.long_avgdelta < 1 && !isSaveCgmSource) {
        if ( glucose_status.last_cal && glucose_status.last_cal < 3 ) {
            rT.reason = "CGM was just calibrated";
        } /*else {
            rT.reason = "Error: CGM data is unchanged for the past ~45m";
        }*/
    }
    //cherry pick from oref upstream dev cb8e94990301277fb1016c778b4e9efa55a6edbc
    if (bg <= 10 || bg === 38 || noise >= 3 || minAgo > 12 || minAgo < -5  ) {//|| ( bg > 60 && glucose_status.delta == 0 && glucose_status.short_avgdelta > -1 && glucose_status.short_avgdelta < 1 && glucose_status.long_avgdelta > -1 && glucose_status.long_avgdelta < 1 ) && !isSaveCgmSource
        if (currenttemp.rate > basal) { // high temp is running
            rT.reason += ". Replacing high temp basal of "+currenttemp.rate+" with neutral temp of "+basal;
            rT.deliverAt = deliverAt;
            rT.temp = 'absolute';
            rT.duration = 30;
            rT.rate = basal;
            return rT;
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        } else if ( currenttemp.rate === 0 && currenttemp.duration > 30 ) { //shorten long zero temps to 30m
            rT.reason += ". Shortening " + currenttemp.duration + "m long zero temp to 30m. ";
            rT.deliverAt = deliverAt;
            rT.temp = 'absolute';
            rT.duration = 30;
            rT.rate = 0;
            return rT;
            //return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
        } else { //do nothing.
            rT.reason += esc_text(". Temp " + round(currenttemp.rate,2) + " <= current basal " + basal + "U/hr; doing nothing. ");
            return rT;
        }
    }

    var max_iob = profile.max_iob; // maximum amount of non-bolus IOB OpenAPS will ever deliver

    // if min and max are set, then set target to their average
    var target_bg;
    var min_bg;
    var max_bg;
    if (typeof profile.min_bg !== 'undefined') {
        min_bg = profile.min_bg;
    }
    if (typeof profile.max_bg !== 'undefined') {
        max_bg = profile.max_bg;
    }
    if (typeof profile.min_bg !== 'undefined' && typeof profile.max_bg !== 'undefined') {
        target_bg = (profile.min_bg + profile.max_bg) / 2;
    } else {
        rT.error ='Error: could not determine target_bg. ';
        return rT;
    }

    var sensitivityRatio;
    var high_temptarget_raises_sensitivity = profile.exercise_mode || profile.high_temptarget_raises_sensitivity;
    var normalTarget = profile.normal_target_bg; // evaluate high/low temptarget against 100, not scheduled target (which might change)
    if ( profile.half_basal_exercise_target ) {
        var halfBasalTarget = profile.half_basal_exercise_target;
    } else {
        halfBasalTarget = 160; // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%)
        // 80 mg/dL with low_temptarget_lowers_sensitivity would give 1.5x basal, but is limited to autosens_max (1.2x by default)
    }
    /*
    if ( high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget
        || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget ) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        sensitivityRatio = sensitivityRatio * autosens_data.ratio; //now apply existing sensitivity or resistance
        // limit sensitivityRatio to profile.autosens_max
        sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+"; ");
    } else if (typeof autosens_data !== 'undefined' && autosens_data) {
        sensitivityRatio = autosens_data.ratio;
        console.log("Autosens ratio: "+sensitivityRatio+"; ");
    }
    */

    // Eating Now Variables, relocated for SR
    var ENactive = false, ENtimeOK = false, ENmaxIOBOK = false, enlog = "";
    //Create the time variable to be used to allow the EN to function only between certain hours
    var now = new Date(), nowdec = round(now.getHours()+now.getMinutes()/60,2), nowhrs = now.getHours(), nowmins = now.getMinutes();
    // calculate the epoch time for EN start and end applying an offset when end time is lower than start time
    var ENStartOffset = (profile.EatingNowTimeEnd < profile.EatingNowTimeStart && nowhrs < profile.EatingNowTimeEnd ? 86400000 : 0), ENEndOffset = (profile.EatingNowTimeEnd < profile.EatingNowTimeStart && nowhrs > profile.EatingNowTimeStart ? 86400000 : 0);
    var ENStartTime = new Date().setHours(profile.EatingNowTimeStart,0,0,0)-ENStartOffset, ENEndTime = new Date().setHours(profile.EatingNowTimeEnd,0,0,0) + ENEndOffset;
    var COB = meal_data.mealCOB;

    // variables for deltas
    var delta = glucose_status.delta, deltaShortRise = 0, DeltaPct = 1;
    // Calculate percentage change in delta, short to now
    if (glucose_status.short_avgdelta !=0) deltaShortRise = round((glucose_status.delta - glucose_status.short_avgdelta) / Math.abs(glucose_status.short_avgdelta),2);
    // set the DeltaPct factor that is the deltaShortRise combined minimum of zero + 1 to allow multiply
    DeltaPct = round(1+deltaShortRise,2);

    // eating now time can be delayed if there is no first bolus or carbs
    if (now >= ENStartTime && now < ENEndTime && (meal_data.lastNormalCarbTime >= ENStartTime || meal_data.lastBolusNormalTime >= ENStartTime)) ENtimeOK = true;
    var lastNormalCarbAge = round(( new Date(systemTime).getTime() - meal_data.lastNormalCarbTime ) / 60000);


    enlog += "nowhrs: " + nowhrs + ", now: " + now +"\n";
    enlog += "ENStartOffset: " + ENStartOffset + ", ENStartTime: " + ENStartTime +"\n";
    enlog += "ENEndOffset: " + ENEndOffset + ", ENEndTime: " + ENEndTime +"\n";
    enlog += "lastNormalCarbTime: " + meal_data.lastNormalCarbTime + ", lastBolusNormalTime: " + meal_data.lastBolusNormalTime +"\n";
    enlog += "lastNormalCarbAge: " + lastNormalCarbAge +"\n";

    /*
    // set sensitivityRatio to a minimum of 1 when EN active allowing resistance, and allow <1 overnight to allow sensitivity
    sensitivityRatio = (ENtimeOK && !profile.temptargetSet ? Math.max(sensitivityRatio,1) : sensitivityRatio);
    sensitivityRatio = (profile.use_sens_TDD && !profile.temptargetSet ? 1 : sensitivityRatio);
    sensitivityRatio = (!ENtimeOK && !profile.temptargetSet ? Math.min(sensitivityRatio,1) : sensitivityRatio);


    if (sensitivityRatio) {
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal !== profile_current_basal) {
            console.log("Adjusting basal from "+profile_current_basal+" to "+basal+"; ");
        } else {
            console.log("Basal unchanged: "+basal+"; ");
        }
    }

    // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
    if (profile.temptargetSet || ENtimeOK) {
        //console.log("Temp Target set, not adjusting with autosens; ");
    } else if (typeof autosens_data !== 'undefined' && autosens_data) {
        if ( profile.sensitivity_raises_target && autosens_data.ratio < 1 || profile.resistance_lowers_target && autosens_data.ratio > 1 ) {
            // with a target of 100, default 0.7-1.2 autosens min/max range would allow a 93-117 target range
            min_bg = round((min_bg - 60) / autosens_data.ratio) + 60;
            max_bg = round((max_bg - 60) / autosens_data.ratio) + 60;
            var new_target_bg = round((target_bg - 60) / autosens_data.ratio) + 60;
            // don't allow target_bg below 80
            new_target_bg = Math.max(80, new_target_bg);
            if (target_bg === new_target_bg) {
                console.log("target_bg unchanged: "+new_target_bg+"; ");
            } else {
                console.log("target_bg from "+target_bg+" to "+new_target_bg+"; ");
            }
            target_bg = new_target_bg;
        }
    }
    */

    if (typeof iob_data === 'undefined' ) {
        rT.error ='Error: iob_data undefined. ';
        return rT;
    }

    var iobArray = iob_data;
    if (typeof(iob_data.length) && iob_data.length > 1) {
        iob_data = iobArray[0];
        //console.error(JSON.stringify(iob_data[0]));
    }

    if (typeof iob_data.activity === 'undefined' || typeof iob_data.iob === 'undefined' ) {
        rT.error ='Error: iob_data missing some property. ';
        return rT;
    }

    // patches ==== START
    var ignoreCOB = profile.enableGhostCOB; //MD#01: Ignore any COB and rely purely on UAM after initial rise

    // Check that max iob is OK
    if (iob_data.iob <= max_iob) ENmaxIOBOK = true;

    // If we have UAM enabled with IOB less than max enable eating now mode
    if (profile.enableUAM && ENmaxIOBOK) {
        // enable eatingnow if no TT and within safe hours
        if (ENtimeOK) ENactive = true;
        // If there are COB enable eating now
        if (meal_data.mealCOB >0) ENactive = true;
        // no EN with a TT other than normal target
        if (profile.temptargetSet) ENactive = false;
        if (profile.temptargetSet && target_bg <= normalTarget) ENactive = true;
    }
    //ENactive = false; //DEBUG
    enlog += "ENactive: " + ENactive + ", ENtimeOK: " + ENtimeOK+"\n";
    enlog += "ENmaxIOBOK: " + ENmaxIOBOK + ", max_iob: " + max_iob+"\n";

    // patches ===== END

    var tick;

    if (glucose_status.delta > -0.5) {
        tick = "+" + round(glucose_status.delta,0);
    } else {
        tick = round(glucose_status.delta,0);
    }
    //var minDelta = Math.min(glucose_status.delta, glucose_status.short_avgdelta, glucose_status.long_avgdelta);
    var minDelta = Math.min(glucose_status.delta, glucose_status.short_avgdelta);
    var minAvgDelta = Math.min(glucose_status.short_avgdelta, glucose_status.long_avgdelta);
    var maxDelta = Math.max(glucose_status.delta, glucose_status.short_avgdelta, glucose_status.long_avgdelta);

    var profile_sens = round(profile.sens,1)
    var sens = profile.sens;
    /*
    if (typeof autosens_data !== 'undefined' && autosens_data) {
        sens = profile.sens / sensitivityRatio;
        sens = round(sens, 1);
        if (sens !== profile_sens) {
            console.log("Profile ISF from "+profile_sens+" to "+sens);
        } else {
            console.log("Profile ISF unchanged: "+sens);
        }
        //console.log(" (autosens ratio "+sensitivityRatio+")");
    }
    //console.error("CR:", );
    */

    // cTime could be used for bolusing based on recent COB with Ghost COB
    var ENTime = (( new Date(systemTime).getTime() - ENStartTime) / 60000); // elapsed time since EN Start
    var c1Time = (typeof meal_data.firstCarbTime !== 'undefined' ? (( new Date(systemTime).getTime() - meal_data.firstCarbTime) / 60000) : 9999); // first carb entry after EN start
    var cTime = (( new Date(systemTime).getTime() - meal_data.lastCarbTime) / 60000); // last carb entry after EN start
    var b1Time = (typeof meal_data.firstNormalBolusTime !== 'undefined' ? (( new Date(systemTime).getTime() - meal_data.firstNormalBolusTime) / 60000) : 9999); // first normal bolus after EN start
    var bTime = (typeof meal_data.lastBolusNormalTime !== 'undefined' ? (( new Date(systemTime).getTime() - meal_data.lastBolusNormalTime) / 60000) : 9999); // last normal bolus after EN start
    // ENWindowOK is when there is a recent COB entry or manual bolus
    var ENWindowOK = (ENactive && profile.ENWindow > 0 && Math.min(c1Time, cTime ,bTime, b1Time) < profile.ENWindow || (profile.temptargetSet && target_bg == normalTarget));
    var ENWindowRunTime = Math.min(c1Time, cTime, bTime, b1Time);

    // breakfast/first meal related vars
    // firstMealWindow is when either c1Time or b1Time is less than EN Window
    var firstMealWindow = false;
    if (ENWindowOK && c1Time < profile.ENWindow) { // first cob entry is active and within EN Window
        firstMealWindow = true;
        if (b1Time != 9999 && b1Time > profile.ENWindow) firstMealWindow = false; // first bolus has also happened and is more than EN Window
    } else if (ENWindowOK && b1Time < profile.ENWindow) { // first bolus entry is active and within EN Window
        firstMealWindow = true;
        if (c1Time != 9999 && c1Time > profile.ENWindow) firstMealWindow = false; // first COB entry has also happened and is more than EN Window
    }

    // stronger CR and ISF can be used when firstmeal is within 2h window
    var firstMealScaling = (firstMealWindow && !profile.use_sens_TDD && profile.sens == profile.sens_midnight && profile.carb_ratio == profile.carb_ratio_midnight);
    var carb_ratio = (firstMealScaling ? round(profile.carb_ratio_midnight/(profile.BreakfastPct/100),1) : profile.carb_ratio);
    sens = (firstMealScaling ? round(profile.sens_midnight/(profile.BreakfastPct/100),1) : sens);

    enlog += "ENTime: " + ENTime + ", firstMealWindow: "+ firstMealWindow + ", firstMealScaling: "+ firstMealScaling + ", b1Time:" +b1Time+", c1Time:" +c1Time+ ", bTime:" +bTime+ ", cTime:" +cTime+", ENWindowOK:" + ENWindowOK+"\n";
    // If GhostCOB is enabled we will use COB when ENWindowOK but outside this window UAM will be used
    if (ignoreCOB && ENWindowOK && meal_data.mealCOB > 0 ) ignoreCOB = false;

    // TDD ********************************
    var tdd7 = (meal_data.TDDAvg7d ? meal_data.TDDAvg7d : ((basal * 12)*100)/21);
    var tdd1 = meal_data.TDDAvg1d;
    var tdd_4 = meal_data.TDDLast4h;
    var tdd_8 = meal_data.TDDLast8h;
    var tdd8to4 = meal_data.TDDLast8hfor4h;
    var tdd_last8_wt = ( ( ( 1.4 * tdd_4) + ( 0.6 * tdd8to4) ) * 3 );
    var tdd8_exp = ( 3 * tdd_8 );
    console.log("8 hour extrapolated = " +tdd8_exp+ "; ");

    var TDD = ( tdd_last8_wt * 0.33 ) + ( tdd7 * 0.34 ) + (tdd1 * 0.33);
    console.log("TDD = " +TDD+ " using rolling 8h Total extrapolation + TDD7 (60/40); ");
    //var TDD = (tdd7 * 0.4) + (tdd_24 * 0.6);

   console.error("                                 ");
   //console.error("7-day average TDD is: " +tdd7+ "; ");
   console.error("Rolling 8 hours weight average: "+tdd_last8_wt+"; ");
   console.error("Calculated TDD: "+TDD+"; ");
   console.error("1-day average TDD is: "+tdd1+"; ");
   console.error("7-day average TDD is: " +tdd7+ "; ");

    // just after midnight there is a big spike in TDD this will use the 3d avg during this time
//    var TDD = (nowhrs >=2 ? (tdd24h+tdd3d+tdd_pump_now_ms)/3 : tdd3d);
//    enlog +="TDD24H:"+round(tdd24h,3)+", TDD7D:"+round(tdd7d,3)+", TDDPUMPNOWMS:"+round(tdd_pump_now_ms,3)+" = TDD:"+round(TDD,3)+"\n";

    // TIR
    var TIR_suggest = "";
//    if (meal_data.TIRW2 < meal_data.TIRW1 && Math.max(meal_data.TIRW1L, meal_data.TIRW2L)) == 0 { // if the 2nd hour TIR window is less in range and there are no lows
//        TIR_suggest = "+";
//    }

    // ins_val used as the divisor for ISF scaling
    var insulinType = profile.insulinType, ins_val = 90, ins_peak = 75;
    // insulin peak including onset min 30, max 75
    ins_peak = (profile.insulinPeak < 30 ? 30 : Math.min(profile.insulinPeak,75));
    // ins_val: Free-Peak^?:55-90, Lyumjev^45:75, Ultra-Rapid^55:65, Rapid-Acting^75:55
    ins_val = (ins_peak < 60 ? (ins_val-ins_peak)+30 : (ins_val-ins_peak)+40);
    enlog += "insulinType is " + insulinType + ", ins_val is " + ins_val + ", ins_peak is " + ins_peak+"\n";

    enlog += "* advanced ISF:\n";
    // Limit ISF increase for sens_currentBG at 10mmol / 180mgdl
    var ISFbgMax = 180;
    enlog += "ISFbgMax:"+convert_bg(ISFbgMax, profile)+"\n";

    // ISF at normal target
    var sens_normalTarget = sens, sens_profile = sens; // use profile sens and keep profile sens with any SR
    enlog += "sens_normalTarget:" + convert_bg(sens_normalTarget, profile)+"\n";

    // MaxISF is the user defined limit for sens_TDD based on a percentage of the current profile based ISF
    var MaxISF = sens_profile/(profile.MaxISFpct/100);
    // ISF based on TDD
    var sens_TDD = 1800 / ( TDD * (Math.log(( Math.min(normalTarget,ISFbgMax) / ins_val ) + 1 ) ) );
    enlog += "sens_TDD:" + convert_bg(sens_TDD, profile) +"\n";
    sens_TDD = sens_TDD / (profile.sens_TDD_scale/100);
    sens_TDD = (sens_TDD > sens*3 ? sens : sens_TDD); // fresh install of v3

    enlog += "sens_TDD scaled by "+profile.sens_TDD_scale+"%:" + convert_bg(sens_TDD, profile) +"\n";
    // If Use TDD ISF is enabled in profile restrict by MaxISF also adjust for when a high TT using SR if applicable
    sens_normalTarget = (profile.use_sens_TDD && ENactive ? Math.max(MaxISF, sens_TDD) : sens_normalTarget);

     //NEW SR CODE
    // SensitivityRatio code relocated for sens_TDD
    var SR_TDD = tdd8_exp / tdd7;
    if ( high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget ) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        enlog += "Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+"; ";
        sens_normalTarget =  sens_normalTarget / sensitivityRatio ; // CHECK THIS  LINE
        //sens =  sens / sensitivityRatio ; // CHECK THIS  LINE
        sens_normalTarget = round(sens_normalTarget, 1);
        enlog += "sens_normalTarget now "+sens_normalTarget+ "due to temp target; ";
    } else {
        sensitivityRatio = (ENtimeOK && profile.enableSRTDD ? SR_TDD : 1);
        sensitivityRatio = (!profile.enableSRTDD && typeof autosens_data !== 'undefined' && autosens_data ? autosens_data.ratio : sensitivityRatio);
        if (sensitivityRatio > 1) {
            sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
            //sensitivityRatio = (lastNormalCarbAge > 480 && !ENtimeOK ? 1 : sensitivityRatio); // set SR to 1 if no recent carbs (UAM) and its night time
            sensitivityRatio = (!profile.enableSRTDD ? 1 : sensitivityRatio);
            sensitivityRatio = round(sensitivityRatio,2);
            enlog += "Sensitivity ratio >1 is now: "+sensitivityRatio+";\n";
        } else if (sensitivityRatio < 1) {
            sensitivityRatio = Math.max(sensitivityRatio, profile.autosens_min);
            sensitivityRatio = round(sensitivityRatio,2);
            enlog += "Sensitivity ratio <1 is now: "+sensitivityRatio+";\n";
        }
        sens_normalTarget = (!profile.use_sens_TDD ? sens_normalTarget / sensitivityRatio : sens_normalTarget); // adjust ISF when not using sens_TDD
    }
    //enlog +="****** DEBUG ******\n"+"TDD/tdd24h = "+TDD+"/"+tdd24h+"="+TDD/tdd24h+"\n****** DEBUG ******\n";


    if (sensitivityRatio && profile.openapsama_useautosens === true) {
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal !== profile_current_basal) {
            enlog += "Adjusting basal from "+profile_current_basal+" to "+basal+"; ";
        } else {
           enlog += "Basal unchanged: "+basal+"; ";
        }
    }

    // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
    if (profile.temptargetSet) {
        //console.log("Temp Target set, not adjusting with autosens; ");
    } else {
        if ( profile.openapsama_useautosens === true && (profile.sensitivity_raises_target && sensitivityRatio < 1 || profile.resistance_lowers_target && sensitivityRatio > 1) ) {
            // with a target of 100, default 0.7-1.2 autosens min/max range would allow a 93-117 target range
            min_bg = round((min_bg - 60) / sensitivityRatio) + 60;
            max_bg = round((max_bg - 60) / sensitivityRatio) + 60;
            var new_target_bg = round((target_bg - 60) / sensitivityRatio) + 60;
            // don't allow target_bg below 80
            new_target_bg = Math.max(80, new_target_bg);
            if (target_bg === new_target_bg) {
                console.log("target_bg unchanged: "+new_target_bg+"; ");
            } else {
                console.log("target_bg from "+target_bg+" to "+new_target_bg+"; ");
            }
            target_bg = new_target_bg;
        }
    }
    //NEW SR CODE

    //circadian sensitivity curve
    // https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3879757/
    //                       Time 00 ,  01 ,  02 ,  03 ,  04 ,  05 ,  06 ,  07 ,  08 ,  09 ,  10 ,  11 ,  12 ,  13 ,  14 ,  15 ,  16 ,  17 ,  18 ,  19 ,  20 ,  21 ,  22 , 23 ,  24 .
    //var sens_circadian_curve = [1.40, 1.40, 0.80, 0.60, 0.52, 0.47, 0.43, 0.41, 0.40, 0.45, 0.60, 0.72, 0.83, 0.91, 0.97, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.20, 1.40];
    //enlog += "sens_circadian_curve["+nowhrs+"]:" + sens_circadian_curve[nowhrs]+"\n";
    enlog +="nowmins:"+nowmins+"\n";
    //var sens_circadian_now = round(sens_circadian_curve[nowhrs]+((sens_circadian_curve[nowhrs+1]-sens_circadian_curve[nowhrs])/60) * nowmins,1);

    // experimenting with basal rate from 3PM
    var sens_circadian_now = (profile.enableBasalAt3PM ? round(profile.current_basal / profile.BasalAt3PM,2) : 1);
    enlog +="sens_circadian_now:"+sens_circadian_now+"\n";

    // Apply circadian variance to ISF
    sens_normalTarget *= sens_circadian_now;
    enlog += "sens_normalTarget with circadian variance:" + convert_bg(sens_normalTarget, profile)+"\n";

    // Threshold for SMB at night
    var SMBbgOffset = (profile.SMBbgOffset  > 0 ? target_bg+profile.SMBbgOffset : target_bg);
    enlog += "SMBbgOffset:"+SMBbgOffset+"\n";

    // Allow user preferences to adjust the scaling of ISF as BG increases
    // Scaling is converted to a percentage, 0 is normal scaling (1), 5 is 5% stronger (0.95) and -5 is 5% weaker (1.05)
    var ISFBGscaler = profile.ISFbgscaler;
    enlog += "ISFBGscaler from profile:" + ISFBGscaler +"\n";
    // At night when below SMB bg no additional scaling unless profile is weaker
    ISFBGscaler = (!ENtimeOK && bg < SMBbgOffset? Math.min(ISFBGscaler,0) : ISFBGscaler);
    // When eating now is not active during the day do not apply additional scaling unless weaker
    ISFBGscaler = (!ENactive && ENtimeOK ? Math.min(ISFBGscaler,0) : ISFBGscaler);
    enlog += "ISFBGscaler is now:" + ISFBGscaler +"\n";

    // sens_target_bg is used like a target, when the number is lower the ISF scaling is stronger
    // for delta > 4 and 105% change from short_avg or within COB window MAX 45 mins use lower target for ISF scaling
    // for all times when EN time is OK use 20% higher target for less ISF scaling
    // var sens_target_bg = c;
    var sens_target_bg = (ENWindowOK ? ins_val : ins_val * 1.2);
    // only allow adjusted ISF target when eatingnow time is OK and bg below ISFbgMax, dont use at night
    //sens_target_bg = (ENtimeOK && bg < ISFbgMax ? sens_target_bg : target_bg);
    sens_target_bg = (ENactive ? sens_target_bg : target_bg);
    var sens_BGscaler = (Math.log(Math.min(bg,ISFbgMax)/sens_target_bg)+1);

    // Convert ISFBGscaler to %
    ISFBGscaler = (100-ISFBGscaler)/100;
    enlog += "ISFBGscaler % is now:" + ISFBGscaler +"\n";

    // Apply ISFBGscaler to sens_BGscaler
    sens_BGscaler = sens_BGscaler/ISFBGscaler;
    enlog += "sens_BGscaler adjusted with ISFBGscaler:" + sens_BGscaler +"\n";

    var sens_currentBG = sens_normalTarget/sens_BGscaler;
    enlog += "sens_currentBG after scaling:" + convert_bg(sens_currentBG, profile) +"\n";

    // if above target allow scaling and profile ISF as the weakest, if below target use profile ISF as the strongest
    sens_currentBG = (bg > sens_target_bg ? Math.min(sens_currentBG,sens_normalTarget) : Math.max(sens_currentBG,sens_normalTarget));

    // in the COB window allow normal ISF as minimum
    //sens_currentBG = (ENWindowOK ? Math.min(sens_currentBG,sens_normalTarget) : sens_currentBG);
    sens_currentBG = round(sens_currentBG,1);
    enlog += "sens_currentBG final result:"+ convert_bg(sens_currentBG, profile) +"\n";

    // sens is the current bg when EN active e.g. no TT
    sens = (ENactive ? sens_currentBG : sens_normalTarget);
    // at night use sens_currentBG without additional scaling
    sens = (!ENactive && !ENtimeOK ? sens_currentBG : sens);
    enlog += "sens final result:"+sens+"="+convert_bg(sens, profile)+"\n";

    // HypoPredBG - TS
    var eRatio = round(sens / 13.2);
    console.error("CR:",eRatio);
    var HypoPredBG = round( bg - (iob_data.iob * sens) ) + round( 60 / 5 * ( minDelta - round(( -iob_data.activity * sens * 5 ), 2)));
    var EBG = (0.02 * glucose_status.delta * glucose_status.delta) + (0.58 * glucose_status.long_avgdelta) + bg;
    //console.log("Experimental test, EBG : "+EBG+" REBG : "+REBG+" ; ");
    //console.log ("HypoPredBG = "+HypoPredBG+"; ");
    var hypo_target = round(Math.min(200, min_bg + (EBG - min_bg)/3 ),0);

    /*
    if (!profile.temptargetSet && HypoPredBG <= 125 && profile.sensitivity_raises_target ) {

        if (hypo_target <= target_bg) {
            hypo_target = target_bg + 10;
            console.log("target_bg from "+target_bg+" to "+hypo_target+" because HypoPredBG is lesser than 125 : "+HypoPredBG+"; ");
        } else if (target_bg === hypo_target) {
           console.log("target_bg unchanged: "+hypo_target+"; ");
        }

        target_bg = hypo_target;
        halfBasalTarget = 160;
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+"; ");
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal !== profile_current_basal) {
            console.log("Adjusting basal from "+profile_current_basal+" to "+basal+"; ");
        } else {
            console.log("Basal unchanged: "+basal+"; ");
        }
   }
   */


    // compare currenttemp to iob_data.lastTemp and cancel temp if they don't match
    var lastTempAge;
    if (typeof iob_data.lastTemp !== 'undefined' ) {
        lastTempAge = round(( new Date(systemTime).getTime() - iob_data.lastTemp.date ) / 60000); // in minutes
    } else {
        lastTempAge = 0;
    }
    //console.error("currenttemp:",currenttemp,"lastTemp:",JSON.stringify(iob_data.lastTemp),"lastTempAge:",lastTempAge,"m");
    var tempModulus = (lastTempAge + currenttemp.duration) % 30;
    console.error("currenttemp:",currenttemp,"lastTempAge:",lastTempAge,"m","tempModulus:",tempModulus,"m");
    rT.temp = 'absolute';
    rT.deliverAt = deliverAt;
    if ( microBolusAllowed && currenttemp && iob_data.lastTemp && currenttemp.rate !== iob_data.lastTemp.rate && lastTempAge > 10 && currenttemp.duration ) {
        rT.reason = "Warning: currenttemp rate "+currenttemp.rate+" != lastTemp rate "+iob_data.lastTemp.rate+" from pumphistory; canceling temp";
        return tempBasalFunctions.setTempBasal(0, 0, profile, rT, currenttemp);
    }
    if ( currenttemp && iob_data.lastTemp && currenttemp.duration > 0 ) {
        // TODO: fix this (lastTemp.duration is how long it has run; currenttemp.duration is time left
        //if ( currenttemp.duration < iob_data.lastTemp.duration - 2) {
            //rT.reason = "Warning: currenttemp duration "+currenttemp.duration+" << lastTemp duration "+round(iob_data.lastTemp.duration,1)+" from pumphistory; setting neutral temp of "+basal+".";
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        //}
        //console.error(lastTempAge, round(iob_data.lastTemp.duration,1), round(lastTempAge - iob_data.lastTemp.duration,1));
        var lastTempEnded = lastTempAge - iob_data.lastTemp.duration
        if ( lastTempEnded > 5 && lastTempAge > 10 ) {
            rT.reason = "Warning: currenttemp running but lastTemp from pumphistory ended "+lastTempEnded+"m ago; canceling temp";
            //console.error(currenttemp, round(iob_data.lastTemp,1), round(lastTempAge,1));
            return tempBasalFunctions.setTempBasal(0, 0, profile, rT, currenttemp);
        }
        // TODO: figure out a way to do this check that doesn't fail across basal schedule boundaries
        //if ( tempModulus < 25 && tempModulus > 5 ) {
            //rT.reason = "Warning: currenttemp duration "+currenttemp.duration+" + lastTempAge "+lastTempAge+" isn't a multiple of 30m; setting neutral temp of "+basal+".";
            //console.error(rT.reason);
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        //}
    }

    //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
    var bgi = round(( -iob_data.activity * sens * 5 ), 2);
    // project deviations for 30 minutes
    var deviation = round( 30 / 5 * ( minDelta - bgi ) );
    // don't overreact to a big negative delta: use minAvgDelta if deviation is negative
    if (deviation < 0) {
        deviation = round( (30 / 5) * ( minAvgDelta - bgi ) );
        // and if deviation is still negative, use long_avgdelta
        if (deviation < 0) {
            deviation = round( (30 / 5) * ( glucose_status.long_avgdelta - bgi ) );
        }
    }

    // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
    if (iob_data.iob > 0) {
        var naive_eventualBG = round( bg - (iob_data.iob * sens) );
    } else { // if IOB is negative, be more conservative and use the lower of sens, profile.sens
        naive_eventualBG = round( bg - (iob_data.iob * Math.min(sens, profile.sens) ) );
    }
    // and adjust it for the deviation above
    var eventualBG = naive_eventualBG + deviation;

    // raise target for noisy / raw CGM data
    if (glucose_status.noise >= 2) {
        // increase target at least 10% (default 30%) for raw / noisy data
        var noisyCGMTargetMultiplier = Math.max( 1.1, profile.noisyCGMTargetMultiplier );
        // don't allow maxRaw above 250
        var maxRaw = Math.min( 250, profile.maxRaw );
        var adjustedMinBG = round(Math.min(200, min_bg * noisyCGMTargetMultiplier ));
        var adjustedTargetBG = round(Math.min(200, target_bg * noisyCGMTargetMultiplier ));
        var adjustedMaxBG = round(Math.min(200, max_bg * noisyCGMTargetMultiplier ));
        console.log("Raising target_bg for noisy / raw CGM data, from "+target_bg+" to "+adjustedTargetBG+"; ");
        min_bg = adjustedMinBG;
        target_bg = adjustedTargetBG;
        max_bg = adjustedMaxBG;
    // adjust target BG range if configured to bring down high BG faster
    } else if ( bg > max_bg && profile.adv_target_adjustments && ! profile.temptargetSet ) {
        // with target=100, as BG rises from 100 to 160, adjustedTarget drops from 100 to 80
        adjustedMinBG = round(Math.max(80, min_bg - (bg - min_bg)/3 ),0);
        adjustedTargetBG =round( Math.max(80, target_bg - (bg - target_bg)/3 ),0);
        adjustedMaxBG = round(Math.max(80, max_bg - (bg - max_bg)/3 ),0);
        // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedMinBG, don’t use it
        //console.error("naive_eventualBG:",naive_eventualBG+", eventualBG:",eventualBG);
        if (eventualBG > adjustedMinBG && naive_eventualBG > adjustedMinBG && min_bg > adjustedMinBG) {
            console.log("Adjusting targets for high BG: min_bg from "+min_bg+" to "+adjustedMinBG+"; ");
            min_bg = adjustedMinBG;
        } else {
            console.log("min_bg unchanged: "+min_bg+"; ");
        }
        // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedTargetBG, don’t use it
        if (eventualBG > adjustedTargetBG && naive_eventualBG > adjustedTargetBG && target_bg > adjustedTargetBG) {
            console.log("target_bg from "+target_bg+" to "+adjustedTargetBG+"; ");
            target_bg = adjustedTargetBG;
        } else {
            console.log("target_bg unchanged: "+target_bg+"; ");
        }
        // if eventualBG, naive_eventualBG, and max_bg aren't all above adjustedMaxBG, don’t use it
        if (eventualBG > adjustedMaxBG && naive_eventualBG > adjustedMaxBG && max_bg > adjustedMaxBG) {
            console.error("max_bg from "+max_bg+" to "+adjustedMaxBG);
            max_bg = adjustedMaxBG;
        } else {
            console.error("max_bg unchanged: "+max_bg);
        }
    }

    var expectedDelta = calculate_expected_delta(target_bg, eventualBG, bgi);
    if (typeof eventualBG === 'undefined' || isNaN(eventualBG)) {
        rT.error ='Error: could not calculate eventualBG. ';
        return rT;
    }

    // min_bg of 90 -> threshold of 65, 100 -> 70 110 -> 75, and 130 -> 85
    //var threshold = Math.max(min_bg - 0.5*(min_bg-40),72); // minimum 72
    var threshold = Math.max(min_bg-0.5*(min_bg-40), profile.normal_target_bg-9, 75); // minimum 75 or current profile target - 10

    //console.error(reservoir_data);

    rT = {
        'temp': 'absolute'
        , 'bg': bg
        , 'tick': tick
        , 'eventualBG': eventualBG
        , 'targetBG': target_bg
        , 'insulinReq': 0
        , 'reservoir' : reservoir_data // The expected reservoir volume at which to deliver the microbolus (the reservoir volume from right before the last pumphistory run)
        , 'deliverAt' : deliverAt // The time at which the microbolus should be delivered
        , 'sensitivityRatio' : sensitivityRatio // autosens ratio (fraction of normal basal)
    };

    // generate predicted future BGs based on IOB, COB, and current absorption rate

    var COBpredBGs = [];
    var aCOBpredBGs = [];
    var IOBpredBGs = [];
    var UAMpredBGs = [];
    var ZTpredBGs = [];
    COBpredBGs.push(bg);
    aCOBpredBGs.push(bg);
    IOBpredBGs.push(bg);
    ZTpredBGs.push(bg);
    UAMpredBGs.push(bg);

    var enableSMB = enable_smb(
        profile,
        microBolusAllowed,
        meal_data,
        target_bg
    );

    // enable UAM (if enabled in preferences)
    var enableUAM=(profile.enableUAM);


    //console.error(meal_data);
    // carb impact and duration are 0 unless changed below
    var ci = 0;
    var cid = 0;
    // calculate current carb absorption rate, and how long to absorb all carbs
    // CI = current carb impact on BG in mg/dL/5m
    ci = round((minDelta - bgi),1);
    var uci = round((minDelta - bgi),1);
    // ISF (mg/dL/U) / CR (g/U) = CSF (mg/dL/g)

    // TODO: remove commented-out code for old behavior
    //if (profile.temptargetSet) {
        // if temptargetSet, use unadjusted profile.sens to allow activity mode sensitivityRatio to adjust CR
        //var csf = profile.sens / carb_ratio;
    //} else {
        // otherwise, use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments
        // so that autotuned CR is still in effect even when basals and ISF are being adjusted by autosens
        //var csf = sens / carb_ratio;
    //}
    // use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments so that
    // autotuned CR is still in effect even when basals and ISF are being adjusted by TT or autosens
    // this avoids overdosing insulin for large meals when low temp targets are active
    csf = sens_currentBG / carb_ratio;
    console.error("profile.sens:",profile.sens,"sens:",sens,"CSF:",csf);

    var maxCarbAbsorptionRate = 30; // g/h; maximum rate to assume carbs will absorb if no CI observed
    // limit Carb Impact to maxCarbAbsorptionRate * csf in mg/dL per 5m
    var maxCI = round(maxCarbAbsorptionRate*csf*5/60,1)
    if (ci > maxCI) {
        console.error("Limiting carb impact from",ci,"to",maxCI,"mg/dL/5m (",maxCarbAbsorptionRate,"g/h )");
        ci = maxCI;
    }
    var remainingCATimeMin = 3; // h; duration of expected not-yet-observed carb absorption
    // adjust remainingCATime (instead of CR) for autosens if sensitivityRatio defined
    if (sensitivityRatio){
        remainingCATimeMin = remainingCATimeMin / sensitivityRatio;
    }
    // 20 g/h means that anything <= 60g will get a remainingCATimeMin, 80g will get 4h, and 120g 6h
    // when actual absorption ramps up it will take over from remainingCATime
    var assumedCarbAbsorptionRate = 20; // g/h; maximum rate to assume carbs will absorb if no CI observed
    var remainingCATime = remainingCATimeMin;
    if (meal_data.carbs) {
        // if carbs * assumedCarbAbsorptionRate > remainingCATimeMin, raise it
        // so <= 90g is assumed to take 3h, and 120g=4h
        remainingCATimeMin = Math.max(remainingCATimeMin, meal_data.mealCOB/assumedCarbAbsorptionRate);
        var lastCarbAge = round(( new Date(systemTime).getTime() - meal_data.lastCarbTime ) / 60000);
        //console.error(meal_data.lastCarbTime, lastCarbAge);

        var fractionCOBAbsorbed = ( meal_data.carbs - meal_data.mealCOB ) / meal_data.carbs;
        remainingCATime = remainingCATimeMin + 1.5 * lastCarbAge/60;
        remainingCATime = round(remainingCATime,1);
        //console.error(fractionCOBAbsorbed, remainingCATimeAdjustment, remainingCATime)
        console.error("Last carbs",lastCarbAge,"minutes ago; remainingCATime:",remainingCATime,"hours;",round(fractionCOBAbsorbed*100)+"% carbs absorbed");
    }

    // calculate the number of carbs absorbed over remainingCATime hours at current CI
    // CI (mg/dL/5m) * (5m)/5 (m) * 60 (min/hr) * 4 (h) / 2 (linear decay factor) = total carb impact (mg/dL)
    var totalCI = Math.max(0, ci / 5 * 60 * remainingCATime / 2);
    // totalCI (mg/dL) / CSF (mg/dL/g) = total carbs absorbed (g)
    var totalCA = totalCI / csf;
    var remainingCarbsCap = 90; // default to 90
    var remainingCarbsFraction = 1;
    if (profile.remainingCarbsCap) { remainingCarbsCap = Math.min(90,profile.remainingCarbsCap); }
    if (profile.remainingCarbsFraction) { remainingCarbsFraction = Math.min(1,profile.remainingCarbsFraction); }
    var remainingCarbsIgnore = 1 - remainingCarbsFraction;
    var remainingCarbs = Math.max(0, meal_data.mealCOB - totalCA - meal_data.carbs*remainingCarbsIgnore);
    remainingCarbs = Math.min(remainingCarbsCap,remainingCarbs);
    // assume remainingCarbs will absorb in a /\ shaped bilinear curve
    // peaking at remainingCATime / 2 and ending at remainingCATime hours
    // area of the /\ triangle is the same as a remainingCIpeak-height rectangle out to remainingCATime/2
    // remainingCIpeak (mg/dL/5m) = remainingCarbs (g) * CSF (mg/dL/g) * 5 (m/5m) * 1h/60m / (remainingCATime/2) (h)
    var remainingCIpeak = remainingCarbs * csf * 5 / 60 / (remainingCATime/2);
    //console.error(profile.min_5m_carbimpact,ci,totalCI,totalCA,remainingCarbs,remainingCI,remainingCATime);

    // calculate peak deviation in last hour, and slope from that to current deviation
    var slopeFromMaxDeviation = round(meal_data.slopeFromMaxDeviation,2);
    // calculate lowest deviation in last hour, and slope from that to current deviation
    var slopeFromMinDeviation = round(meal_data.slopeFromMinDeviation,2);
    // assume deviations will drop back down at least at 1/3 the rate they ramped up
    var slopeFromDeviations = Math.min(slopeFromMaxDeviation,-slopeFromMinDeviation/3);
    //console.error(slopeFromMaxDeviation);

    var aci = 10;
    //5m data points = g * (1U/10g) * (40mg/dL/1U) / (mg/dL/5m)
    // duration (in 5m data points) = COB (g) * CSF (mg/dL/g) / ci (mg/dL/5m)
    // limit cid to remainingCATime hours: the reset goes to remainingCI
    if (ci === 0) {
        // avoid divide by zero
        cid = 0;
    } else {
        cid = Math.min(remainingCATime*60/5/2,Math.max(0, meal_data.mealCOB * csf / ci ));
    }
    var acid = Math.max(0, meal_data.mealCOB * csf / aci );
    // duration (hours) = duration (5m) * 5 / 60 * 2 (to account for linear decay)
    console.error("Carb Impact:",ci,"mg/dL per 5m; CI Duration:",round(cid*5/60*2,1),"hours; remaining CI (~2h peak):",round(remainingCIpeak,1),"mg/dL per 5m");
    //console.error("Accel. Carb Impact:",aci,"mg/dL per 5m; ACI Duration:",round(acid*5/60*2,1),"hours");
    var minIOBPredBG = 999;
    var minCOBPredBG = 999;
    var minUAMPredBG = 999;
    var minGuardBG = bg;
    var minCOBGuardBG = 999;
    var minUAMGuardBG = 999;
    var minIOBGuardBG = 999;
    var minZTGuardBG = 999;
    var minPredBG;
    var avgPredBG;
    var IOBpredBG = eventualBG;
    var maxIOBPredBG = bg;
    var maxCOBPredBG = bg;
    var maxUAMPredBG = bg;
    //var maxPredBG = bg;
    var eventualPredBG = bg;
    var lastIOBpredBG;
    var lastCOBpredBG;
    var lastUAMpredBG;
    var lastZTpredBG;
    var UAMduration = 0;
    var remainingCItotal = 0;
    var remainingCIs = [];
    var predCIs = [];
    try {
        iobArray.forEach(function(iobTick) {
            //console.error(iobTick);
            var predBGI = round(( -iobTick.activity * sens * 5 ), 2);
            var predZTBGI = round(( -iobTick.iobWithZeroTemp.activity * sens * 5 ), 2);
            // for IOBpredBGs, predicted deviation impact drops linearly from current deviation down to zero
            // over 60 minutes (data points every 5m)
            var predDev = ci * ( 1 - Math.min(1,IOBpredBGs.length/(60/5)) );
            IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI + predDev;
            // calculate predBGs with long zero temp without deviations
            var ZTpredBG = ZTpredBGs[ZTpredBGs.length-1] + predZTBGI;
            // for COBpredBGs, predicted carb impact drops linearly from current carb impact down to zero
            // eventually accounting for all carbs (if they can be absorbed over DIA)
            var predCI = Math.max(0, Math.max(0,ci) * ( 1 - COBpredBGs.length/Math.max(cid*2,1) ) );
            var predACI = Math.max(0, Math.max(0,aci) * ( 1 - COBpredBGs.length/Math.max(acid*2,1) ) );
            // if any carbs aren't absorbed after remainingCATime hours, assume they'll absorb in a /\ shaped
            // bilinear curve peaking at remainingCIpeak at remainingCATime/2 hours (remainingCATime/2*12 * 5m)
            // and ending at remainingCATime h (remainingCATime*12 * 5m intervals)
            var intervals = Math.min( COBpredBGs.length, (remainingCATime*12)-COBpredBGs.length );
            var remainingCI = Math.max(0, intervals / (remainingCATime/2*12) * remainingCIpeak );
            remainingCItotal += predCI+remainingCI;
            remainingCIs.push(round(remainingCI,0));
            predCIs.push(round(predCI,0));
            //console.log(round(predCI,1)+"+"+round(remainingCI,1)+" ");
            COBpredBG = COBpredBGs[COBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predCI + remainingCI;
            var aCOBpredBG = aCOBpredBGs[aCOBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predACI;
            // for UAMpredBGs, predicted carb impact drops at slopeFromDeviations
            // calculate predicted CI from UAM based on slopeFromDeviations
            var predUCIslope = Math.max(0, uci + ( UAMpredBGs.length*slopeFromDeviations ) );
            // if slopeFromDeviations is too flat, predicted deviation impact drops linearly from
            // current deviation down to zero over 3h (data points every 5m)
            var predUCImax = Math.max(0, uci * ( 1 - UAMpredBGs.length/Math.max(3*60/5,1) ) );
            //console.error(predUCIslope, predUCImax);
            // predicted CI from UAM is the lesser of CI based on deviationSlope or DIA
            var predUCI = Math.min(predUCIslope, predUCImax);
            if(predUCI>0) {
                //console.error(UAMpredBGs.length,slopeFromDeviations, predUCI);
                UAMduration=round((UAMpredBGs.length+1)*5/60,1);
            }
            UAMpredBG = UAMpredBGs[UAMpredBGs.length-1] + predBGI + Math.min(0, predDev) + predUCI;
            //console.error(predBGI, predCI, predUCI);
            // truncate all BG predictions at 4 hours
            if ( IOBpredBGs.length < 48) { IOBpredBGs.push(IOBpredBG); }
            if ( COBpredBGs.length < 48) { COBpredBGs.push(COBpredBG); }
            if ( aCOBpredBGs.length < 48) { aCOBpredBGs.push(aCOBpredBG); }
            if ( UAMpredBGs.length < 48) { UAMpredBGs.push(UAMpredBG); }
            if ( ZTpredBGs.length < 48) { ZTpredBGs.push(ZTpredBG); }
            // calculate minGuardBGs without a wait from COB, UAM, IOB predBGs
            if ( COBpredBG < minCOBGuardBG ) { minCOBGuardBG = round(COBpredBG); }
            if ( UAMpredBG < minUAMGuardBG ) { minUAMGuardBG = round(UAMpredBG); }
            if ( IOBpredBG < minIOBGuardBG ) { minIOBGuardBG = round(IOBpredBG); }
            if ( ZTpredBG < minZTGuardBG ) { minZTGuardBG = round(ZTpredBG); }

            // set minPredBGs starting when currently-dosed insulin activity will peak
            // look ahead 60m (regardless of insulin type) so as to be less aggressive on slower insulins
            var insulinPeakTime = 60;
            // add 30m to allow for insulin delivery (SMBs or temps)
            insulinPeakTime = 90;
            insulinPeakTime = ins_peak; // use insulin peak with onset from insulinType
            var insulinPeak5m = (insulinPeakTime/60)*12;
            //console.error(insulinPeakTime, insulinPeak5m, profile.insulinPeakTime, profile.curve);

            // wait 90m before setting minIOBPredBG
            if ( IOBpredBGs.length > insulinPeak5m && (IOBpredBG < minIOBPredBG) ) { minIOBPredBG = round(IOBpredBG); }
            if ( IOBpredBG > maxIOBPredBG ) { maxIOBPredBG = IOBpredBG; }
            // wait 85-105m before setting COB and 60m for UAM minPredBGs
            if ( (cid || remainingCIpeak > 0) && COBpredBGs.length > insulinPeak5m && (COBpredBG < minCOBPredBG) ) { minCOBPredBG = round(COBpredBG); }
            if ( (cid || remainingCIpeak > 0) && COBpredBG > maxIOBPredBG ) { maxCOBPredBG = COBpredBG; }
            if ( enableUAM && UAMpredBGs.length > 12 && (UAMpredBG < minUAMPredBG) ) { minUAMPredBG = round(UAMpredBG); }
            if ( enableUAM && UAMpredBG > maxIOBPredBG ) { maxUAMPredBG = UAMpredBG; }
        });
        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
    } catch (e) {
        console.error("Problem with iobArray.  Optional feature Advanced Meal Assist disabled");
    }
    if (meal_data.mealCOB) {
        console.error("predCIs (mg/dL/5m):",predCIs.join(" "));
        console.error("remainingCIs:      ",remainingCIs.join(" "));
    }
    rT.predBGs = {};
    IOBpredBGs.forEach(function(p, i, theArray) {
        theArray[i] = round(Math.min(401,Math.max(39,p)));
    });
    for (var i=IOBpredBGs.length-1; i > 12; i--) {
        if (IOBpredBGs[i-1] !== IOBpredBGs[i]) { break; }
        else { IOBpredBGs.pop(); }
    }
    rT.predBGs.IOB = IOBpredBGs;
    lastIOBpredBG=round(IOBpredBGs[IOBpredBGs.length-1]);
    ZTpredBGs.forEach(function(p, i, theArray) {
        theArray[i] = round(Math.min(401,Math.max(39,p)));
    });
    for (i=ZTpredBGs.length-1; i > 6; i--) {
        // stop displaying ZTpredBGs once they're rising and above target
        if (ZTpredBGs[i-1] >= ZTpredBGs[i] || ZTpredBGs[i] <= target_bg) { break; }
        else { ZTpredBGs.pop(); }
    }
    rT.predBGs.ZT = ZTpredBGs;
    lastZTpredBG=round(ZTpredBGs[ZTpredBGs.length-1]);
    if (meal_data.mealCOB > 0) {
        aCOBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = round(Math.min(401,Math.max(39,p)));
        });
        for (i=aCOBpredBGs.length-1; i > 12; i--) {
            if (aCOBpredBGs[i-1] !== aCOBpredBGs[i]) { break; }
            else { aCOBpredBGs.pop(); }
        }
    }
    if (meal_data.mealCOB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
        COBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = round(Math.min(401,Math.max(39,p)));
        });
        for (i=COBpredBGs.length-1; i > 12; i--) {
            if (COBpredBGs[i-1] !== COBpredBGs[i]) { break; }
            else { COBpredBGs.pop(); }
        }
        rT.predBGs.COB = COBpredBGs;
        lastCOBpredBG=round(COBpredBGs[COBpredBGs.length-1]);
        if (!ignoreCOB) eventualBG = Math.max(eventualBG, round(COBpredBGs[COBpredBGs.length-1]) ); //MD#01: Dont use COB eventualBG if ignoring COB
    }
    if (ci > 0 || remainingCIpeak > 0) {
        if (enableUAM) {
            UAMpredBGs.forEach(function(p, i, theArray) {
                theArray[i] = round(Math.min(401,Math.max(39,p)));
            });
            for (i=UAMpredBGs.length-1; i > 12; i--) {
                if (UAMpredBGs[i-1] !== UAMpredBGs[i]) { break; }
                else { UAMpredBGs.pop(); }
            }
            rT.predBGs.UAM = UAMpredBGs;
            lastUAMpredBG=round(UAMpredBGs[UAMpredBGs.length-1]);
            if (UAMpredBGs[UAMpredBGs.length-1]) {
                eventualBG = Math.max(eventualBG, round(UAMpredBGs[UAMpredBGs.length-1]) );
            }
        }

        // set eventualBG based on COB or UAM predBGs
        rT.eventualBG = eventualBG;
    }

    console.error("UAM Impact:",uci,"mg/dL per 5m; UAM Duration:",UAMduration,"hours");

    // Eventual BG based future sensitivity - sens_future
    // sens_future is calculated using a percentage of eventualBG (sens_eBGweight) with the rest as current bg, to reduce the risk of overdosing.
    var sens_future = sens, sens_future_max = false, sens_future_bg = bg;
    // categorize the eventualBG prediction type for more accurate weighting
    // By default sens_future will remain as the current bg ie. sens with eBGweight = 0
    var sens_predType = "BGL", sens_eBGweight = 0;
    if (lastUAMpredBG > 0 && eventualBG >= lastUAMpredBG) sens_predType = "UAM"; // UAM or any prediction > UAM is the default
    if (lastCOBpredBG > 0 && eventualBG == lastCOBpredBG) sens_predType = "COB"; // if COB prediction is present eventualBG aligns
    if (minDelta >=-2 && minDelta <=2) sens_predType = "BGL"; // small delta use current bg

    if (ENactive) {
        //if (sens_predType == "UAM" && bg <= ISFbgMax) {
        if (sens_predType == "UAM") {
            sens_eBGweight = 0.25;
            sens_eBGweight = (delta > 4 && DeltaPct > 1 && !COB ? 0.50 : sens_eBGweight); // rising and accelerating
        }
        if (sens_predType == "COB") {
            sens_eBGweight = 0.50;
            sens_eBGweight = (delta >=6 ? 0.75 : sens_eBGweight); // rising faster
        }
        if (sens_predType == "BGL") {
            sens_eBGweight = 0.50; // small delta
        }

        // if bg is falling and not slowly increase weighting to eventualBG
        sens_eBGweight = (delta < 0 && eventualBG < target_bg ? 1 : sens_eBGweight);

        // calculate the prediction bg based on the weightings for eventualBG
        sens_future_bg = (Math.max(eventualBG,40) * sens_eBGweight) + (bg * (1-sens_eBGweight));

        // apply dynamic ISF scaling formula
        var sens_future_scaler = Math.log(sens_future_bg/75)+1;
        sens_future = sens_normalTarget/sens_future_scaler;
    }

    // at night or when EN disabled use sens unless using eatingnow override
    if (!ENactive) {
        // Current bg at night
        sens_predType = "BGL";
        sens_future_bg = Math.min(bg,eventualBG);
        sens_future_bg = Math.max(sens_future_bg,40); // minimum 2.2
        sens_eBGweight = (eventualBG < bg ? 1 : 0);
        // apply dynamic ISF scaling formula
        var sens_future_scaler = Math.log(sens_future_bg/target_bg)+1;
        sens_future = sens_normalTarget/sens_future_scaler;
        // limit sens_future to autosens max at night with SMB
        sens_future = (!ENtimeOK && bg < SMBbgOffset ? sens_future : Math.max(sens_future, sens_normalTarget/profile.autosens_max));
        // set sens_future_max to true for reason asterisk
        sens_future_max = (sens_future == sens_normalTarget/profile.autosens_max);
    }

    // if BG below target then take the max of the sens vars
    sens_future = (bg < target_bg && !ENWindowOK ? Math.max(sens_profile, sens_normalTarget, sens_currentBG, sens_future) : sens_future);

    sens_future = round(sens_future,1);
    enlog += "* sens_eBGweight:\n";
    enlog += "sens_predType: " + sens_predType+"\n";
    enlog += "sens_eBGweight final result: " + sens_eBGweight +"\n";

//    enlog += "*** TESTING FUTURE DELTA ***\n";
//    var insulinPeak5m = 18, COBpredBGsDelta, UAMpredBGsDelta;
//    enlog += "insulinPeak5m: "+insulinPeak5m+"\n";
//    enlog += "COBpredBGs.length: " + COBpredBGs.length+"\n";
//    enlog += "UAMpredBGs.length: " + UAMpredBGs.length+"\n";
//    COBpredBGsDelta = round((COBpredBGs.length >= insulinPeak5m + 2 ? COBpredBGs[insulinPeak5m+2]-COBpredBGs[insulinPeak5m] : 0),1);
//    UAMpredBGsDelta = round((UAMpredBGs.length >= insulinPeak5m + 2 ? UAMpredBGs[insulinPeak5m+2]-UAMpredBGs[insulinPeak5m] : 0),1);
//    enlog += "COBpredBGsDelta: " + COBpredBGsDelta+"\n";
//    enlog += "UAMpredBGsDelta: " + UAMpredBGsDelta+"\n";
//    enlog += "*** TESTING FUTURE DELTA ***\n";

    minIOBPredBG = Math.max(39,minIOBPredBG);
    minCOBPredBG = Math.max(39,minCOBPredBG);
    minUAMPredBG = Math.max(39,minUAMPredBG);
    minPredBG = round(minIOBPredBG);

    var fractionCarbsLeft = meal_data.mealCOB/meal_data.carbs;
    // if we have COB and UAM is enabled, average both
    if ( minUAMPredBG < 999 && minCOBPredBG < 999 ) {
        // weight COBpredBG vs. UAMpredBG based on how many carbs remain as COB
        avgPredBG = round( (1-fractionCarbsLeft)*UAMpredBG + fractionCarbsLeft*COBpredBG );
    // if UAM is disabled, average IOB and COB
    } else if ( minCOBPredBG < 999 ) {
        avgPredBG = round( (IOBpredBG + COBpredBG)/2 );
    // if we have UAM but no COB, average IOB and UAM
    } else if ( minUAMPredBG < 999 ) {
        avgPredBG = round( (IOBpredBG + UAMpredBG)/2 );
    } else {
        avgPredBG = round( IOBpredBG );
    }
    if (ignoreCOB && enableUAM) avgPredBG = round( (IOBpredBG + UAMpredBG)/2 );  //MD#01: If we are ignoring COB and we have UAM, average IOB and UAM as above
    // if avgPredBG is below minZTGuardBG, bring it up to that level
    if ( minZTGuardBG > avgPredBG ) {
        avgPredBG = minZTGuardBG;
    }

    // if we have both minCOBGuardBG and minUAMGuardBG, blend according to fractionCarbsLeft
    if ( (cid || remainingCIpeak > 0) ) {
        if ( enableUAM ) {
            minGuardBG = fractionCarbsLeft*minCOBGuardBG + (1-fractionCarbsLeft)*minUAMGuardBG;
        } else {
            minGuardBG = minCOBGuardBG;
        }
    } else if ( enableUAM ) {
        minGuardBG = minUAMGuardBG;
    } else {
        minGuardBG = minIOBGuardBG;
    }
    if (ignoreCOB && enableUAM) minGuardBG = minUAMGuardBG; //MD#01: if we are ignoring COB and have UAM just use minUAMGuardBG as above
    minGuardBG = round(minGuardBG);
    //console.error(minCOBGuardBG, minUAMGuardBG, minIOBGuardBG, minGuardBG);

    var minZTUAMPredBG = minUAMPredBG;
    // if minZTGuardBG is below threshold, bring down any super-high minUAMPredBG by averaging
    // this helps prevent UAM from giving too much insulin in case absorption falls off suddenly
    if ( minZTGuardBG < threshold ) {
        minZTUAMPredBG = (minUAMPredBG + minZTGuardBG) / 2;
    // if minZTGuardBG is between threshold and target, blend in the averaging
    } else if ( minZTGuardBG < target_bg ) {
        // target 100, threshold 70, minZTGuardBG 85 gives 50%: (85-70) / (100-70)
        var blendPct = (minZTGuardBG-threshold) / (target_bg-threshold);
        var blendedMinZTGuardBG = minUAMPredBG*blendPct + minZTGuardBG*(1-blendPct);
        minZTUAMPredBG = (minUAMPredBG + blendedMinZTGuardBG) / 2;
        //minZTUAMPredBG = minUAMPredBG - target_bg + minZTGuardBG;
    // if minUAMPredBG is below minZTGuardBG, bring minUAMPredBG up by averaging
    // this allows more insulin if lastUAMPredBG is below target, but minZTGuardBG is still high
    } else if ( minZTGuardBG > minUAMPredBG ) {
        minZTUAMPredBG = (minUAMPredBG + minZTGuardBG) / 2;
    }
    minZTUAMPredBG = round(minZTUAMPredBG);
    //console.error("minUAMPredBG:",minUAMPredBG,"minZTGuardBG:",minZTGuardBG,"minZTUAMPredBG:",minZTUAMPredBG);
    // if any carbs have been entered recently
    if (meal_data.carbs) {

        // if UAM is disabled, use max of minIOBPredBG, minCOBPredBG
        if ( ! enableUAM && minCOBPredBG < 999 ) {
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG));
        // if we have COB, use minCOBPredBG, or blendedMinPredBG if it's higher
        } else if ( minCOBPredBG < 999 ) {
            // calculate blendedMinPredBG based on how many carbs remain as COB
            var blendedMinPredBG = fractionCarbsLeft*minCOBPredBG + (1-fractionCarbsLeft)*minZTUAMPredBG;
            // if blendedMinPredBG > minCOBPredBG, use that instead
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG, blendedMinPredBG));
        // if carbs have been entered, but have expired, use minUAMPredBG
        } else if ( enableUAM ) {
            minPredBG = minZTUAMPredBG;
        } else {
            minPredBG = minGuardBG;
        }
    // in pure UAM mode, use the higher of minIOBPredBG,minUAMPredBG
    } else if ( enableUAM ) {
        minPredBG = round(Math.max(minIOBPredBG,minZTUAMPredBG));
    }
    if (ignoreCOB && enableUAM) minPredBG = round(Math.max(minIOBPredBG,minZTUAMPredBG)); //MD#01 If we are ignoring COB with UAM enabled use pure UAM mode like above

    // make sure minPredBG isn't higher than avgPredBG
    minPredBG = Math.min( minPredBG, avgPredBG );

    console.log("minPredBG: "+minPredBG+" minIOBPredBG: "+minIOBPredBG+" minZTGuardBG: "+minZTGuardBG);
    if (minCOBPredBG < 999) {
        console.log(" minCOBPredBG: "+minCOBPredBG);
    }
    if (minUAMPredBG < 999) {
        console.log(" minUAMPredBG: "+minUAMPredBG);
    }
    console.error(" avgPredBG:",avgPredBG,"COB:",meal_data.mealCOB,"/",meal_data.carbs);
    // But if the COB line falls off a cliff, don't trust UAM too much:
    // use maxCOBPredBG if it's been set and lower than minPredBG
    if ( maxCOBPredBG > bg && !ignoreCOB ) { //MD#01 Only if we aren't using GhostCOB
        minPredBG = Math.min(minPredBG, maxCOBPredBG);
    }

    rT.COB=meal_data.mealCOB;
    rT.IOB=iob_data.iob;
    rT.reason="COB: " + round(meal_data.mealCOB, 1) + ", Dev: " + convert_bg(deviation, profile) + ", BGI: " + convert_bg(bgi, profile) + ", Delta: " + glucose_status.delta + "/" + glucose_status.short_avgdelta + (delta > 0 ? "=" + round(DeltaPct*100)+ "%" : "") + ", ISF: " + convert_bg(sens_normalTarget, profile) + (profile.use_sens_TDD && sens_normalTarget == MaxISF ? "*" : "") + "/" + convert_bg(sens, profile) + (bg >= ISFbgMax ? "*" : "") + "=" + convert_bg(sens_future, profile) + (sens_future_max ? "*" : "") + ", CR: " + round(carb_ratio, 2) + ", Target: " + convert_bg(target_bg, profile) + (target_bg !=normalTarget ? "(" +convert_bg(normalTarget, profile)+")" : "") + ", minPredBG " + convert_bg(minPredBG, profile) + ", minGuardBG " + convert_bg(minGuardBG, profile) + ", IOBpredBG " + convert_bg(lastIOBpredBG, profile) + ", LGS: " + convert_bg(threshold, profile);

    if (lastCOBpredBG > 0) {
        rT.reason += ", " + (ignoreCOB && !ENWindowOK ? "!" : "") + "COBpredBG " + convert_bg(lastCOBpredBG, profile);
    }
    if (lastUAMpredBG > 0) {
        rT.reason += ", UAMpredBG " + convert_bg(lastUAMpredBG, profile);
    }
    // extra reason text
    rT.reason += (HypoPredBG <=125 && hypo_target <= target_bg ? ", HypoPredBG " + convert_bg(HypoPredBG, profile) + ", HypoTargetBG " + convert_bg(hypo_target, profile) : "");
    rT.reason += ", EN: " + (ENactive ? "Active" : "Inactive");
    rT.reason += (firstMealWindow ? " Breakfast " : "");
    rT.reason += (ENWindowOK ? " (" + round(ENWindowRunTime)+"/"+profile.ENWindow+"m)" : "");
    rT.reason += (!ENmaxIOBOK ? " IOB" : "");
    rT.reason += (meal_data.mealCOB > 0  ? " COB" : "");
    rT.reason += (profile.temptargetSet ? " TT="+convert_bg(target_bg, profile) : "");
    rT.reason += (!ENactive && !ENtimeOK && bg < SMBbgOffset && meal_data.mealCOB==0 ? " No SMB < " + convert_bg(SMBbgOffset,profile) : "");
    rT.reason += ", SR: " + (typeof autosens_data !== 'undefined' && autosens_data ? round(autosens_data.ratio,2) + "=": "") + sensitivityRatio;
    rT.reason += ", SR_TDD: " + round(SR_TDD,2);
    rT.reason += ", TDD:" + round(TDD, 2) + " " + (profile.sens_TDD_scale !=100 ? profile.sens_TDD_scale + "% " : "") + "("+convert_bg(sens_TDD, profile)+")";
    //rT.reason += ", TIRW1:" + round(meal_data.TIRW1H, 2) + "/" + round(meal_data.TIRW1, 2) + "/"+ round(meal_data.TIRW1L, 2);
    //rT.reason += ", TIRW2:" + round(meal_data.TIRW2H, 2) + "/" + round(meal_data.TIRW2, 2) + "/"+ round(meal_data.TIRW2L, 2); //+(TIR_suggest ? "=" + TIR_suggest : "");
    rT.reason += "; ";
    // use naive_eventualBG if above 40, but switch to minGuardBG if both eventualBGs hit floor of 39
    var carbsReqBG = naive_eventualBG;
    if ( carbsReqBG < 40 ) {
        carbsReqBG = Math.min( minGuardBG, carbsReqBG );
    }
    var bgUndershoot = threshold - carbsReqBG;
    // calculate how long until COB (or IOB) predBGs drop below min_bg
    var minutesAboveMinBG = 240;
    var minutesAboveThreshold = 240;
    if (meal_data.mealCOB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
        for (i=0; i<COBpredBGs.length; i++) {
            //console.error(COBpredBGs[i], min_bg);
            if ( COBpredBGs[i] < min_bg ) {
                minutesAboveMinBG = 5*i;
                break;
            }
        }
        for (i=0; i<COBpredBGs.length; i++) {
            //console.error(COBpredBGs[i], threshold);
            if ( COBpredBGs[i] < threshold ) {
                minutesAboveThreshold = 5*i;
                break;
            }
        }
    } else {
        for (i=0; i<IOBpredBGs.length; i++) {
            //console.error(IOBpredBGs[i], min_bg);
            if ( IOBpredBGs[i] < min_bg ) {
                minutesAboveMinBG = 5*i;
                break;
            }
        }
        for (i=0; i<IOBpredBGs.length; i++) {
            //console.error(IOBpredBGs[i], threshold);
            if ( IOBpredBGs[i] < threshold ) {
                minutesAboveThreshold = 5*i;
                break;
            }
        }
    }

    if (enableSMB && minGuardBG < threshold) {
        console.error("minGuardBG",convert_bg(minGuardBG, profile),"projected below", convert_bg(threshold, profile) ,"- disabling SMB");
        //rT.reason += "minGuardBG "+minGuardBG+"<"+threshold+": SMB disabled; ";
        enableSMB = false;
    }
    if ( maxDelta > 0.30 * bg ) {
        console.error("maxDelta",convert_bg(maxDelta, profile),"> 30% of BG",convert_bg(bg, profile),"- disabling SMB");
        rT.reason += esc_text("maxDelta "+convert_bg(maxDelta, profile)+" > 30% of BG "+convert_bg(bg, profile)+": SMB disabled; ");
        enableSMB = false;
    }

    console.error("BG projected to remain above",convert_bg(min_bg, profile),"for",minutesAboveMinBG,"minutes");
    if ( minutesAboveThreshold < 240 || minutesAboveMinBG < 60 ) {
        console.error("BG projected to remain above",convert_bg(threshold,profile),"for",minutesAboveThreshold,"minutes");
    }
    // include at least minutesAboveThreshold worth of zero temps in calculating carbsReq
    // always include at least 30m worth of zero temp (carbs to 80, low temp up to target)
    var zeroTempDuration = minutesAboveThreshold;
    // BG undershoot, minus effect of zero temps until hitting min_bg, converted to grams, minus COB
    var zeroTempEffect = profile.current_basal*sens*zeroTempDuration/60;
    // don't count the last 25% of COB against carbsReq
    var COBforCarbsReq = Math.max(0, meal_data.mealCOB - 0.25*meal_data.carbs);
    var carbsReq = (bgUndershoot - zeroTempEffect) / csf - COBforCarbsReq;
    zeroTempEffect = round(zeroTempEffect);
    carbsReq = round(carbsReq);
    console.error("naive_eventualBG:",naive_eventualBG,"bgUndershoot:",bgUndershoot,"zeroTempDuration:",zeroTempDuration,"zeroTempEffect:",zeroTempEffect,"carbsReq:",carbsReq);
    console.log("=======================");
    console.log("Eating Now");
    console.log("=======================");
    console.log(enlog);
    console.log("=======================");

    if ( carbsReq >= profile.carbsReqThreshold && minutesAboveThreshold <= 45 ) {
        rT.carbsReq = carbsReq;
        rT.carbsReqWithin = minutesAboveThreshold;
        rT.reason += carbsReq + " add'l carbs req w/in " + minutesAboveThreshold + "m; ";
    }

    // don't low glucose suspend if IOB is already super negative and BG is rising faster than predicted
    if (bg < threshold && iob_data.iob < -profile.current_basal*20/60 && minDelta > 0 && minDelta > expectedDelta) {
        rT.reason += "IOB "+iob_data.iob+" < " + round(-profile.current_basal*20/60,2);
        rT.reason += " and minDelta " + convert_bg(minDelta, profile) + " > " + "expectedDelta " + convert_bg(expectedDelta, profile) + "; ";
        rT.reason = esc_text(rT.reason);
    // predictive low glucose suspend mode: BG is / is projected to be < threshold
    } else if ( bg < threshold || minGuardBG < threshold ) {
        rT.reason += esc_text("minGuardBG " + convert_bg(minGuardBG, profile) + "<" + convert_bg(threshold, profile));
        bgUndershoot = target_bg - minGuardBG;
        var worstCaseInsulinReq = bgUndershoot / sens;
        var durationReq = round(60*worstCaseInsulinReq / profile.current_basal);
        durationReq = round(durationReq/30)*30;
        // always set a 30-120m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
        durationReq = Math.min(120,Math.max(30,durationReq));
        return tempBasalFunctions.setTempBasal(0, durationReq, profile, rT, currenttemp);
    }

    // if not in LGS mode, cancel temps before the top of the hour to reduce beeping/vibration
    // console.error(profile.skip_neutral_temps, rT.deliverAt.getMinutes());
    if ( profile.skip_neutral_temps && rT.deliverAt.getMinutes() >= 55 ) {
        rT.reason += "; Canceling temp at " + rT.deliverAt.getMinutes() + "m past the hour. ";
        return tempBasalFunctions.setTempBasal(0, 0, profile, rT, currenttemp);
    }

    if (eventualBG < min_bg) { // if eventual BG is below target:
        rT.reason += esc_text("Eventual " + (sens_predType !="BGL" ? sens_predType : "") + " BG " + convert_bg(eventualBG, profile) + " < " + convert_bg(min_bg, profile));
        // if 5m or 30m avg BG is rising faster than expected delta
        if ( minDelta > expectedDelta && minDelta > 0 && !carbsReq ) {
            // if naive_eventualBG < 40, set a 30m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
            if (naive_eventualBG < 40) {
                rT.reason += esc_text(", naive_eventualBG < 40. ");
                return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
            }
            if (glucose_status.delta > minDelta) {
                rT.reason += esc_text(", but Delta " + convert_bg(tick, profile) + " > expectedDelta " + convert_bg(expectedDelta, profile));
            } else {
                rT.reason += esc_text(", but Min. Delta " + minDelta.toFixed(2) + " > Exp. Delta " + convert_bg(expectedDelta, profile));
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + round(currenttemp.rate,2) + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }

        // calculate 30m low-temp required to get projected BG up to target
        // multiply by 2 to low-temp faster for increased hypo safety
        var insulinReq = 2 * Math.min(0, (eventualBG - target_bg) / sens_future);
        insulinReq = round( insulinReq , 2);
        // calculate naiveInsulinReq based on naive_eventualBG
        var naiveInsulinReq = Math.min(0, (naive_eventualBG - target_bg) / sens);
        naiveInsulinReq = round( naiveInsulinReq , 2);
        if (minDelta < 0 && minDelta > expectedDelta) {
            // if we're barely falling, newinsulinReq should be barely negative
            var newinsulinReq = round(( insulinReq * (minDelta / expectedDelta) ), 2);
            //console.error("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
            insulinReq = newinsulinReq;
        }
        // rate required to deliver insulinReq less insulin over 30m:
        var rate = basal + (2 * insulinReq);
        rate = round_basal(rate, profile);

        // if required temp < existing temp basal
        var insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
        // if current temp would deliver a lot (30% of basal) less than the required insulin,
        // by both normal and naive calculations, then raise the rate
        var minInsulinReq = Math.min(insulinReq,naiveInsulinReq);
        if (insulinScheduled < minInsulinReq - basal*0.3) {
            rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate).toFixed(2) + " is a lot less than needed. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }
        if (typeof currenttemp.rate !== 'undefined' && (currenttemp.duration > 5 && rate >= currenttemp.rate * 0.8)) {
            rT.reason += esc_text(", temp " + round(currenttemp.rate,2) + " ~< req " + rate + "U/hr. ");
            return rT;
        } else {
            // calculate a long enough zero temp to eventually correct back up to target
            if ( rate <=0 ) {
                bgUndershoot = target_bg - naive_eventualBG;
                worstCaseInsulinReq = bgUndershoot / sens;
                durationReq = round(60*worstCaseInsulinReq / profile.current_basal);
                if (durationReq < 0) {
                    durationReq = 0;
                // don't set a temp longer than 120 minutes
                } else {
                    durationReq = round(durationReq/30)*30;
                    durationReq = Math.min(120,Math.max(0,durationReq));
                }
                //console.error(durationReq);
                if (durationReq > 0) {
                    rT.reason += ", setting " + durationReq + "m zero temp. ";
                    return tempBasalFunctions.setTempBasal(rate, durationReq, profile, rT, currenttemp);
                }
            } else {
                rT.reason += ", setting " + round(rate,2) + "U/hr. ";
            }
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }
    }

    // if eventual BG is above min but BG is falling faster than expected Delta
    if (minDelta < expectedDelta) {
        // if in SMB mode, don't cancel SMB zero temp
        if (! (microBolusAllowed && enableSMB)) {
            if (glucose_status.delta < minDelta) {
                rT.reason += esc_text("Eventual " + (sens_predType !="BGL" ? sens_predType : "") + " BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Delta " + convert_bg(tick, profile) + " < Exp. Delta " + convert_bg(expectedDelta, profile));
            } else {
                rT.reason += esc_text("Eventual " + (sens_predType !="BGL" ? sens_predType : "") + " BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Min. Delta " + minDelta.toFixed(2) + " < Exp. Delta " + convert_bg(expectedDelta, profile));
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + round(currenttemp.rate,2) + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }
    }
    // eventualBG or minPredBG is below max_bg
    if (Math.min(eventualBG,minPredBG) < max_bg) {
        // if in SMB mode, don't cancel SMB zero temp
        if (! (microBolusAllowed && enableSMB )) {
            rT.reason += convert_bg(eventualBG, profile)+"-"+convert_bg(minPredBG, profile)+" in range: no temp required";
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + round(currenttemp.rate,2) + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }
    }

    // eventual BG is at/above target
    // if iob is over max, just cancel any temps
    if ( eventualBG >= max_bg ) {
        rT.reason += esc_text("Eventual " + (sens_predType !="BGL" ? sens_predType : "") + " BG " + convert_bg(eventualBG, profile) + " >= " +  convert_bg(max_bg, profile) + ", ");
    }
    if (iob_data.iob > max_iob) {
        rT.reason += esc_text("IOB " + round(iob_data.iob,2) + " > max_iob " + max_iob);
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + round(currenttemp.rate,2) + " ~ req " + basal + "U/hr. ";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + basal + " as temp. ";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

        // insulinReq is the additional insulin required to get minPredBG down to target_bg
        //console.error(minPredBG,eventualBG);
        insulinReq = round( (Math.min(minPredBG,eventualBG) - target_bg) / sens_future, 2);
        // if that would put us over max_iob, then reduce accordingly
        if (insulinReq > max_iob-iob_data.iob) {
            rT.reason += "max_iob " + max_iob + ", ";
            insulinReq = max_iob-iob_data.iob;
        }

        // rate required to deliver insulinReq more insulin over 30m:
        rate = basal + (2 * insulinReq);
        rate = round_basal(rate, profile);
        insulinReq = round(insulinReq,3);
        rT.insulinReq = insulinReq;
        //console.error(iob_data.lastBolusTime);
        // minutes since last bolus
        var lastBolusAge = round(( new Date(systemTime).getTime() - iob_data.lastBolusTime ) / 60000,1);
        //console.error(lastBolusAge);
        //console.error(profile.temptargetSet, target_bg, rT.COB);
        // only allow microboluses with COB or low temp targets, or within DIA hours of a bolus
        if (microBolusAllowed && enableSMB && bg > threshold) {
            // never bolus more than maxSMBBasalMinutes worth of basal
            var mealInsulinReq = round( meal_data.mealCOB / carb_ratio ,3);
            if (typeof profile.maxSMBBasalMinutes === 'undefined' ) {
                var maxBolus = round( profile.current_basal * 30 / 60 ,1);
                console.error("profile.maxSMBBasalMinutes undefined: defaulting to 30m");
            // if IOB covers more than COB, limit maxBolus to 30m of basal
            } else if ( iob_data.iob > mealInsulinReq && iob_data.iob > 0 ) {
                console.error("IOB",iob_data.iob,"> COB",meal_data.mealCOB+"; mealInsulinReq =",mealInsulinReq);
                if (profile.maxUAMSMBBasalMinutes) {
                    console.error("profile.maxUAMSMBBasalMinutes:",profile.maxUAMSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                    maxBolus = round( profile.current_basal * profile.maxUAMSMBBasalMinutes / 60 ,1);
                } else {
                    console.error("profile.maxUAMSMBBasalMinutes undefined: defaulting to 30m");
                    maxBolus = round( profile.current_basal * 30 / 60 ,1);
                }
            } else {
                console.error("profile.maxSMBBasalMinutes:",profile.maxSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                maxBolus = round( profile.current_basal * profile.maxSMBBasalMinutes / 60 ,1);
            }


            // ============  EATING NOW MODE  ==================== START ===
            var insulinReqPctDefault = 0.65; // this is the default insulinReqPct and maxBolus is respected outside of eating now
            var insulinReqPct = insulinReqPctDefault; // this is the default insulinReqPct and maxBolus is respected outside of eating now
            var insulinReqOrig = insulinReq;
            var ENReason = "";
            var ENMaxSMB = maxBolus; // inherit AAPS maxBolus
            var maxBolusOrig = maxBolus;
            var ENinsulinReqPct = 0.75; // EN insulinReqPct is 75%

            // START === if we are eating now and BGL prediction is higher than normal target ===
            if (ENactive && eventualBG > target_bg) {
                ENReason = ""; //blank boost reason to prepare for boost info

                // EN insulinReqPct is now used
                insulinReqPct = ENinsulinReqPct;

                // set EN SMB limit for COB or UAM
                // ISFBooost maxBolus is COB outside of COB Window
                // UAMBoost maxBolus is for predictions that are UAM with or without COB
                ENMaxSMB = (sens_predType == "COB" ? profile.COB_maxBolus : profile.UAM_maxBolus);

                // if ENWindowOK allow further increase max of SMB within the window
                if (ENWindowOK) {
                    if (COB) {
                        ENMaxSMB = (firstMealWindow ? profile.Win_COB_maxBolus_breakfast : profile.Win_COB_maxBolus);
                        ENReason += ", Recent COB " + (profile.temptargetSet && target_bg == normalTarget ? " + TT" : "") + " ENW-SMB";
                    } else {
                        ENMaxSMB = (firstMealWindow ? profile.Win_UAM_maxBolus_breakfast : profile.Win_UAM_maxBolus);
                    }
                }

                // ============== MAXBOLUS RESTRICTIONS ==============
                // if ENMaxSMB is more than 0 then use it else AAPS maxBolus
                ENMaxSMB = ( ENMaxSMB > profile.safety_maxbolus ? profile.current_basal * ENMaxSMB / 60 : ENMaxSMB);
                ENMaxSMB = ( ENMaxSMB > 0 ? ENMaxSMB : maxBolus );

                // ============== DELTA & IOB BASED RESTRICTIONS ==============
                // if the delta is increasing allow larger SMB, COB predictions and COB window are always allowed larger SMB
                // IOB should be positive to cater for unexpected sudden jumps relating to basal unless COB as above
                var DeltaPctThreshold = 1.10;
                if ((DeltaPct > DeltaPctThreshold && iob_data.iob > maxBolus * 0.75) || sens_predType == "COB" || ENWindowOK) {
                    insulinReqPct = insulinReqPct;
                    ENMaxSMB = ENMaxSMB;
                    if (DeltaPct > DeltaPctThreshold && !ENWindowOK) ENReason += ", DeltaPct > " + round(DeltaPctThreshold*100) + "% EN" + (ENWindowOK ? "W" : "") + "-SMB";
                } else {
                    // prevent SMB when below target for UAM rises Hypo Rebound Protection :)
                    insulinReqPct = (bg < target_bg ? 0 : insulinReqPct);
                    ENReason += (insulinReqPct == 0 ? ", HypoSafety Restrict SMB" : "");
                    ENReason += (iob_data.iob < maxBolus * 0.75 && insulinReqPct != 0  ? ", Low IOB Restrict SMB" : "");
                    ENMaxSMB = Math.min(maxBolus,ENMaxSMB); // use the most restrictive
                }
                // ===================================================

                if (ENtimeOK) {
                    // increase maxbolus if we are within the hours specified
                    maxBolus = round(ENMaxSMB,1);
                    insulinReqPct = insulinReqPct;
                } else {
                    // Default insulinReqPct at night
                    insulinReqPct = insulinReqPctDefault;
                    // default SMB
                    maxBolus = round(maxBolus,1);
                }

                // ============== IOB RESTRICTION  ==============
                if (insulinReq > max_iob-iob_data.iob) {
                    insulinReq = round(max_iob-iob_data.iob,2);
                }
            }
            // END === if we are eating now and BGL prediction is higher than normal target ===
            // ============  EATING NOW MODE  ==================== END ===

            // boost insulinReq and maxBolus if required limited to ENMaxSMB
            var roundSMBTo = 1 / profile.bolus_increment;
            var microBolus = Math.floor(Math.min(insulinReq * insulinReqPct ,maxBolus)*roundSMBTo)/roundSMBTo;

            // calculate a long enough zero temp to eventually correct back up to target
            var smbTarget = target_bg;
            worstCaseInsulinReq = (smbTarget - (naive_eventualBG + minIOBPredBG)/2 ) / sens;
            durationReq = round(60*worstCaseInsulinReq / profile.current_basal);

            // Nightmode TBR when below SMBbgOffset with no low TT / no COB
            if (!ENactive && !ENtimeOK && bg < SMBbgOffset && meal_data.mealCOB==0)  {
                microBolus = 0;
            }

            // if insulinReq > 0 but not enough for a microBolus, don't set an SMB zero temp
            if (insulinReq > 0 && microBolus < profile.bolus_increment) {
                durationReq = 0;
            }

            var smbLowTempReq = 0;
            if (durationReq <= 0) {
                durationReq = 0;
            // don't set an SMB zero temp longer than 60 minutes
            } else if (durationReq >= 30) {
                durationReq = round(durationReq/30)*30;
                durationReq = Math.min(60,Math.max(0,durationReq));
            } else {
                // if SMB durationReq is less than 30m, set a nonzero low temp
                smbLowTempReq = round( basal * durationReq/30 ,2);
                durationReq = 30;
            }
            rT.reason += " insulinReq " + insulinReq + (insulinReq > insulinReqOrig ? "(" + insulinReqOrig + ")" : "") + "@"+round(insulinReqPct*100,0)+"%";
            if (microBolus >= maxBolus) {
                rT.reason +=  "; maxBolus" + (maxBolus > maxBolusOrig ? "^ ": " ") + maxBolus;
            }
            if (durationReq > 0) {
                rT.reason += "; setting " + durationReq + "m low temp of " + smbLowTempReq + "U/h";
            }
            rT.reason += ". ";
            rT.reason += ENReason;
            rT.reason = esc_text(rT.reason) + ". ";

            //allow SMBs every 3 minutes by default
            var SMBInterval = 3;
            if (profile.SMBInterval) {
                // allow SMBIntervals between 1 and 10 minutes
                SMBInterval = Math.min(10,Math.max(1,profile.SMBInterval));
            }
            var nextBolusMins = round(SMBInterval-lastBolusAge,0);
            var nextBolusSeconds = round((SMBInterval - lastBolusAge) * 60, 0) % 60;
            //console.error(naive_eventualBG, insulinReq, worstCaseInsulinReq, durationReq);
            console.error("naive_eventualBG",naive_eventualBG+",",durationReq+"m "+smbLowTempReq+"U/h temp needed; last bolus",lastBolusAge+"m ago; maxBolus: "+maxBolus);
            if (lastBolusAge > SMBInterval) {
                if (microBolus > 0) {
                    rT.units = microBolus;
                    rT.reason += "Microbolusing " + microBolus + "/" + maxBolus + "U.";
                }
            } else {
                rT.reason += "Waiting " + nextBolusMins + "m " + nextBolusSeconds + "s to microbolus again. ";
            }
            //rT.reason += ". ";

            // if no zero temp is required, don't return yet; allow later code to set a high temp
            if (durationReq > 0) {
                rT.rate = smbLowTempReq;
                rT.duration = durationReq;
                return rT;
            }

        }

        var maxSafeBasal = tempBasalFunctions.getMaxSafeBasal(profile);

        if (rate > maxSafeBasal) {
            rT.reason += "adj. req. rate: "+round(rate, 2)+" to maxSafeBasal: "+maxSafeBasal+", ";
            rate = round_basal(maxSafeBasal, profile);
        }

        insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
        if (insulinScheduled >= insulinReq * 2) { // if current temp would deliver >2x more than the required insulin, lower the rate
            rT.reason += esc_text(currenttemp.duration + "m@" + (currenttemp.rate).toFixed(2) + " > 2 * insulinReq. Setting temp basal of " + rate + "U/hr. ");
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (typeof currenttemp.duration === 'undefined' || currenttemp.duration === 0) { // no temp is set
            rT.reason += "no temp, setting " + rate + "U/hr. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (currenttemp.duration > 5 && (round_basal(rate, profile) <= round_basal(currenttemp.rate, profile))) { // if required temp <~ existing temp basal
            rT.reason += esc_text("temp " + round(currenttemp.rate,2) + " >~ req " + rate + "U/hr. ");
            return rT;
        }

        // required temp > existing temp basal
        rT.reason += esc_text("temp " + round(currenttemp.rate,2) + " < " + rate + "U/hr. ");
        return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
    }

};

module.exports = determine_basal;
