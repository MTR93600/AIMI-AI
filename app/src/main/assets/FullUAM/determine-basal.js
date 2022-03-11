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


var round_basal = require('../round-basal')

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
    } else if (! profile.allowSMB_with_high_temptarget && profile.temptargetSet && target_bg > 100) {
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
    if (profile.enableSMB_with_temptarget === true && (profile.temptargetSet && target_bg < 100)) {
        if (meal_data.bwFound) {
            console.error("Warning: SMB enabled within 6h of using Bolus Wizard: be sure to easy bolus 30s before using Bolus Wizard");
        } else {
            console.error("SMB enabled for temptarget of",convert_bg(target_bg, profile));
        }
        return true;
    }

    console.error("SMB disabled (no enableSMB preferences active or no condition satisfied)");
    return true;
}

function determine_varSMBratio(profile, bg, target_bg)
{   // mod 12: let SMB delivery ratio increase f#rom min to max depending on how much bg exceeds target
    if ( typeof profile.smb_delivery_ratio_bg_range === 'undefined' || profile.smb_delivery_ratio_bg_range === 0 ) {
        // not yet upgraded to this version or deactivated in SMB extended menu
        console.error('SMB delivery ratio set to fixed value', profile.smb_delivery_ratio);
        return profile.smb_delivery_ratio;
    }
    var lower_SMB = Math.min(profile.smb_delivery_ratio_min, profile.smb_delivery_ratio_max);
    if (bg <= target_bg) {
        console.error('SMB delivery ratio limited by minimum value', lower_SMB);
        return lower_SMB;
    }
    var higher_SMB = Math.max(profile.smb_delivery_ratio_min, profile.smb_delivery_ratio_max);
    var higher_bg = target_bg + profile.smb_delivery_ratio_bg_range;
    if (bg >= higher_bg) {
        console.error('SMB delivery ratio limited by maximum value', higher_SMB);
        return higher_SMB;
    }
    var new_SMB = lower_SMB + (higher_SMB - lower_SMB)*(bg-target_bg) / profile.smb_delivery_ratio_bg_range;
    console.error('SMB delivery ratio set to interpolated value', new_SMB);
    return new_SMB;
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
    var minAgo = round((systemTime - bgTime) / 60 / 1000 ,1);

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
        } else if (currenttemp.rate === 0 && currenttemp.duration > 30) { //shorten long zero temps to 30m
            rT.reason += ". Shortening " + currenttemp.duration + "m long zero temp to 30m. ";
            rT.deliverAt = deliverAt;
            rT.temp = 'absolute';
            rT.duration = 30;
            rT.rate = 0;
            return rT;
            //return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
        } else { //do nothing.
            rT.reason += ". Temp " + currenttemp.rate + " <= current basal " + basal + "U/hr; doing nothing. ";
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
    var normalTarget = 100; // evaluate high/low temptarget against 100, not scheduled target (which might change)
    if ( profile.half_basal_exercise_target ) {
        var halfBasalTarget = profile.half_basal_exercise_target;
    } else {
        halfBasalTarget = 160; // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%)
        // 80 mg/dL with low_temptarget_lowers_sensitivity would give 1.5x basal, but is limited to autosens_max (1.2x by default)
    }
    if ( high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget ) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        //console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+"; ");
    } else if (typeof autosens_data !== 'undefined' && autosens_data) {
        sensitivityRatio = autosens_data.ratio;
        console.log("Autosens ratio: "+sensitivityRatio+"; ");
    }
    if (sensitivityRatio) {
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal !== profile_current_basal) {
            //console.log("Adjusting basal from "+profile_current_basal+" to "+basal+"; ");
        } else {
            console.log("Basal unchanged: "+basal+"; ");
        }
    }



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
    if (typeof autosens_data !== 'undefined' && autosens_data) {
        sens = profile.sens / sensitivityRatio;
        sens = round(sens, 1);
        if (sens !== profile_sens) {
            console.log("ISF from "+profile_sens+" to "+sens);
        } else {
            console.log("ISF unchanged: "+sens);
        }
        //console.log(" (autosens ratio "+sensitivityRatio+")");
    }
    /* ************************
       ** TS AutoTDD code    **
       ************************ */
    var enlog = "";
    var now = new Date().getHours();
        if (now < 1){
            now = 1;}
        else {
            console.error("Time now is "+now+"; ");
        }
    var circadian_sensitivity = 1;
    if (now >= 0 && now < 2){
        //circadian_sensitivity = 1.4;
        now = Math.max(now,0.5);
        circadian_sensitivity = (0.09130*Math.pow(now,3))-(0.33261*Math.pow(now,2))+1.4;
    }else if (now >= 2 && now < 3){
         //circadian_sensitivity = 0.8;
         circadian_sensitivity = (0.0869*Math.pow(now,3))-(0.05217*Math.pow(now,2))-(0.23478*now)+0.8;
    }else if (now >= 3 && now < 8){
         //circadian_sensitivity = 0.8;
         circadian_sensitivity = (0.0007*Math.pow(now,3))-(0.000730*Math.pow(now,2))-(0.0007826*now)+0.6;
    }else if (now >= 8 && now < 11){
         //circadian_sensitivity = 0.6;
         circadian_sensitivity = (0.001244*Math.pow(now,3))-(0.007619*Math.pow(now,2))-(0.007826*now)+0.4;
    }else if (now >= 11 && now < 15){
         //circadian_sensitivity = 0.8;
         circadian_sensitivity = (0.00078*Math.pow(now,3))-(0.00272*Math.pow(now,2))-(0.07619*now)+0.8;
    }else if (now >= 15 && now <= 22){
         circadian_sensitivity = 1.0;
    }else if (now >= 22 && now <= 24){
        //circadian_sensitivity = 1.2;
        circadian_sensitivity = (0.000125*Math.pow(now,3))-(0.0015*Math.pow(now,2))-(0.0045*now)+1.2;
    }
basal /= circadian_sensitivity;
enlog += "Basal circadian_sensitivity factor : "+basal+"\n";
    if ( meal_data.TDDPUMP ){
        var statTirBelow = meal_data.StatLow7;
        var statinrange = meal_data.StatInRange7;
        var currentTIRLow = meal_data.currentTIRLow;
        var CurrentTIRinRange = meal_data.currentTIRRange;
        var CurrentTIRAbove = meal_data.currentTIRAbove;
        var CurrentTIR_70_140_Above = meal_data.currentTIR_70_140_Above;
        //var tdd7 = meal_data.TDDAIMI7;
        var tdd7 = ((basal * 12)*100)/21;
        var tdd24 = meal_data.TDDLast24;
        var tdd724 = (tdd7 + tdd24)/2;
        // Experimental base on 50% basal use during a normal day,
         //which is 21% of the current TDD base on an average data
        var tdd_pump_now = meal_data.TDDPUMP;
        var tdd_pump = (tdd_pump_now / (now / 24));
        var TDD = (tdd724 * 0.4) + (tdd_pump * 0.6);
        enlog +="tdd24 : "+tdd24+"\n";
        enlog +="tdd7 : "+tdd7+"\n";
        enlog +="tdd724 : "+tdd724+"\n";
        enlog +="TDD  : "+TDD+"\n";
        enlog +="Pump extrapolated TDD = "+tdd_pump+";\n";
        var smbTDD = 0;
        if (tdd_pump < (0.3 * tdd724)) {
            TDD = (tdd724 * 0.8) + (tdd_pump * 0.2);
            smbTDD = 1;
            enlog +="tdd_pump is lesser than 30% tdd724\n";
            } else if (tdd_pump < (0.5 * tdd724)){
                TDD = (tdd724 * 0.5) + (tdd_pump * 0.5);
                smbTDD = 1;
                enlog +="TDD weighted to pump due to low insulin usage. TDD = "+TDD+";\n";
            }else{

                enlog +="TDD roling 24h and TDD average 7 (projection) ="+tdd724+", TDD Pump ="+tdd_pump+" and TDD = "+TDD+";\n";
            }


         var iTime_Start_Bolus = profile.iTime_Start_Bolus;
        var iTimeProfile = profile.iTime;
        var LastManualBolus = meal_data.lastBolusNormalUnits;
        var insulinPeakTime = 35;
        if (AIMI_UAM_U200 && C1 > C2){
        insulinPeakTime = 15 * 1.618 * circadian_sensitivity;
        //enlog += "AIMI_UAM_U200 && C1>C2 insulinPeakTime : "+insulinPeakTime+"\n";
        }else if (AIMI_UAM_U200){
        insulinPeakTime = 20 * 1.618 * circadian_sensitivity;
        //enlog += "AIMI_UAM_U200 insulinPeakTime : "+insulinPeakTime+"\n";
        }else if (AIMI_UAM_U100 && C1 > C2){
        insulinPeakTime = 20 * 1.618 * circadian_sensitivity;
        }else if (AIMI_UAM_U100){
        insulinPeakTime = 25 * 1.618 * circadian_sensitivity;
        }else if (AIMI_UAM_Fiasp && C1 > C2){
        insulinPeakTime = 25 * 1.618 * circadian_sensitivity;
        }else if (AIMI_UAM_Fiasp){
        insulinPeakTime = 30 * 1.618 * circadian_sensitivity;
        }else if (AIMI_UAM_Novorapid && C1 > C2){
        insulinPeakTime = 35 * 1.618 * circadian_sensitivity;
        }else if (AIMI_UAM_Novorapid){
        insulinPeakTime = 40 * 1.618 * circadian_sensitivity;
        }

        enlog += "circadian_sensitivity : "+circadian_sensitivity+"\n";
        //var iTime = round(( new Date(systemTime).getTime() - meal_data.lastBolusNormalTime ) / 60000,1);
        var lastbolusAge = round(( new Date(systemTime).getTime() - meal_data.lastBolusNormalTime ) / 60000,1);
        var C1 = bg + glucose_status.delta;
        var C2 = (profile.min_bg * 1.618)-(glucose_status.delta * 1.618);
        var AIMI_UAM = profile.enable_AIMI_UAM;
        var AIMI_COB = profile.key_use_AIMI_COB;
        var AIMI_UAM_U200 = profile.enable_AIMI_UAM_U200;
        var AIMI_UAM_U100 = profile.enable_AIMI_UAM_U100;
        var AIMI_UAM_Fiasp = profile.enable_AIMI_UAM_Fiasp;
        var AIMI_UAM_Novorapid = profile.enable_AIMI_UAM_Novorapid;
        var AIMI_PBolus = profile.key_use_AIMI_PBolus;
        var AIMI_BreakFastLight = profile.key_use_AIMI_BreakFastLight;
        var AIMI_BL_StartTime = profile.key_AIMI_BreakFastLight_timestart;
        var AIMI_BL_EndTime = profile.key_AIMI_BreakFastLight_timeend;
        var AIMI_lastBolusSMBUnits = meal_data.lastBolusSMBUnits;
        var AIMI_BG_ACC = glucose_status.delta / glucose_status.short_avgdelta;
        enlog += "AIMI_BG_ACC : "+AIMI_BG_ACC+"\n";
        var AIMI_ACC = false;

        if (AIMI_BG_ACC < 1 && glucose_status.delta >= 20){
        AIMI_ACC = true;
        enlog += "AIMI_ACC for fast sugar : "+AIMI_ACC+"\n";
        }


        if (now >= AIMI_BL_EndTime){
            AIMI_BreakFastLight = false;
        }
        enlog += "### AIMI_BreakFastLight = "+AIMI_BreakFastLight+" \n";

        if (AIMI_UAM && AIMI_COB && AIMI_PBolus){
            enlog += "#### YOU CAN NOT USE UAM, COB AND PBOLUS in the same time, make a choice ! \n ";
        }else if (AIMI_UAM && AIMI_COB){
           enlog += "#### YOU CAN NOT USE UAM AND COB in the same time, make a choice ! \n ";
       }else if (AIMI_UAM && AIMI_PBolus){
           enlog += "#### YOU CAN NOT USE UAM AND PBOLUS in the same time, make a choice ! \n ";
       }else if (AIMI_PBolus && AIMI_COB){
           enlog += "#### YOU CAN NOTE USE COB AND PBOLUS in the same time, make a choice ! \n";
       }else{
           enlog += "#### your settings about how AIMI manage the rise is good :-) \n";
       }


        if (AIMI_COB && !AIMI_PBolus && !AIMI_UAM && meal_data.AIMI_lastCarbUnit > 30) {

                         var lastCarbAgebis = round(( new Date(systemTime).getTime() - meal_data.AIMI_lastCarbTime ) / 60000);
                         //console.error(meal_data.lastCarbTime, lastCarbAge);
                         var iTime = lastCarbAgebis;
                         enlog +="lastCarbAgebis =  iTime : "+iTime+"\n";

        }else if (AIMI_PBolus && !AIMI_COB && !AIMI_UAM && LastManualBolus >= iTime_Start_Bolus && lastbolusAge < iTimeProfile){

                var iTime = lastbolusAge;
                enlog += "iTime is running : "+iTime+" because manual bolus ("+LastManualBolus+") >= iTime_Starting_Bolus ("+iTime_Start_Bolus+")\n";

        }else if (AIMI_PBolus && !AIMI_UAM && !AIMI_COB && LastManualBolus <= iTime_Start_Bolus && lastbolusAge < iTimeProfile){

                         iTime = iTimeProfile + 1 ;
                         enlog += "A manual bolus was done, but iTime is disable, LastManualBolus < iTime_start_bolus : "+LastManualBolus+"<"+iTime_Start_Bolus+"\n";

        }
        enlog += "C1 = "+C1+" and C2 = "+C2+"\n";





        if (iTime < profile.iTime && CurrentTIRinRange <= 96 && CurrentTIR_70_140_Above <= 20 && currentTIRLow >=4 && statinrange <= 95 && statTirBelow >= 4 && bg < 170 || smbTDD === 1 && bg < 170 ){iTimeProfile *=0.7; }



        if (CurrentTIR_70_140_Above > 20 && currentTIRLow < 5 && CurrentTIRinRange < 95 && smbTDD === 0 || smbTDD === 0 && iTime < iTimeProfile && tdd_pump >= tdd724*0.3 && CurrentTIR_70_140_Above > 20 ){
            TDD*=1.2;
            //console.log("TDD new value because TIR during the current Day show an average BG greater than 140 with a proportion greater than 20% or TDD_pump > 0.3*TTD7 && iTime < iTimeProfile  <  :"+TDD);
            enlog +="TDD new value because TIR during the current Day show an average BG greater than 140 with a proportion greater than 20% or TDD_pump > 0.3*TTD7 && iTime < iTimeProfile  <  :"+TDD+"\n";
        }else if (iTime < profile.iTime && CurrentTIRinRange <= 96 && CurrentTIR_70_140_Above <= 20 && currentTIRLow >=4 && statinrange <= 95 && statTirBelow >= 4 && bg < 170 || smbTDD === 1 && bg < 170 ){

        iTimeProfile *=0.7;
        }else if (statinrange <= 96 && statTirBelow >= 4 && CurrentTIR_70_140_Above <= 20 && currentTIRLow >= 4){
            TDD*=0.7;
            enlog +="TDD new value because TIR show hypo during the last 7 days and  the curent day too :"+TDD+"\n";
        }


        //console.log("stat Tir : "+StatLow7);
    /*var variable_sens = (277700 / (TDD * bg));
    variable_sens = round(variable_sens,1);
    //var TDDnow = meal_data.TDDAIMI1;
    console.log("Current sensitivity is " +variable_sens+" based on current bg");*/
    //console.log("####### tdd7 : "+tdd7+"##### tdd1 : "+tdd1+" ### variable_sens :
    //"+variable_sens+" ; ");
    //console.log("TDDnow : "+TDDnow+";");
    //sens = variable_sens;
    var TDDaverage3 = meal_data.TDDAIMI3;
    var MagicNumber = profile.sens*TDDaverage3*profile.min_bg;
    enlog += "TDDaverage3("+TDDaverage3+") and MagicNumber("+MagicNumber+")\n";
    var sens_normalTarget = sens; // use profile for now * EXPERIMENT *
    //var sens_TDD = round((277700 / (TDD * normalTarget)),1);
    var sens_TDD = round((MagicNumber / (TDD * normalTarget)),1);
    var sens_avg = (sens_normalTarget+sens_TDD)/2;
    var sens_normalTarget = sens_avg;
    var sens_currentBG = sens_normalTarget/(bg/normalTarget); // * EXPERIMENT *
    sens_currentBG = round(sens_currentBG,1);
    sens = sens_currentBG / circadian_sensitivity;
    enlog +="Current sensitivity is " +sens_currentBG+" based on current bg\n";
    }else{
    sens = profile.sens / circadian_sensitivity;
    enlog +="######--TDD and TIR don't have data, the ISF come from the profile--######\n";
    }

    //var eRatio = round((bg/0.16)/sens,2);

    var mineRatio = profile.carb_ratio/2;
    var eRatio = Math.max(mineRatio,sens / 13.2);


    var HypoPredBG = round( bg - (iob_data.iob * sens) ) + round( 60 / 5 * ( minDelta - round(( -iob_data.activity * sens * 5 ), 2)));
    var HyperPredBG = round( bg - (iob_data.iob * sens) ) + round( 60 / 5 * ( minDelta - round(( -iob_data.activity * sens * 5 ), 2)));
    var TriggerPredSMB = round( bg - (iob_data.iob * sens) ) + round( 240 / 5 * ( minDelta - round(( -iob_data.activity * sens * 5 ), 2)));




    var csf = profile.sens / profile.carb_ratio ;

    var EBG =Math.max(0, round((0.02 * glucose_status.delta * glucose_status.delta) + (0.58 * glucose_status.long_avgdelta) + bg,2));
    //var EBG180 = Math.max(0,round((0.02 * glucose_status.delta * glucose_status.delta) + (0.58 * glucose_status.long_avgdelta) + HyperPredBGTest2,2));
    //var EBG120 = Math.max(0,round((0.02 * glucose_status.delta * glucose_status.delta) + (0.58 * glucose_status.long_avgdelta) + HyperPredBGTest3,2));
    var EBG60 = Math.max(0,round((0.02 * glucose_status.delta * glucose_status.delta) + (0.58 * glucose_status.long_avgdelta) + HyperPredBG,2));
    var REBG = round(EBG / min_bg,2);
    var REBG60 = round(EBG60 / min_bg,2);
    var EBX = Math.max(0,round(Math.min(EBG,EBG60),2));
    var REBX = Math.max(0.5,round(Math.min(REBG60,REBG),2));
    var Hypo_ratio = 1;

     if (currentTIRLow > 10){
     var hypo_target = 100 * circadian_sensitivity;
     enlog += "target_bg from "+target_bg+" to "+hypo_target+" because currentTIRLow > 5 : "+currentTIRLow+"\n";

     target_bg = hypo_target;
     Hypo_ratio = 0.7;
     enlog += "Hypo_ratio : "+Hypo_ratio+"\n";
     C2 = (target_bg * 1.618)-(glucose_status.delta * 1.618);
     enlog += "C2 change because of hypo_target : "+C2+"\n";
     halfBasalTarget = 160;
     var c = halfBasalTarget - normalTarget;
     sensitivityRatio = c/(c+target_bg-normalTarget);
     sensitivityTDD = (TDD / tdd724);
     enlog += "sensitivityTDD : "+sensitivityTDD+"\n";
     //sensitivityRatio = REBX;
     // limit sensitivityRatio to profile.autosens_max (1.2x by default)
     sensitivityRatio = Math.min(sensitivityTDD, profile.autosens_max);
     sensitivityRatio = round(sensitivityRatio,2);
     enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";
     basal = profile.current_basal * sensitivityRatio;
     basal = round_basal(basal, profile);
     if (basal !== profile_current_basal) {
         enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
     } else {
         enlog +="Basal unchanged: "+basal+";\n";
     }
     }else if (!profile.temptargetSet && HypoPredBG <= 125 && profile.sensitivity_raises_target && C1 < C2){//&& glucose_status.delta <= 0

        var hypo_target = round(Math.min(200, min_bg + (EBG - min_bg)/3 ),0);
        hypo_target *= circadian_sensitivity;
       if (EBG <= 120 && HypoPredBG < 90) {
            hypo_target = 130 * circadian_sensitivity;
            enlog +="target_bg from "+target_bg+" to "+hypo_target+" because EBG is lesser than 100 and HypoPredBG < 80 : "+EBG+"; \n";
        }else if (EBG60 <= 90 && EBG60 >0 && smbTDD === 1) {
            hypo_target = 100 * circadian_sensitivity;
            enlog +="target_bg from "+target_bg+" to "+hypo_target+" because EBG60 is lesser than 90: "+EBG60+";\n ";
        }else if (target_bg === hypo_target) {
            enlog +="target_bg unchanged: "+hypo_target+";\n";
        }/*else{
            hypo_target = 100;
            enlog +="target_bg from "+target_bg+" to "+hypo_target+" because HypoPredBG is lesser than 125 : "+HypoPredBG+";\n";
        }*/
        target_bg = hypo_target;
        halfBasalTarget = 160;
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        sensitivityTDD = (TDD / tdd724);
        enlog += "sensitivityTDD : "+sensitivityTDD+"\n";
        //sensitivityRatio = REBX;
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityTDD, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal !== profile_current_basal) {
            enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
        } else {
            enlog +="Basal unchanged: "+basal+";\n";
        }
    } else if (!profile.temptargetSet && HyperPredBG >= 220 && profile.resistance_lowers_target && C1 > C2) {

        var hyper_target = round(Math.max(80, min_bg - (bg - min_bg)/3 ),0);
        hyper_target *= circadian_sensitivity;
        if (target_bg === hyper_target) {
            enlog +="target_bg unchanged: "+hyper_target+";\n";
        } else {
            enlog +="target_bg from "+target_bg+" to "+hyper_target+" because HyperPredBG > 180 : "+HyperPredBG+" ;\n";
        }
        target_bg = hyper_target;
        C1 = bg + (glucose_status.delta*1.618);
        C2 = target_bg * 1.618;
        halfBasalTarget = 160;
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        sensitivityTDD = (TDD / tdd724);
        enlog += "sensitivityTDD : "+sensitivityTDD+"\n";
        //sensitivityRatio = REBX;
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityTDD, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";
        if (iTime < iTimeProfile || C1 > C2 ){
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);

        if (basal !== profile_current_basal) {
            enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
        } else {
            enlog +="Basal unchanged: "+basal+";\n";
        }
        }
    }

//================= MT =====================================
    //console.log("***hypo_target : "+hypo_target+" & hyper_target : "+hyper_target);

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
    var threshold = min_bg - 0.5*(min_bg-40);

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
        //var csf = profile.sens / profile.carb_ratio;
    //} else {
        // otherwise, use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments
        // so that autotuned CR is still in effect even when basals and ISF are being adjusted by autosens
        //var csf = sens / profile.carb_ratio;
    //}
    // use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments so that
    // autotuned CR is still in effect even when basals and ISF are being adjusted by TT or autosens
    // this avoids overdosing insulin for large meals when low temp targets are active
    //var eRatio = profile.carb_ratio;

    csf = sens / eRatio;
    console.error("profile.sens:",profile.sens,"sens:",sens,"CSF:",round (csf, 2),"eRatio",eRatio);
    console.error("CR:",eRatio);
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
        console.error(meal_data.lastCarbTime, lastCarbAge);


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

            if ( iTime < iTimeProfile && IOBpredBGs.length < 24 || IOBpredBGs.length < 48) { IOBpredBGs.push(IOBpredBG); }
            if ( iTime < iTimeProfile && COBpredBGs.length < 24 || COBpredBGs.length < 48) { COBpredBGs.push(COBpredBG); }
            if ( iTime < iTimeProfile && aCOBpredBGs.length < 24 || aCOBpredBGs.length < 48) { aCOBpredBGs.push(aCOBpredBG); }
            if ( iTime < iTimeProfile && UAMpredBGs.length < 24 ||  UAMpredBGs.length < 48) { UAMpredBGs.push(UAMpredBG); }
            if ( iTime < iTimeProfile && ZTpredBGs.length < 24 || ZTpredBGs.length < 48) { ZTpredBGs.push(ZTpredBG); }
            // calculate minGuardBGs without a wait from COB, UAM, IOB predBGs
            if ( COBpredBG < minCOBGuardBG ) { minCOBGuardBG = round(COBpredBG); }
            if ( UAMpredBG < minUAMGuardBG ) { minUAMGuardBG = round(UAMpredBG); }
            if ( IOBpredBG < minIOBGuardBG ) { minIOBGuardBG = round(IOBpredBG); }
            if ( ZTpredBG < minZTGuardBG ) { minZTGuardBG = round(ZTpredBG); }



            // set minPredBGs starting when currently-dosed insulin activity will peak
            // look ahead 60m (regardless of insulin type) so as to be less aggressive on slower insulins

            //enlog += "insulinPeakTime : "+insulinPeakTime+"\n";
            // add 30m to allow for insulin delivery (SMBs or temps)
            //insulinPeakTime = 90;
            var insulinPeak5m = (insulinPeakTime/60)*12;
            //enlog += "insulinPeak5m : "+insulinPeak5m+"\n";
            //console.error(insulinPeakTime, insulinPeak5m);//, profile.insulinPeakTime, profile.curve
            //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m);
            // wait 90m before setting minIOBPredBG
            if ( IOBpredBGs.length > insulinPeak5m && (IOBpredBG < minIOBPredBG) ) { minIOBPredBG = round(IOBpredBG); }
            if ( IOBpredBG > maxIOBPredBG ) { maxIOBPredBG = IOBpredBG; }
            // wait 85-105m before setting COB and 60m for UAM minPredBGs
            if ( (cid || remainingCIpeak > 0) && COBpredBGs.length > insulinPeak5m && (COBpredBG < minCOBPredBG) ) { minCOBPredBG = round(COBpredBG); }
            if ( (cid || remainingCIpeak > 0) && COBpredBG > maxIOBPredBG ) { maxCOBPredBG = COBpredBG; }
            if ( iTime < iTimeProfile && enableUAM && UAMpredBGs.length > 6 && (UAMpredBG < minUAMPredBG) || enableUAM && UAMpredBGs.length > 12 && (UAMpredBG < minUAMPredBG) ) { minUAMPredBG = round(UAMpredBG); }
            if ( enableUAM && UAMpredBG > maxIOBPredBG ) { maxUAMPredBG = UAMpredBG; }
            //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m+" prediction : "+curvepred * 5+" minutes");
        });
        //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m+" minutes");

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
        eventualBG = Math.max(eventualBG, round(COBpredBGs[COBpredBGs.length-1]) );
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
    console.log("EventualBG is" +eventualBG+" ;");
    var TrigPredAIMI =  (TriggerPredSMB_future_sens_60 + TriggerPredSMB_future_sens_35) / 1.618;
    var AIMI_ISF = profile.key_use_AimiUAM_ISF;
    if ( meal_data.TDDPUMP ){
        //var future_sens = ( 277700 / (TDD * eventualBG));
        //var future_sens = round(future_sens,1);
        if( glucose_status.delta >= 0 && iTime < iTimeProfile ) {
        var future_sens = ( MagicNumber / (TDD * ( (eventualBG * 0.6) + (bg * 0.4) )));
        console.log("Future state sensitivity is " +future_sens+" based on a weighted average of bg & eventual bg");
        }else if (iTime < iTimeProfile){
        var future_sens = ( MagicNumber / (TDD * eventualBG));
        console.log("Future state sensitivity is " +future_sens+" based on eventual bg due to -ve delta");
        }else{
        var future_sens = sens;
        }
    }else{
    var future_sens = sens;
    }
    future_sens = round(future_sens,1);

var TriggerPredSMB_future_sens_60 = round( bg - (iob_data.iob * future_sens) ) + round( 60 / 5 * ( minDelta - round(( -iob_data.activity * future_sens * 5 ), 2)));
var TriggerPredSMB_future_sens_45 = round( bg - (iob_data.iob * future_sens) ) + round( 45 / 5 * ( minDelta - round(( -iob_data.activity * future_sens * 5 ), 2)));
var TriggerPredSMB_future_sens_35 = round( bg - (iob_data.iob * future_sens) ) + round( 35 / 5 * ( minDelta - round(( -iob_data.activity * future_sens * 5 ), 2)));
var TrigPredAIMI =  (TriggerPredSMB_future_sens_60 + TriggerPredSMB_future_sens_35) / 1.618;
//var AIMI_ISF = profile.key_use_AimiUAM_ISF;
if (AIMI_UAM && AIMI_BreakFastLight && now >= AIMI_BL_StartTime && now <= AIMI_BL_EndTime){

    var future_sens = sens;
    console.log("*****Future_sens is not use with light breakfast");

}else if(AIMI_ISF && AIMI_UAM && !AIMI_PBolus && !AIMI_COB && C1 > C2){

    //var future_sens = ( 277700 / (TDD * TrigPredAIMI));
    var future_sens = ( (MagicNumber/1.618) / (TDD * bg));
    console.log("*****Future state sensitivity is " +future_sens+" based on bg("+bg+")\n");

}
        console.log("------------------------------");
                console.log(" AAPS-MASTER-3.0.1-AIMI V17 11/03/2022 ");
                console.log("------------------------------");
                if ( meal_data.TDDPUMP ){
                console.log(enlog);
                }
                /*console.log("Pump extrapolated TDD = "+tdd_pump);
                console.log("tdd7 using 7-day average "+tdd7);
                console.log("TDD 7 ="+tdd7+", TDD Pump ="+tdd_pump+" and TDD = "+TDD);}
                console.log("Current sensitivity is " +variable_sens+" based on current bg");*/
                console.log("eRatio: "+eRatio);
                console.log("-------------");
                console.log("TriggerPredSMB : "+TriggerPredSMB);
                console.log("TriggerPredSMB_future_sens_60 : "+TriggerPredSMB_future_sens_60);
                console.log("TriggerPredSMB_future_sens_45 : "+TriggerPredSMB_future_sens_45);
                console.log("TriggerPredSMB_future_sens_35 : "+TriggerPredSMB_future_sens_35);
                console.log("TrigPredAIMI : "+TrigPredAIMI);
                console.log("EBG : "+EBG+" ; REBG : "+REBG);
                console.log("EBG60 : "+EBG60+" ; REBG60 : "+REBG60);
                console.log("HypoPredBG : "+HypoPredBG+" ; HyperPredBG : "+HyperPredBG);
                console.log("-------------");
                console.log("target_bg : "+target_bg);
                console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg);
                console.log("Adjusting basal from "+profile_current_basal+" to "+basal);
                console.log("Future state sensitivity is " +future_sens+" based on eventual bg");
                console.log("-------------");
                if ( meal_data.TDDPUMP ){
                    if (iTime < iTimeProfile){
                    console.log("iTime : "+iTime);
                    console.log("iTimeProfile : "+iTimeProfile);
                    console.log("smbTDD : "+smbTDD);
                    console.log("-------------");
                    }
                }


    //console.error(insulinPeakTime, insulinPeak5m, profile.insulinPeakTime, profile.curve);
    //console.log("curve prediction : "+curvepred);

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
    if ( maxCOBPredBG > bg ) {
        minPredBG = Math.min(minPredBG, maxCOBPredBG);
    }

    rT.COB=meal_data.mealCOB;
    rT.IOB=iob_data.iob;
    rT.reason="COB: " + round(meal_data.mealCOB, 1) + ", Dev: " + convert_bg(deviation, profile) + ", BGI: " + convert_bg(bgi, profile) + ", ISF: " + convert_bg(sens, profile) + ", CR: " + round(profile.carb_ratio, 2) + ", eRatio" + eRatio + ", Target: " + convert_bg(target_bg, profile) + ",minPredBG " + convert_bg(minPredBG, profile) + ", minGuardBG " + convert_bg(minGuardBG, profile) + ", IOBpredBG " + convert_bg(lastIOBpredBG, profile);
    if (lastCOBpredBG > 0) {
        rT.reason += ", COBpredBG " + convert_bg(lastCOBpredBG, profile);
    }
    if (lastUAMpredBG > 0) {
        rT.reason += ", UAMpredBG " + convert_bg(lastUAMpredBG, profile)
    }
    rT.reason += "; ";
    // use naive_eventualBG if above 40, but switch to minGuardBG if both eventualBGs hit floor of 39
    //var carbsReqBG = naive_eventualBG;
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
    if ( maxDelta > 0.20 * bg ) {
        console.error("maxDelta",convert_bg(maxDelta, profile),"> 20% of BG",convert_bg(bg, profile),"- disabling SMB");
        rT.reason += "maxDelta "+convert_bg(maxDelta, profile)+" > 20% of BG "+convert_bg(bg, profile)+": SMB disabled; ";
        enableSMB = false;
    }
    /*if (variable_sens >= 139) {
        console.error("The risk to make an hypoglycemia was detected, HypoPredBG :",HypoPredBG," < 100 then SMB disabled; ");
        rT.reason += "The risk to make an hypoglycemia was detected, HypoPredBG :"+HypoPredBG+" < 100 then SMB disabled; ";
        enableSMB = false;
    }*/

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
    if ( carbsReq >= profile.carbsReqThreshold && minutesAboveThreshold <= 45 ) {
        rT.carbsReq = carbsReq;
        rT.carbsReqWithin = minutesAboveThreshold;
        rT.reason += carbsReq + " add'l carbs req w/in " + minutesAboveThreshold + "m; ";
    }

    // don't low glucose suspend if IOB is already super negative and BG is rising faster than predicted
    if (bg < threshold && iob_data.iob < -profile.current_basal*20/60 && minDelta > 0 && minDelta > expectedDelta) {
        rT.reason += "IOB "+iob_data.iob+" < " + round(-profile.current_basal*20/60,2);
        rT.reason += " and minDelta " + convert_bg(minDelta, profile) + " > " + "expectedDelta " + convert_bg(expectedDelta, profile) + "; ";
    // predictive low glucose suspend mode: BG is / is projected to be < threshold
    } else if ( bg < threshold || minGuardBG < threshold ) {
        rT.reason += "minGuardBG " + convert_bg(minGuardBG, profile) + "<" + convert_bg(threshold, profile);
        bgUndershoot = target_bg - minGuardBG;
        var worstCaseInsulinReq = bgUndershoot / sens;
        var durationReq = round(60*worstCaseInsulinReq / basal);
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
        rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " < " + convert_bg(min_bg, profile);
        // if 5m or 30m avg BG is rising faster than expected delta
        if ( minDelta > expectedDelta && minDelta > 0 && !carbsReq ) {
            // if naive_eventualBG < 40, set a 30m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
            if (naive_eventualBG < 40) {
                rT.reason += ", naive_eventualBG < 40. ";
                return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
            }
            if (glucose_status.delta > minDelta) {
                rT.reason += ", but Delta " + convert_bg(tick, profile) + " > expectedDelta " + convert_bg(expectedDelta, profile);
            } else {
                rT.reason += ", but Min. Delta " + minDelta.toFixed(2) + " > Exp. Delta " + convert_bg(expectedDelta, profile);
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }

        // calculate 30m low-temp required to get projected BG up to target
        // multiply by 2 to low-temp faster for increased hypo safety
        var insulinReq = 2 * Math.min(0, (eventualBG - target_bg) / future_sens);
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
            rT.reason += ", temp " + currenttemp.rate + " ~< req " + rate + "U/hr. ";
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
                rT.reason += ", setting " + rate + "U/hr. ";
            }
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }
    }

    // if eventual BG is above min but BG is falling faster than expected Delta
    if (minDelta < expectedDelta) {
        // if in SMB mode, don't cancel SMB zero temp
        if (! (microBolusAllowed && enableSMB)) {
            if (glucose_status.delta < minDelta) {
                rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Delta " + convert_bg(tick, profile) + " < Exp. Delta " + convert_bg(expectedDelta, profile);
            } else {
                rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Min. Delta " + minDelta.toFixed(2) + " < Exp. Delta " + convert_bg(expectedDelta, profile);
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
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
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
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
        rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " >= " +  convert_bg(max_bg, profile) + ", ";
    }
    if (iob_data.iob > max_iob) {
        rT.reason += "IOB " + round(iob_data.iob,2) + " > max_iob " + max_iob;
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + basal + " as temp. ";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

        // insulinReq is the additional insulin required to get minPredBG down to target_bg
        //console.error(minPredBG,eventualBG);
        insulinReq = round( (Math.min(minPredBG,eventualBG) - target_bg) / future_sens, 2);
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
            var mealInsulinReq = round( meal_data.mealCOB / eRatio ,3);
            var smb_max_range = profile.smb_max_range_extension;
                        //if (smb_max_range > 1) {
                        //    console.error("SMB max range extended from default by factor", smb_max_range)
                        //}
                        if (typeof profile.maxSMBBasalMinutes === 'undefined' ) {
                            var maxBolus = round(smb_max_range * profile.current_basal * 30 / 60 ,1);
                            console.error("profile.maxSMBBasalMinutes undefined: defaulting to 30m");
                        // if IOB covers more than COB, limit maxBolus to 30m of basal
                        } else if ( iob_data.iob > mealInsulinReq && iob_data.iob > 0 ) {
                            console.error("IOB",iob_data.iob,"> COB",meal_data.mealCOB+"; mealInsulinReq =",mealInsulinReq);
                            if (profile.maxUAMSMBBasalMinutes) {
                                console.error("profile.maxUAMSMBBasalMinutes:",profile.maxUAMSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                                maxBolus = round(smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes / 60 ,1);
                            } else {
                                console.error("profile.maxUAMSMBBasalMinutes undefined: defaulting to 30m");
                                maxBolus = round(smb_max_range * profile.current_basal * 30 / 60 ,1);
                            }
                        } else {
                            console.error("profile.maxSMBBasalMinutes:",profile.maxSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                            maxBolus = round(smb_max_range * profile.current_basal * profile.maxSMBBasalMinutes / 60 ,1);
                        }
            var AIMI_R = 161.8;
                        /*if (profile.out_units === "mmol/L"){
                        bg /= 18;
                        target_bg /= 18;
                        sens /= 18;
                        AIMI_R /= 10;
                        future_sens /= 18;
                        glucose_status.delta /= 18;
                        console.log("data in mmol");
                        }*/
            var insulinQ = insulinReq;
            var insulinReqPCT = profile.UAM_InsulinReq/100;
            var InsulinTDD = (TDD * 0.6) / 24;
            var maxBolusTT = maxBolus;
            var roundSMBTo = 1 / profile.bolus_increment;

            var mealM = meal_data.AIMI_lastCarbUnit / 3;
            //var mealIns = mealM / profile.carb_ratio;
            var mealIns = mealM / eRatio ;
            var carbslimitsmb = meal_data.AIMI_lastCarbUnit / profile.carb_ratio;
            var limitIOB = Math.min((0.90*max_iob),((bg * 1.618) / sens));
            console.log("####limitIOB : "+limitIOB+"\n")
            var bgDegree = round((bg + glucose_status.delta) / AIMI_R,2);
            limitIOB *= bgDegree;
            var UAMAIMIReason = "";
            var AIMI_UAM_CAP = profile.key_use_AIMI_CAP;
            var smb_ratio = determine_varSMBratio(profile, bg, target_bg);


            //var TrigPredAIMI =  (TriggerPredSMB_future_sens_60 + TriggerPredSMB_future_sens_35) / 1.618;

            if (AIMI_COB && meal_data.AIMI_lastCarbUnit < 30 && meal_data.AIMI_lastCarbUnit > 0 && iob_data.iob < carbslimitsmb){
                maxBolusTT = round(smb_max_range * profile.current_basal * (profile.maxSMBBasalMinutes + glucose_status.delta + glucose_status.short_avgdelta) / 60 ,1);
                var microBolus = Math.min(insulinReq*smb_ratio, maxBolusTT);
                console.log("carbs entry < 30 maxSMBBasalMinutes is up by the delta : "+(profile.maxSMBBasalMinutes + glucose_status.delta + glucose_status.short_avgdelta)+"minutes");


            }else if (AIMI_COB && meal_data.AIMI_lastCarbUnit > 30 && meal_data.carbs && iTime <= iTimeProfile && iTime < 6 && iob_data.iob <= mealIns && iob_data.iob < carbslimitsmb){

                var microBolus = mealIns+basal;
                console.log("first mealIns shot : "+mealIns);

            }else if (AIMI_COB && meal_data.AIMI_lastCarbUnit > 30 && meal_data.carbs && iTime <= iTimeProfile && iTime > 10 && iTime < 20 && iob_data.iob <= (2*mealIns) && iob_data.iob < max_iob && iob_data.iob < carbslimitsmb){

                var microBolus = mealIns;
                console.log("second mealIns shot : "+mealIns);

            }else if (AIMI_COB && meal_data.AIMI_lastCarbUnit > 30 && meal_data.carbs && iTime <= iTimeProfile && iTime >= 40 && iTime <= 60 && iob_data.iob <= (3*mealM / profile.carb_ratio) && glucose_status.delta >= 5 && C1 > C2 && HypoPredBG > 120 && iob_data.iob < max_iob && iob_data.iob < carbslimitsmb*0.9){

                var microBolus = mealIns;
                console.log("third mealIns shot : "+mealIns);

            }else if (AIMI_COB && meal_data.AIMI_lastCarbUnit > 30 && meal_data.carbs && profile.iTime_Bolus > 0 && iTime <= iTimeProfile && C1 > C2 && glucose_status.delta >= 5 && glucose_status.long_avgdelta > 0 && iob_data.iob < iTime_Start_Bolus && ! profile.temptargetSet && HypoPredBG > 120 && iob_data.iob < max_iob && iob_data.iob < carbslimitsmb){

                var microBolus =  profile.iTime_Bolus;

            }else if (AIMI_PBolus && ! meal_data.carbs && profile.iTime_Bolus > 0 && iTime <= iTimeProfile && C1 > C2 && glucose_status.delta >= 5 && glucose_status.long_avgdelta > 0 && iob_data.iob < (2*iTime_Start_Bolus) && ! profile.temptargetSet && HypoPredBG > 120 && iob_data.iob < max_iob){

                var microBolus =  profile.iTime_Bolus;

            }else if ( AIMI_PBolus && iTime < iTimeProfile && smbTDD === 0 && ! profile.temptargetSet && HypoPredBG > 120 && iob_data.iob < max_iob){
                /*insulinReq = insulinReq + InsulinTDD + basal;
                //smb_ratio *= 3;
                maxBolusTT = round(basal * profile.iTime_MaxBolus_minutes * smb_max_range / 60 ,1);
                //maxBolusTT *= 3;
                var microBolus = Math.min(insulinReq*insulinReqPCT*smb_ratio, maxBolusTT);
                console.log("iTime_MaxBolus_minutes : "+profile.iTime_MaxBolus_minutes+ "maxBolus : "+maxBolusTT);
                //var microBolus = Math.min(insulinReq*smb_ratio*insulinReqPCT, maxBolusTT);*/
                insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);
                var microBolus = (AIMI_UAM_U100 ? Math.min(AIMI_UAM_CAP,(insulinReq * 2 * Hypo_ratio)) : Math.min(AIMI_UAM_CAP,(insulinReq * Hypo_ratio)));
                console.log("***Pbolus*** - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");


            }else if ( AIMI_COB && iTime < iTimeProfile && smbTDD === 0 && ! profile.temptargetSet && HypoPredBG > 120 && iob_data.iob < max_iob){
                 /*insulinReq = insulinReq + InsulinTDD + basal;
                 //smb_ratio *= 3;
                 maxBolusTT = round(basal * profile.iTime_MaxBolus_minutes * smb_max_range / 60 ,1);
                 //maxBolusTT *= 3;
                 var microBolus = Math.min(insulinReq*insulinReqPCT*smb_ratio, maxBolusTT);
                 console.log("iTime_MaxBolus_minutes : "+profile.iTime_MaxBolus_minutes+ "maxBolus : "+maxBolusTT);*/
                 insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);
                 var microBolus = (AIMI_UAM_U100 ? Math.min(AIMI_UAM_CAP,(insulinReq * 2 * Hypo_ratio)) : Math.min(AIMI_UAM_CAP,(insulinReq * Hypo_ratio)));
                 console.log("***COB*** - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");

            }else if (AIMI_PBolus && smbTDD === 1 || AIMI_PBolus && profile.temptargetSet && target_bg > normalTarget && iTime < iTimeProfile ){

                insulinReqPCT = 0.8;
                var microBolus = Math.min(insulinReq*insulinReqPCT, maxBolusTT);

            }else if (AIMI_UAM_U200 && !AIMI_UAM_Novorapid && !AIMI_UAM_Fiasp && !AIMI_UAM_U100 && AIMI_UAM && AIMI_BreakFastLight && now >= AIMI_BL_StartTime && now <= AIMI_BL_EndTime && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (smb_ratio*limitIOB/2)){

                       insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * smb_ratio,2);
                       //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                       var microBolus = Math.min(AIMI_UAM_CAP/2,(insulinReq/2)*Hypo_ratio);
                       console.log("***FullUAM*** U200 - Breakfast Light - InsulinReq("+insulinReq/2+"), limitIOB("+limitIOB+"), smb_ratio("+smb_ratio+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+")\n");



            }else if (!AIMI_UAM_U200 && !AIMI_UAM_Fiasp && !AIMI_UAM_Novorapid && AIMI_UAM_U100 && AIMI_UAM && AIMI_BreakFastLight && now >= AIMI_BL_StartTime && now <= AIMI_BL_EndTime && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (smb_ratio*limitIOB/2)){

                        insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * smb_ratio,2);
                        //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                        var microBolus = Math.min(AIMI_UAM_CAP/2,(insulinReq*Hypo_ratio));
                        console.log("***FullUAM*** U100 - Breakfast Light - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), smb_ratio("+smb_ratio+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+")\n");

            }else if (AIMI_UAM_U200 && !AIMI_UAM_Novorapid && !AIMI_UAM_Fiasp && !AIMI_UAM_U100 && AIMI_UAM && !AIMI_BreakFastLight && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (bgDegree*limitIOB)){

                      insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);

                      var microBolus = Math.min(AIMI_UAM_CAP,(insulinReq*Hypo_ratio));
                      console.log("***FullUAM*** U200 - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");

            }else if(!AIMI_UAM_U200 && !AIMI_UAM_Fiasp && !AIMI_UAM_Novorapid && AIMI_UAM_U100 && AIMI_UAM && !AIMI_BreakFastLight && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (bgDegree*2*limitIOB)){

                    insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);
                    var microBolus = Math.min(AIMI_UAM_CAP,(insulinReq * 2 * Hypo_ratio));
                    console.log("***FullUAM*** U100 - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");

            }else if (!AIMI_UAM_U200 && AIMI_UAM_Fiasp && !AIMI_UAM_Novorapid && !AIMI_UAM_U100 && AIMI_UAM && AIMI_BreakFastLight && now >= AIMI_BL_StartTime && now <= AIMI_BL_EndTime && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (smb_ratio*limitIOB/2)){

                      insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * smb_ratio,2);
                                     //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                      var microBolus = Math.min(AIMI_UAM_CAP/2,(insulinReq/2)*Hypo_ratio);
                      console.log("***UAM*** Fiasp - Breakfast Light - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), smb_ratio("+smb_ratio+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+")\n");

            }else if (!AIMI_UAM_U200 && !AIMI_UAM_Novorapid && AIMI_UAM_Fiasp && !AIMI_UAM_U100 && AIMI_UAM && !AIMI_BreakFastLight && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (bgDegree*limitIOB)){

                       insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);
                                   //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                       var microBolus = Math.min(AIMI_UAM_CAP,(insulinReq*Hypo_ratio));
                       console.log("***UAM*** Fiasp - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");

            }else if (!AIMI_UAM_U200 && !AIMI_UAM_Fiasp && AIMI_UAM_Novorapid && !AIMI_UAM_U100 && AIMI_UAM && AIMI_BreakFastLight && now >= AIMI_BL_StartTime && now <= AIMI_BL_EndTime && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (smb_ratio*limitIOB/2)){

                       insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * smb_ratio,2);
                                      //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                       var microBolus = Math.min(AIMI_UAM_CAP/2,(insulinReq/2)*Hypo_ratio);
                       console.log("***UAM*** Novorapid - Breakfast Light - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), smb_ratio("+smb_ratio+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+")\n");

             }else if (!AIMI_UAM_U200 && AIMI_UAM_Novorapid && !AIMI_UAM_Fiasp && !AIMI_UAM_U100 && AIMI_UAM && !AIMI_BreakFastLight && !AIMI_COB && !AIMI_PBolus && ! profile.temptargetSet && ! meal_data.carbs && TriggerPredSMB_future_sens_45 > C2 && glucose_status.delta > 0 && glucose_status.long_avgdelta > 0  && iob_data.iob <= (bgDegree*limitIOB)){

                        insulinReq = round((((glucose_status.delta * 3.1416) + (bg*0.52) ) / future_sens) * bgDegree,2);
                                    //maxBolusTT = round(((smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes * 1.618)+(glucose_status.delta * 1.618) / 60) *(insulinReq/1.618) ,1);

                        var microBolus = Math.min(AIMI_UAM_CAP,(insulinReq*Hypo_ratio));
                        console.log("***UAM*** Novorapid - InsulinReq("+insulinReq+"), limitIOB("+limitIOB+"), TriggerPredSMB_future_sens_45("+TriggerPredSMB_future_sens_45+"), bgDegree("+bgDegree+") \n");

             }else if (C1 > C2 && glucose_status.delta > 2 && bg > 180 && meal_data.lastBolusSMBUnits < (0.7 * AIMI_UAM_CAP) ){

                var microBolus =  profile.iTime_Bolus;
                console.log("****iTime_bolus value was sending****\n");

             }else{

                var microBolus = Math.min(insulinReq*smb_ratio, maxBolusTT);

            }
            microBolus = Math.floor(microBolus*roundSMBTo)/roundSMBTo;
            //var microBolus = Math.floor(Math.min(insulinReq * insulinReqPCT,maxBolusTT)*roundSMBTo)/roundSMBTo;
            // calculate a long enough zero temp to eventually correct back up to target
    if ( meal_data.TDDPUMP ){
            if (iTime < iTimeProfile ){
            console.log("--- if iTime < "+iTimeProfile+" -----");
                            console.log("TriggerPredSMB : "+TriggerPredSMB);
                            console.log("iTime : "+iTime);
                            console.log("target_bg from "+target_bg+" to "+hyper_target);
                            console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg);
                            console.log("Adjusting basal from "+profile_current_basal+" to "+basal);
                            console.log("maxBolusTT : "+maxBolusTT);
                            console.log("InsulinReqPCT : "+(insulinReqPCT * 100)+"%");
                            console.log("insulinReq : "+insulinReq);
                            if(iTime < iTimeProfile && smbTDD === 0){
                            console.log("insulinQ : "+insulinQ);
                            console.log("InsulinTDD : "+InsulinTDD);}
                            console.log("microBolus : "+microBolus);
        console.log("------------------------------");
        }
    }


            var smbTarget = target_bg;
            worstCaseInsulinReq = (smbTarget - (naive_eventualBG + minIOBPredBG)/2 ) / sens;
            durationReq = round(30*worstCaseInsulinReq / basal);

            if (meal_data.mealCOB==0 && TriggerPredSMB_future_sens_45 < 100)  {
            //if (!eatingnowtimeOK && bg < EatingNowBGThreshold && meal_data.mealCOB==0)  {
                //var maxBolus = round( profile.current_basal * 30 / 60 ,1);
                microBolus = 0;
                UAMAIMIReason += ", no SMB, ";
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
            rT.reason += " insulinReq " + insulinReq + ", InsulinReqPCT " + insulinReqPCT*100+"%, smbRatio : " + smb_ratio;
            if (microBolus >= maxBolus) {
                rT.reason +=  "; maxBolusTT " + maxBolusTT;
            }
            if (durationReq > 0) {
                rT.reason += "; setting " + durationReq + "m low temp of " + smbLowTempReq + "U/h";
            }
            rT.reason += ". ";
            rT.reason += UAMAIMIReason;

            //allow SMBs every 3 minutes by default
            var SMBInterval = 3;
            if (profile.SMBInterval) {
                // allow SMBIntervals between 1 and 10 minutes
                SMBInterval = Math.min(10,Math.max(1,profile.SMBInterval));
            }
            if (AIMI_UAM && AIMI_BreakFastLight){
            SMBInterval = 15;
            }else if (AIMI_UAM && ! profile.temptargetSet && ! meal_data.carbs && !AIMI_COB && !iTime && iob_data > 0.8 * max_iob && meal_data.lastBolusSMBUnits >= 0.8 * AIMI_UAM_CAP){
            SMBInterval = 20;
            }else if (AIMI_UAM && ! profile.temptargetSet && ! meal_data.carbs && !AIMI_COB && !iTime && iob_data > 0.5 * max_iob){
            SMBInterval = 10;
            }
            var nextBolusMins = round(SMBInterval-lastBolusAge,0);
            var nextBolusSeconds = round((SMBInterval - lastBolusAge) * 60, 0) % 60;
            //console.error(naive_eventualBG, insulinReq, worstCaseInsulinReq, durationReq);
            console.error("naive_eventualBG",naive_eventualBG+",",durationReq+"m "+smbLowTempReq+"U/h temp needed; last bolus",lastBolusAge+"m ago; maxBolus: "+maxBolus);
            if (lastBolusAge > SMBInterval) {
                if (microBolus > 0) {
                    rT.units = microBolus;
                    rT.reason += "Microbolusing " + microBolus + "U. ";
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
            rT.reason += currenttemp.duration + "m@" + (currenttemp.rate).toFixed(2) + " > 2 * insulinReq. Setting temp basal of " + rate + "U/hr. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (typeof currenttemp.duration === 'undefined' || currenttemp.duration === 0) { // no temp is set
            rT.reason += "no temp, setting " + rate + "U/hr. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (currenttemp.duration > 5 && (round_basal(rate, profile) <= round_basal(currenttemp.rate, profile))) { // if required temp <~ existing temp basal
            rT.reason += "temp " + currenttemp.rate + " >~ req " + rate + "U/hr. ";
            return rT;
        }

        // required temp > existing temp basal
        rT.reason += "temp " + currenttemp.rate + "<" + rate + "U/hr. ";
        return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
    }

};

module.exports = determine_basal;

