/*
  Determine Basal

  Released under MIT license. See the accompanying LICENSE.txt file for
  full terms and conditions

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO ER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
*/


var round_basal = require('../round-basal')
var bgValues = [];
var now = new Date();
var deltaValues = [];
var isReduced = false; // Initialiser la variable globale
var reductionCount = 0;

function processNewBGValue(newBG) {
    if (newBG < 70) {
        reductionCount = 10;
    }

    if (reductionCount > 0) {
        // Définir isReduced à true si reductionCount est plus grand que 0
        isReduced = true;

        // Décrémenter le compteur
        reductionCount--;
    } else {
        // Définir isReduced à false si reductionCount est égal à 0
        isReduced = false;
    }
}

function getMedianBG(newBG) {
    var hours = now.getHours();

    // Si nous sommes entre minuit (0 heure) et 6 heures du matin, on utilise la médiane
    if (hours >= 0 && hours < 6) {
        // Ajoutez la nouvelle valeur à la liste et triez la liste
        bgValues.push(newBG);
        bgValues.sort();

        // Choisissez la valeur médiane
        var medianBG;
        if (bgValues.length % 2 === 0) {
            medianBG = (bgValues[bgValues.length / 2 - 1] + bgValues[bgValues.length / 2]) / 2;
        } else {
            medianBG = bgValues[(bgValues.length - 1) / 2];
        }

        // Retirez la valeur la plus ancienne si la liste est trop longue
        if (bgValues.length > 5) {
            bgValues.shift();
        }

        return medianBG;
    } else {
        // Pendant la journée, retournez simplement la valeur actuelle
        return newBG;
    }
}

function getMedianDelta(newDelta) {
    // Ajoutez la nouvelle valeur à la liste et triez la liste
    deltaValues.push(newDelta);
    deltaValues.sort();

    // Choisissez la valeur médiane
    var medianDelta;
    if (deltaValues.length % 2 === 0) {
        medianDelta = (deltaValues[deltaValues.length / 2 - 1] + deltaValues[deltaValues.length / 2]) / 2;
    } else {
        medianDelta = deltaValues[(deltaValues.length - 1) / 2];
    }

    // Retirez la valeur la plus ancienne si la liste est trop longue
    if (deltaValues.length > 5) {
        deltaValues.shift();
    }

    return medianDelta;
}
// Fonctions
function calculerMicroBolus(AIMI_UAM_CAP,microBolus, max_iob, iob_data) {
    return Math.min(AIMI_UAM_CAP,microBolus, max_iob - iob_data.iob);
}

function calculerMicroBolusSelonBG(minPredBG, eventualBG, target_bg, future_sens) {
    return Math.min(AIMI_UAM_CAP, round((Math.min(minPredBG, eventualBG) - target_bg) / future_sens, 2));
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

var determine_basal = function determine_basal(glucose_status, currenttemp, iob_data, profile, autosens_data, meal_data, tempBasalFunctions, microBolusAllowed, reservoir_data, currentTime, flatBGsDetected) {

    var rT = {}; //short for requestedTemp
    var deliverAt = new Date();
    //variable for step
    var countsteps = profile.key_use_countsteps;
    var recentSteps5Minutes = profile.recentSteps5Minutes;
    var recentSteps10Minutes = profile.recentSteps10Minutes;
    var recentSteps30Minutes = profile.recentSteps30Minutes;
    var recentSteps60Minutes = profile.recentSteps60Minutes;
    //variables B30 : forcer la basale en fonction des conditions
    var b30upperLimit = profile.b30_upperBG;
    var b30upperdelta = profile.b30_upperdelta;
    // variables for deltas
    var delta = getMedianDelta(glucose_status.delta);
    var shortAvgDelta = glucose_status.short_avgdelta;
    var longAvgDelta = glucose_status.long_avgdelta;

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
     bg = getMedianBG(bg);
    var noise = glucose_status.noise;
    var aimisafesensor = false;
    // 38 is an xDrip error state that usually indicates sensor failure
    // all other BG values between 11 and 37 mg/dL reflect non-error-code BG values, so we should zero temp for those
    if (bg <= 10 || bg === 38 || noise >= 3) {  //Dexcom is in ??? mode or calibrating, or xDrip reports high noise
        rT.reason = "CGM is calibrating, in ??? state, or noise is high";
        aimisafesensor = true;
    }
    if (minAgo > 12 || minAgo < -5) { // Dexcom data is too old, or way in the future
        rT.reason = "If current system time "+systemTime+" is correct, then BG data is too old. The last BG data was read "+minAgo+"m ago at "+bgTime;
    // if BG is too old/noisy, or is changing less than 1 mg/dL/5m for 45m, cancel any high temps and shorten any long zero temps
    //cherry pick from oref upstream dev cb8e94990301277fb1016c778b4e9efa55a6edbc
    } else if ( bg > 60 && flatBGsDetected) {
        if ( glucose_status.last_cal && glucose_status.last_cal < 3 ) {
            rT.reason = "CGM was just calibrated";
            aimisafesensor = true;

        } /*else {
            rT.reason = "Error: CGM data is unchanged for the past ~45m";
        }*/
    }


    if (bg <= 10 || bg === 38 || noise >= 3 || minAgo > 12 || minAgo < -5 || ( bg > 60 && flatBGsDetected )){
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
            rT.reason += ". Temp " + currenttemp.rate + " <= current basal " + round(basal, 2) + "U/hr; doing nothing. ";
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
    //var sensitivityTDD;
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
if (profile.temptargetSet) {
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

    //variable AIMI
    var aimi_activity = countsteps === true && recentSteps5Minutes > 100 && recentSteps10Minutes > 200 || countsteps === true && recentSteps5Minutes >= 0 && recentSteps30Minutes >= 1000 ? true : false;
    var TDD = profile.tdd7Days === null || profile.tdd7Days === undefined ? profile.TDD : profile.key_tdd7;
    var insulinDivisor = profile.insulinDivisor;
    var variable_sens = profile.variable_sens;
    var lastHourTIRLow = profile.lastHourTIRLow;
    var lastHourTIRAbove = profile.lastHourTIRAbove;
    var last2HourTIRAbove = profile.last2HourTIRAbove;
    //var aimisensitivity = profile.aimisensitivity;
    var TimeSMB = round(( new Date(systemTime).getTime() - meal_data.lastBolusSMBTime ) / 60000,1);
    var AIMI_UAM = target_bg >= 130 || aimi_activity === true ? false : profile.enable_AIMI_UAM;
    var countSMB = meal_data.countSMB;
    var AIMI_IgnoreCOB = profile.key_use_AimiIgnoreCOB;
    var AIMI_COB = AIMI_IgnoreCOB ? 0 : meal_data.mealCOB;
    var AIMI_IOBpredBGbf = profile.key_use_AimiIOBpredBG;
    var lastbolusAge = round(( new Date(systemTime).getTime() - meal_data.lastBolusNormalTime ) / 60000,1);
    var enlog = "";
    var aimi_bg = bg;
    var aimi_delta = glucose_status.delta;
    if (glucose_status.delta >= 0 && lastbolusAge > 100){//&& bg > b30upperLimit
    aimi_bg = (bg + (bg - glucose_status.delta))/2;
    aimi_delta = ((bg - aimi_bg) + glucose_status.delta)/2;
    }else if (glucose_status.delta < 0 && lastbolusAge > 100){//&& bg > b30upperLimit
    aimi_bg = (bg + (bg + glucose_status.delta))/2;
    aimi_delta = ((bg - aimi_bg) + glucose_status.delta)/2;
    }

    var LastManualBolus = meal_data.lastBolusNormalUnits;
    var date_now = new Date();
    var now = new Date();
    var nowdec = round(now.getHours() + now.getMinutes() / 60, 2);
    var nowminutes = date_now.getHours() + date_now.getMinutes() / 60 + date_now.getSeconds() / 60 / 60;
    nowminutes = round(nowminutes,2);
    enlog += "nowminutes = " +nowminutes+" ; \n";

    //var circadian_sensitivity = 1;
    var enable_circadian = profile.key_use_enable_circadian;
    var circadian_sensitivity = (0.00000379*Math.pow(nowminutes,5))-(0.00016422*Math.pow(nowminutes,4))+(0.00128081*Math.pow(nowminutes,3))+(0.02533782*Math.pow(nowminutes,2))-(0.33275556*nowminutes)+1.38581503;
    var circadian_smb = round((0.00000379*delta*Math.pow(nowminutes,5))-(0.00016422*delta*Math.pow(nowminutes,4))+(0.00128081*delta*Math.pow(nowminutes,3))+(0.02533782*delta*Math.pow(nowminutes,2))-(0.33275556*delta*nowminutes)+1.38581503,2);

    circadian_sensitivity = round(circadian_sensitivity,2);
    var iTimeActivation = AIMI_UAM && aimisafesensor === false ? true : false;
    var insulinPeakTime = 60;
    // add 30m to allow for insulin delivery (SMBs or temps)
    insulinPeakTime = 90;
    insulinPeakTime = enable_circadian === true ? insulinPeakTime * circadian_sensitivity : insulinPeakTime;

    var AIMI_lastBolusSMBUnits = meal_data.lastBolusSMBUnits;

    var C1 = bg + glucose_status.delta;
    var C2 = (profile.min_bg * 1.618)-(glucose_status.delta * 1.618);

    enlog += "\nC1 = "+C1+" and C2 = "+C2;

    var b30activity = iob_data.iob - iob_data.basaliob;
    console.log("\nb30activity : "+round(b30activity,2)+" ; ");


    basal = enable_circadian === true ? basal / circadian_sensitivity : basal;
    basal = Math.max(profile.current_basal * 0.5,basal);
    enlog += enable_circadian === true ? "Basal circadian_sensitivity factor : "+basal+"\n" : "";

    if (lastbolusAge < profile.b30_duration && LastManualBolus >= 0){
         rT.reason += ". force basal because iTime is running and lesser than "+profile.b30_duration+" minutes : "+(profile.current_basal*10/60)*profile.b30_duration+" U, remaining time : " +(profile.b30_duration - lastbolusAge);
         rT.temp = 'absolute';
         rT.deliverAt = deliverAt;
         rT.duration = profile.b30_duration;
         rate = round_basal(profile.current_basal*10,profile);
         rT.rate = rate;
         rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate) + " Force Basal AIMI";
         //return tempBasalFunctions.setTempBasal(basal*10, profile.b30_duration, profile, rT, currenttemp);
         return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
     }else if(lastbolusAge > profile.key_mbi &&  meal_data.countSMB40 === 2 && glucose_status.delta >0 && circadian_smb > (-2) && circadian_smb < 1){
         rT.reason += ". force basal because you receive 2 SMB : 10 minutes" +(profile.current_basal*delta/60)*10;
         rT.deliverAt = deliverAt;
         rT.temp = 'absolute';
         rT.duration = 30;
         rate = round_basal(profile.current_basal*delta,profile);
         rT.rate = rate;
         rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate) + " Force Basal AIMI";
         return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
     }else if(lastbolusAge > profile.key_mbi &&  bg < 130 &&  bg > 70 && glucose_status.delta > 0 && circadian_smb > (-2) && circadian_smb < 1){
               rT.reason += ". force basal because bg < 130 and the rise is small" +(profile.current_basal*delta/60)*10;
               rT.deliverAt = deliverAt;
               rT.temp = 'absolute';
               rT.duration = 30;
               rate = round_basal(profile.current_basal*delta,profile);
               rT.rate = rate;
               rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate) + " Force Basal AIMI";
               return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
     }

    if ( meal_data.TDDAIMI3 ){
        var currentTIRLow = round(meal_data.currentTIRLow,2);
        var currentTIRinRange = round(meal_data.currentTIRRange,2);
        var currentTIRAbove = round(meal_data.currentTIRAbove,2);
        enlog +="TDD  : "+TDD+"\n";
    if (meal_data.TDDAIMI3){
    var TDDaverage3 = meal_data.TDDAIMI3;
    }else{
    var TDDaverage3 = ((basal * 12)*100)/21;
    }
    var MagicNumber = profile.sens*TDDaverage3*profile.min_bg;
    enlog += "TDDaverage3("+TDDaverage3+") and MagicNumber("+MagicNumber+")\n";
    sens = variable_sens;
    sens = lastHourTIRLow > 0 ? sens*1.618 : sens;
    sens = glucose_status.delta < 0 && iTimeActivation && aimi_bg < 150 ? profile.sens : sens;
    enlog += lastHourTIRLow > 0 || C1 < C2 && !iTimeActivation || iTimeActivation && glucose_status.delta > 0 ? " ; sens TDD after adjustment depending of C1-C2-aimi-TIRLow : "+sens+ " \n" : " \n";
    }else{
    if (enable_circadian === true){
    sens = iTimeActivation && glucose_status.delta > 15 ? profile.sens / 2 : Math.max(profile.sens * circadian_sensitivity,profile.sens/2);
    enlog += iTimeActivation && glucose_status.delta > 15 ? "ISF from profile divide by 2 because iTimeActivation && glucose_status.delta > 15 :"+sens+" \n" : "######--TDD and TIR don't have data, the ISF come from the profile--######\n";
    }else{
    sens = iTimeActivation && glucose_status.delta > 15 ? profile.sens / 2 : profile.sens;
    enlog += iTimeActivation && glucose_status.delta > 15 ? "ISF from profile divide by 2 because iTimeActivation && glucose_status.delta > 15 :"+sens+" \n" : "######--TDD and TIR don't have data, the ISF come from the profile--######\n";
    }
   }
    var mineRatio = profile.carb_ratio/2;
    var eRatio = round(Math.max(mineRatio,sens / 13.2),2);
    var csf = profile.sens / profile.carb_ratio ;
    var Hypo_ratio = 1;

     if (currentTIRLow > 10 || circadian_smb > (-3) ){
     var hypo_target = 100 * Math.max(1,circadian_sensitivity);
     enlog += "target_bg from "+target_bg+" to "+hypo_target+" because currentTIRLow > 5 : "+currentTIRLow+"\n";

     target_bg = circadian_smb > 5 ? 144 : hypo_target+circadian_smb;
     Hypo_ratio = 0.7;
     enlog += "Hypo_ratio : "+Hypo_ratio+"\n";
     C2 = (target_bg * 1.618)-(glucose_status.delta * 1.618);
     enlog += "C2 change because of hypo_target : "+C2+"\n";
     halfBasalTarget = 160;
     var c = halfBasalTarget - normalTarget;
     sensitivityRatio = c/(c+target_bg-normalTarget);
     sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
     sensitivityRatio = round(sensitivityRatio,2);
     enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";
     sens = target_bg > min_bg * 1.10 ? sens * 1.618 : sens;
     basal = circadian_smb > 5 ? profile.current_basal / 2 : profile.current_basal * sensitivityRatio;
     basal = round_basal(basal, profile);
     if (basal !== profile_current_basal) {
         enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
     } else {
         enlog +="Basal unchanged: "+basal+";\n";
     }
     }

    //set activity behavior
       if (aimi_activity === true){
       target_bg = 140;
       sensitivityRatio = c/(c+target_bg-normalTarget);
       sensitivityRatio = round(sensitivityRatio,2);
       enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";

       basal = profile.current_basal * sensitivityRatio;
       basal = round_basal(basal, profile);
       if (basal !== profile_current_basal) {
           enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
       } else {
           enlog +="Basal unchanged: "+basal+";\n";
       }
        rT.reason += ", aimi_activity : "+aimi_activity+", basal activity : "+basal;
       }else if (countsteps === true && recentSteps30Minutes > 500 && recentSteps5Minutes >= 0){
       target_bg = 120;
       sensitivityRatio = c/(c+target_bg-normalTarget);
       sensitivityRatio = round(sensitivityRatio,2);
       enlog +="Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+";\n";
       basal = profile.current_basal * sensitivityRatio;
       basal = round_basal(basal, profile);
       if (basal !== profile_current_basal) {
          enlog +="Adjusting basal from "+profile_current_basal+" to "+basal+";\n";
       } else {
          enlog +="Basal unchanged: "+basal+";\n";
       }
       rT.reason += ", recentSteps30Minutes : "+recentSteps30Minutes+", basal activity : "+basal;
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
    } else if ( bg > max_bg && profile.adv_target_adjustments && !profile.temptargetSet ) {
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
        ,'variable_sens' : sens
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
    console.error("CR :",eRatio);
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
        remainingCATimeMin = Math.max(remainingCATimeMin, AIMI_COB/assumedCarbAbsorptionRate);
        var lastCarbAge = round(( new Date(systemTime).getTime() - meal_data.lastCarbTime ) / 60000);
        console.error(meal_data.lastCarbTime, lastCarbAge);


        var fractionCOBAbsorbed = ( meal_data.carbs - AIMI_COB ) / meal_data.carbs;
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
    var remainingCarbs = Math.max(0, AIMI_COB - totalCA - meal_data.carbs*remainingCarbsIgnore);
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
    if (ci === 0 || AIMI_IgnoreCOB) {
        // avoid divide by zero
        cid = 0;
    } else {
        cid = Math.min(remainingCATime*60/5/2,Math.max(0, AIMI_COB * csf / ci ));
    }
    var acid = Math.max(0, AIMI_COB * csf / aci );
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
            if (!TDD){
            IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI + predDev;
             //IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + (round(( -iobTick.activity * (1800 / ( TDD * (Math.log((Math.max( IOBpredBGs[IOBpredBGs.length-1],39) / insulinDivisor ) + 1 ) ) )) * 5 ),2));
            }else{
            // calculate insulin activity
            var insulin_activity = -iobTick.activity * (1800 / (TDD * (Math.log((Math.max(IOBpredBGs[IOBpredBGs.length-1], 39) / insulinDivisor) + 1))));

            // round to 2 decimal places and multiply by 5
            insulin_activity = round(insulin_activity * 5, 2);

            // add the insulin activity to the last element of IOBpredBGs
            IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + insulin_activity;
            }
            // calculate predBGs with long zero temp without deviations
            if ( !TDD){
            var ZTpredBG = ZTpredBGs[ZTpredBGs.length-1] + predZTBGI;
            //var ZTpredBG = ZTpredBGs[ZTpredBGs.length-1] + (round(( -iobTick.iobWithZeroTemp.activity * (1800 / ( TDD * (Math.log(( Math.max(ZTpredBGs[ZTpredBGs.length-1],39) / insulinDivisor ) + 1 ) ) )) * 5 ), 2));
            }else{
            // calculate insulin activity with zero temp
            var insulin_activity = -iobTick.iobWithZeroTemp.activity * (1800 / (TDD * (Math.log((Math.max(ZTpredBGs[ZTpredBGs.length-1], 39) / insulinDivisor) + 1))));

            // round to 2 decimal places and multiply by 5
            insulin_activity = round(insulin_activity * 5, 2);

            // add the insulin activity to the last element of ZTpredBGs
            var ZTpredBG = ZTpredBGs[ZTpredBGs.length-1] + insulin_activity;
            }

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
            var predUCIslope = Math.max(0, uci + (UAMpredBGs.length*slopeFromDeviations) );
            // if slopeFromDeviations is too flat, predicted deviation impact drops linearly from
            // current deviation down to zero over 3h (data points every 5m)
            var predUCImax = Math.max(0, uci * ( 1 - UAMpredBGs.length/Math.max(3*60/5,1) ) );
            //console.error(predUCIslope, predUCImax);
            // predicted CI from UAM is the lesser of CI based on deviationSlope or DIA
            var predUCI = Math.min(predUCIslope, predUCImax);
            if(predUCI>0) {
                //console.error(UAMpredBGs.length,slopeFromDeviations, predUCI);
                UAMduration = round((UAMpredBGs.length+1)*5/60,1);
            }
            if (!TDD){
            UAMpredBG = UAMpredBGs[UAMpredBGs.length-1] + predBGI + Math.min(0, predDev) + predUCI;

            //console.error(predBGI, predCI, predUCI);
            }else{
            if (AIMI_UAM === false || profile.accelerating_up === 0 && bg < 140) {
                // calculate insulin activity
                 var insulin_activity = -iobTick.activity * (1800 / (TDD * (Math.log((Math.max(IOBpredBGs[IOBpredBGs.length-1], 39) / insulinDivisor) + 1))));

                // round to 2 decimal places and multiply by 5
                insulin_activity = round(insulin_activity * 5, 2);

                // add the insulin activity to the last element of IOBpredBGs
                UAMpredBG = IOBpredBGs[IOBpredBGs.length-1] + insulin_activity;
            } else {
                // calculate insulin activity
                var insulin_activity = -iobTick.activity * (1800 / (TDD * (Math.log((Math.max(UAMpredBGs[UAMpredBGs.length-1], 39) / insulinDivisor) + 1))));

                // round to 2 decimal places and multiply by 5
                insulin_activity = round(insulin_activity * 5, 2);

                // add the insulin activity to the last element of UAMpredBGs
                UAMpredBG = UAMpredBGs[UAMpredBGs.length-1] + insulin_activity;

                // add the minimum value between 0 and predDev
                UAMpredBG += Math.min(0, predDev);

                // add predUCI
                UAMpredBG += predUCI;
            }
            }



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

            //enlog += "insulinPeakTime : "+insulinPeakTime+"\n";
            // add 30m to allow for insulin delivery (SMBs or temps)
            //insulinPeakTime = 90;
            var insulinPeak5m = (insulinPeakTime/60)*12;
            //enlog += "insulinPeak5m : "+insulinPeak5m+"\n";
            //console.error(insulinPeakTime, insulinPeak5m);//, profile.insulinPeakTime, profile.curve
            //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m);
            // wait 90m before setting minIOBPredBG
            if ( IOBpredBGs.length > insulinPeak5m && (IOBpredBG < minIOBPredBG) ) { minIOBPredBG = round(IOBpredBG); }
            if ( IOBpredBGs.length > insulinPeak5m && (IOBpredBG < minIOBPredBG) ) { minIOBPredBG = round(UAMpredBG); }
            if ( IOBpredBG > maxIOBPredBG ) { maxIOBPredBG = IOBpredBG; }

            // wait 85-105m before setting COB and 60m for UAM minPredBGs
            if ( (cid || remainingCIpeak > 0) && COBpredBGs.length > insulinPeak5m && (COBpredBG < minCOBPredBG) ) { minCOBPredBG = round(COBpredBG); }
            if ( (cid || remainingCIpeak > 0) && COBpredBG > maxIOBPredBG ) { maxCOBPredBG = COBpredBG; }
            if ( enableUAM && UAMpredBGs.length > 12 && (UAMpredBG < minUAMPredBG) ) { minUAMPredBG = round(UAMpredBG); }
            if ( enableUAM && UAMpredBG > maxIOBPredBG ) { maxUAMPredBG = UAMpredBG; }
            //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m+" prediction : "+curvepred * 5+" minutes");
        });
        //console.log("insulinPeakTime : "+insulinPeakTime+" and insulinPeak5m : "+insulinPeak5m+" minutes");

        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
    } catch (e) {
        console.error("Problem with iobArray.  Optional feature Advanced Meal Assist disabled");
    }
    if (AIMI_COB) {
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
    if (AIMI_COB > 0) {
        aCOBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = round(Math.min(401,Math.max(39,p)));
        });
        for (i=aCOBpredBGs.length-1; i > 12; i--) {
            if (aCOBpredBGs[i-1] !== aCOBpredBGs[i]) { break; }
            else { aCOBpredBGs.pop(); }
        }
    }
    if (AIMI_COB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
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

    var AIMI_ISF = profile.key_use_AimiUAM_ISF;
    if (TDD){

        if(AIMI_ISF && AIMI_UAM && !iTimeActivation){

            var future_sens = ( (MagicNumber/1.618) / (TDD * bg));
            console.log("*****Future state sensitivity is " +future_sens+" based on bg("+bg+")\n");

        }else if( AIMI_UAM && aimi_delta >= 10 && iTimeActivation ) {
        var future_sens = ( MagicNumber / (TDD * ( (UAMpredBG * 0.6) + (bg * 0.4) )));
        console.log("Future state sensitivity is " +future_sens+" based on a weighted average of bg & UAMpredBG");
        }else if (iTimeActivation && aimi_delta < 10){
        var future_sens = ( MagicNumber / (TDD * UAMpredBG));
        console.log("Future state sensitivity is " +future_sens+" based on UAMpredBG  due to -ve delta")
        ;
        }else{
        var future_sens = sens;
        }
    }else{
    var future_sens = sens;
    }
    future_sens = iTimeActivation && lastHourTIRLow > 0 ? round(future_sens * 1.618,1) :
    round(future_sens);
    future_sens = iTimeActivation && glucose_status.delta < 0 && bg < 130 ? profile.sens : round(future_sens);
var TimeSMB = round(( new Date(systemTime).getTime() - meal_data.lastBolusSMBTime ) / 60000,1);

    minIOBPredBG = Math.max(39,minIOBPredBG);
    minCOBPredBG = Math.max(39,minCOBPredBG);
    minUAMPredBG = Math.max(39,minUAMPredBG);
    minPredBG = round(minIOBPredBG);

    var fractionCarbsLeft = AIMI_COB/meal_data.carbs;
    // if we have COB and UAM is enabled, average both
    if ( minUAMPredBG < 999 && minCOBPredBG < 999 ) {
        // weight COBpredBG vs. UAMpredBG based on how many carbs remain as COB
        avgPredBG = round((1-fractionCarbsLeft) * UAMpredBG + fractionCarbsLeft * COBpredBG );
    // if UAM is disabled, average IOB and COB
    } else if ( minCOBPredBG < 999 ) {
        avgPredBG = round((IOBpredBG + COBpredBG) / 2 );
    // if we have UAM but no COB, average IOB and UAM
    } else if ( minUAMPredBG < 999 ) {
        avgPredBG = round((IOBpredBG + UAMpredBG)/2 );
    } else {
        avgPredBG = round(IOBpredBG);
    }
    if (AIMI_IgnoreCOB && enableUAM) avgPredBG = round((IOBpredBG + UAMpredBG) / 2);  //MD#01: If weare ignoring COB and we have UAM, average IOB and UAM as above
    // if avgPredBG is below minZTGuardBG, bring it up to that level
    if ( minZTGuardBG > avgPredBG ) {
        avgPredBG = minZTGuardBG;
    }

    // if we have both minCOBGuardBG and minUAMGuardBG, blend according to fractionCarbsLeft
    if ( (cid || remainingCIpeak > 0) ) {
        if ( enableUAM ) {
            minGuardBG = fractionCarbsLeft * minCOBGuardBG + (1-fractionCarbsLeft) * minUAMGuardBG;
        } else {
            minGuardBG = minCOBGuardBG;
        }
    } else if ( enableUAM ) {
        minGuardBG = minUAMGuardBG;
    } else {
        minGuardBG = minIOBGuardBG;
    }
    if (AIMI_IgnoreCOB && enableUAM) minGuardBG = minUAMGuardBG; //MD#01: if we are ignoring COB andhave UAM just use minUAMGuardBG as above
    minGuardBG = round(minGuardBG);
    var minGuardBG_orig = minGuardBG;
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
        var blendedMinZTGuardBG = minUAMPredBG * blendPct + minZTGuardBG * (1-blendPct);
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
        if (!enableUAM && minCOBPredBG < 999) {
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG));
        // if we have COB, use minCOBPredBG, or blendedMinPredBG if it's higher
        } else if (minCOBPredBG < 999) {
            // calculate blendedMinPredBG based on how many carbs remain as COB
            var blendedMinPredBG = fractionCarbsLeft*minCOBPredBG + (1-fractionCarbsLeft)*minZTUAMPredBG;
            // if blendedMinPredBG > minCOBPredBG, use that instead
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG, blendedMinPredBG));
        // if carbs have been entered, but have expired, use minUAMPredBG
        } else if (enableUAM) {
            minPredBG = minZTUAMPredBG;
        } else {
            minPredBG = minGuardBG;
        }
    // in pure UAM mode, use the higher of minIOBPredBG,minUAMPredBG
    } else if (enableUAM) {
        minPredBG = round(Math.max(minIOBPredBG,minZTUAMPredBG));
    }
    if (AIMI_IgnoreCOB && enableUAM) minPredBG = round(Math.max(minIOBPredBG, minZTUAMPredBG)); //MD#01 If weare ignoring COB with UAM enabled use pure UAM mode like above
    // make sure minPredBG isn't higher than avgPredBG
    minPredBG = Math.min(minPredBG, avgPredBG);

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
    if (maxCOBPredBG > bg && !AIMI_IgnoreCOB) {
        minPredBG = Math.min(minPredBG, maxCOBPredBG);
    }

    rT.COB=meal_data.mealCOB;
    rT.IOB=iob_data.iob;
    rT.reason="COB: " + round(meal_data.mealCOB, 1) + ", Dev: " + convert_bg(deviation, profile) + ", BGI: " + convert_bg(bgi, profile) + ", ISF: " + convert_bg(sens, profile) + ", CR: " + round(profile.carb_ratio, 2) + ", eRatio: " + eRatio + ", Target: " +convert_bg(target_bg, profile) + ",minPredBG " + convert_bg(minPredBG, profile) + ", minGuardBG " + convert_bg(minGuardBG, profile) + ", IOBpredBG " + convert_bg(lastIOBpredBG, profile);
    if (lastCOBpredBG > 0) {
        rT.reason += ", COBpredBG " + convert_bg(lastCOBpredBG, profile);
    }
    if (lastUAMpredBG > 0) {
        rT.reason += ", UAMpredBG " + convert_bg(lastUAMpredBG, profile)
    }
    var dia = profile.dia;
    var aimiDIA = round(dia * 60 * circadian_sensitivity,2);

        rT.reason += "; ";
        rT.reason += "================================================================="
        rT.reason +=" , Variant AIMI-AI 19/08/2023 3.2.0-dev-k";
        rT.reason += ", Glucose : BG("+bg+"), TargetBG("+target_bg+"), Delta("+delta+"), shortavg delta("+shortAvgDelta+"), long avg delta("+longAvgDelta+"), accelerating_up("+profile.accelerating_up+"), deccelerating_up("+profile.deccelerating_up+"), accelerating_down("+profile.accelerating_down+"),decelerating_down("+profile.deccelerating_down+"), stable("+profile.stable+")";
        //rT.reason += ", IOB : "+iob_data.iob+"U, tdd 7d/h("+profile.tdd7DaysPerHour+"), tdd 2d/h("+profile.tdd2DaysPerHour+"), tdd daily/h("+profile.tddPerHour+"), tdd 24h/h("+profile.tdd24HrsPerHour+"), TDD("+TDD+")";
        rT.reason += ", IOB : " + iob_data.iob + "U, tdd 7d/h(" + (profile.tdd7DaysPerHour || 0) + "), tdd 2d/h(" + (profile.tdd2DaysPerHour || 0) + "), tdd daily/h(" + (profile.tddPerHour || 0) + "), tdd 24h/h(" + (profile.tdd24HrsPerHour || 0) + "), TDD(" + (TDD || 0) + ")";
        rT.reason += ", Dia : "+aimiDIA+" minutes, MaxSMB : "+profile.mss+" u ";
        rT.reason += ", Profile : Hour of the day("+profile.hourOfDay+"), Weekend("+profile.weekend+"), recentSteps5Minutes("+profile.recentSteps5Minutes+"), recentSteps10Minutes("+profile.recentSteps10Minutes+"), recentSteps15Minutes("+profile.recentSteps15Minutes+"), recentSteps30Minutes("+profile.recentSteps30Minutes+"), recentSteps60Minutes("+profile.recentSteps60Minutes+")";
        rT.reason += enable_circadian === true ? ", circadian_sensitivity : "+circadian_sensitivity : "";
        rT.reason += ",circadian_smb test : "+circadian_smb;
        //rT.reason += ",aimismb test de valeur : "+aimi_smb;
        rT.reason += ", TIR : "+currentTIRLow+" %, "+currentTIRinRange+" %, "+currentTIRAbove+"%";
        rT.reason += (profile.modelai === true ? ", The ai model predicted SMB of "+profile.predictedSMB+"u after safety requirements and rounding to .05, requested "+profile.smbToGive+"u to the pump" : "The ai model need a file which is missing");

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
    if (AIMI_COB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
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
    if ( maxDelta > 0.20 * bg && iTimeActivation === false){
        console.error("maxDelta",convert_bg(maxDelta, profile),"> 20% of BG",convert_bg(bg, profile),"- disabling SMB");
        rT.reason += "maxDelta "+convert_bg(maxDelta, profile)+" > 20% of BG "+convert_bg(bg, profile)+": SMB disabled; ";
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
    var COBforCarbsReq = Math.max(0, AIMI_COB - 0.25*meal_data.carbs);
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
            if (aimi_delta < minDelta) { //#MT AIMI
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
        if (microBolusAllowed && enableSMB && bg > threshold) { //#MT AIMI
            // never bolus more than maxSMBBasalMinutes worth of basal
            var mealInsulinReq = round( AIMI_COB / eRatio ,3);
            var smb_max_range = profile.smb_max_range_extension;
                        //if (smb_max_range > 1) {
                        //    console.error("SMB max range extended from default by factor", smb_max_range)
                        //}
                        if (typeof profile.maxSMBBasalMinutes === 'undefined' ) {
                            var maxBolus = round(smb_max_range * profile.current_basal * 30 / 60 ,1);
                            console.error("profile.maxSMBBasalMinutes undefined: defaulting to 30m");
                        // if IOB covers more than COB, limit maxBolus to 30m of basal
                        } else if ( iob_data.iob > mealInsulinReq && iob_data.iob > 0 ) {
                            console.error("IOB",iob_data.iob,"> COB",AIMI_COB+"; mealInsulinReq =",mealInsulinReq);
                            if (profile.maxUAMSMBBasalMinutes) {
                                console.error("profile.maxUAMSMBBasalMinutes:",profile.maxUAMSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                                maxBolus = round(basal * profile.maxUAMSMBBasalMinutes / 60 ,1);
                            } else {
                                console.error("profile.maxUAMSMBBasalMinutes undefined: defaulting to 30m");
                                maxBolus = round(basal * 30 / 60 ,1);
                            }
                        } else {
                            console.error("profile.maxSMBBasalMinutes:",profile.maxSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                            maxBolus = round(basal * profile.maxSMBBasalMinutes / 60 ,1);
                        }


            var maxBolusTT = maxBolus;
            var AIMI_UAM_CAP = profile.mss;
            if (profile.key_use_enable_mssv){
            if (lastHourTIRLow >= 5 && circadian_smb > 0) {
            AIMI_UAM_CAP *= 0.7;
            }else if (circadian_smb > 3){
            AIMI_UAM_CAP *= 0.7;
            }else if (circadian_smb < -1 && lastbolusAge > profile.key_mbi && now.getHours() > 8){
            AIMI_UAM_CAP = (profile.current_basal)*((profile.key_use_AIMI_CAP+100)/100);
            }
            }
            rT.reason += ", Max Smb Size = "+AIMI_UAM_CAP;
            var roundSMBTo = 1 / profile.bolus_increment;
            var nosmb = false;
            var AI = false;

            if (!profile.temptargetSet && delta > 0) {
                insulinReq = ((1 + Math.sqrt(aimi_delta)) / 2);
                var microBolus = Math.min(AIMI_UAM_CAP, insulinReq);

                if (circadian_smb > -7) {
                    if (profile.modelai === true && profile.smbToGive > 0) {
                        microBolus = profile.smbToGive;
                        AI = true;
                        rT.reason += ", AIMI_AI is running.";
                    } else if (profile.modelai === false) {
                        microBolus = calculerMicroBolusSelonBG(minPredBG, eventualBG, target_bg, future_sens);
                    }
                } else if (profile.smbToGive < 0 && bg > 150 && profile.modelai === true && delta > 5 && meal_data.countSMB40 === 0) {
                    microBolus = calculerMicroBolusSelonBG(minPredBG, eventualBG, target_bg, future_sens);
                    rT.reason += ", ModelAI send a smb required " + profile.smbToGive + "u < 0 in a situation which normally need a smb, thanks to train the model again.";
                } else if (circadian_smb <= -7) {
                    microBolus = Math.min(AIMI_UAM_CAP, insulinReq * 2);
                } else if (circadian_smb > -1 && bg > 200) {
                    microBolus = calculerMicroBolusSelonBG(minPredBG, eventualBG, target_bg, future_sens);
                }

                microBolus = calculerMicroBolus(AIMI_UAM_CAP,microBolus, max_iob, iob_data);

                if ((meal_data.MaxSMBcount >= 1 || meal_data.countSMB40 > 2 || meal_data.countSMB40 === 0) && profile.accelerating_up === 0 && lastbolusAge > profile.key_mbi) {
                    var M1 = microBolus / 3;
                    var M2 = microBolus / 2;
                    var nosmb;


                    if (bg > 170 && delta > 0) {
                        microBolus = AIMI_lastBolusSMBUnits > M2 && meal_data.countSMB40 >= 3 ? M1 : M2;
                    } else if (bg < 130 && delta <= 10) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 6 ? (nosmb = true) : M1;
                    } else if (bg < 130 && delta > 10) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 7 && meal_data.countSMB40 >= 2 ? M1 : M2;
                    } else if (bg > 130 && delta > 10) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 7 && meal_data.countSMB40 >= 2 ? M2 : microBolus;
                    } else if (bg > 130 && delta <= 10) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 6 ? (nosmb = true) : M1;
                    }

                    // Condition pour détecter rapidement une augmentation de la valeur bg
                    if (bg > 150 && delta > 5) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 4 ? M2 : microBolus;
                    }
                    var lastFourValues = now.getHours() < 11 ? UAMpredBGs.slice(-7) : UAMpredBGs.slice(-3);
                    var areValuesClose = true;
                    var arerisingagain = true;

                    for (var i = 0; i < lastFourValues.length; i++) {
                        for (var j = i + 1; j < lastFourValues.length; j++) {
                            var difference = Math.abs(lastFourValues[i] - lastFourValues[j]);
                            if (difference > 5) {
                                areValuesClose = false;
                                break;
                            }else if (difference < 10 && bg < 160){
                                arerisingagain = false;
                            }
                        }
                        if (!areValuesClose) {
                            break;
                        }
                        if (!arerisingagain){
                            break;
                        }
                    }

                    if (areValuesClose) {
                        console.log("Les 4 dernières valeurs sont proches à +/- 5");

                    } else {
                        console.log("Les 4 dernières valeurs ne sont pas proches à +/- 5");
                    }
                     rT.reason += ", areValuesClose : "+areValuesClose+", arerisingagain : "+arerisingagain;
                    // Condition pour détecter une progression lente du bg
                    if (bg >= 130 && areValuesClose === true) {
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 8 ? M1 : microBolus;
                    }else if(arerisingagain === true){
                        microBolus = AIMI_lastBolusSMBUnits > M1 && TimeSMB <= 8 ? M2 : microBolus;
                    }
                }



            }
            microBolus = microBolus > (max_iob - microBolus) ? (max_iob - microBolus) : microBolus;
            var newBG = bg; // Obtenir la nouvelle valeur BG
            processNewBGValue(newBG); // Appeler la fonction avec la nouvelle valeur BG
            microBolus = isReduced ? microBolus * 0.6 : microBolus;
            microBolus = Math.floor(microBolus * roundSMBTo) / roundSMBTo;
            rT.reason += ",isReduced : "+isReduced;


            //var microBolus = Math.floor(Math.min(insulinReq * insulinReqPCT,maxBolusTT)*roundSMBTo)/roundSMBTo;
            // calculate a long enough zero temp to eventually correct back up to target
        var smbTarget = target_bg;
            worstCaseInsulinReq = (smbTarget - (naive_eventualBG + minIOBPredBG)/2 ) / sens;
            durationReq = round(30*worstCaseInsulinReq / basal);

        if (profile.nightSMBdisable === true && now.getHours() >= profile.NoSMBStart && now.getHours() <= profile.NoSMBEnd){
            microBolus = 0;
            rT.reason += ", No SMB during the night, option is enable on the interval you define in the settings, ";
        }else if (UAMpredBG < 110){
            microBolus = 0;
            rT.reason += ", No SMB because UAMpredBG < 100, ";
        }else if (bg < target_bg && delta < -2){
            microBolus = 0;
            rT.reason += ", No SMB because bg < target_bg && delta < -2, ";
        }else if (bg < (target_bg - 15) && shortAvgDelta <= 2){
            microBolus = 0;
            rT.reason += ", No SMB because bg < (target_bg - 15) && shortAvgDelta <= 2, ";
        }else if (bg < 90){
            microBolus = 0;
            rT.reason += ", No SMB because bg < 90, ";
        }else if (bg < 150 && delta < -5){
            microBolus = 0;
            rT.reason += ", No SMB because bg < 150 && delta < -5, ";
        }else if (nosmb === true){
            microBolus = 0;
            rT.reason += ", No SMB = true => force basal, ";
        }else if (delta <= b30upperdelta && bg < b30upperLimit && lastbolusAge > profile.key_mbi){
            microBolus = 0;
            rT.reason += ", B30 decision : No SMB = true => force basal, ";
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

            rT.reason += " insulinReq " + insulinReq;


            if (microBolus >= maxBolus) {
                rT.reason +=  "; maxBolusTT " + maxBolusTT;
            }
            if (durationReq > 0) {
                rT.reason += "; setting " + durationReq + "m low temp of " + smbLowTempReq + "U/h";
            }
            rT.reason += ". ";
            //allow SMBs every 3 minutes by default
            var SMBInterval = 3;
            if (profile.SMBInterval) {
                // allow SMBIntervals between 1 and 10 minutes
                SMBInterval = Math.min(10,Math.max(1,profile.SMBInterval));
            }

            if (profile.key_use_countsteps === true && profile.recentSteps30Minutes > 900 && profile.recentSteps5Minutes >= 0 && profile.accelerating_up === 0){
            SMBInterval = 15;
            rT.reason += ", the smb interval change for "+SMBInterval+" minutes because the steps number > 900 : "+profile.recentSteps30Minutes;
            }else if(meal_data.countSMB40 > 3 && circadian_smb > -7 && lastbolusAge > profile.key_mbi ){
            SMBInterval = 15;
            rT.reason += ", the smb interval change for "+SMBInterval+" minutes because accelerating_up = 0 and you receive more than 3 SMB with a circadian_SMB > -7";
            }else if (delta>-3 && delta<3 && shortAvgDelta>-3 && shortAvgDelta<3 && longAvgDelta>-3 && longAvgDelta<3 && bg < 180 && lastbolusAge > profile.key_mbi ){
            SMBInterval = 15;
            rT.reason += ", the smb interval change for "+SMBInterval+" minutes because bg is stable";
            }else if (circadian_smb > 0 && lastbolusAge > profile.key_mbi ){
            SMBInterval = 15;
            rT.reason += ", the smb interval change for "+SMBInterval+" minutes because circadian_smb ("+circadian_smb+") > 0";
            }else if(lastbolusAge > profile.key_mbi &&  bg < 130 && glucose_status.delta > 0 && circadian_smb > (-2) && circadian_smb < 1){
            SMBInterval = 10;
            rT.reason += ", the smb interval change for "+SMBInterval+" minutes because circadian_smb ("+circadian_smb+") > 0";
            }
            rT.reason += "SMBInterval : "+SMBInterval+" ; ";
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
        if (delta>-3 && delta<3 && shortAvgDelta>-3 && shortAvgDelta<3 && longAvgDelta>-3 && longAvgDelta<3 && bg > 150){
            rT.reason += ". force basal because it's stable but bg > 150 : "+(basal*10/60)*30+" U";
            //rT.deliverAt = deliverAt;
            var durationReq = 30;
            rT.duration = durationReq;
            var rate = round_basal(basal*10,profile);
            //return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);

        }else if (iTimeActivation === true && delta > 0 && delta <= b30upperdelta && bg <= b30upperLimit && bg < 200) {
            if(bg < 100 && delta <= 5 && delta > 0) {
                rT.reason += ". force basal because AIMI is running and delta < 6 : "+(basal*delta/60)*30;
                var durationReq = 30;
                rT.duration = durationReq;
                var rate = round_basal(basal*delta,profile);
            } else if (bg > 0.8 * b30upperLimit && bg < b30upperLimit) {
                rT.reason += ". force basal because AIMI is running and delta < 6 : "+(basal*8/60)*30;
                var durationReq = 30;
                rT.duration = durationReq;
                var rate = round_basal(basal*8,profile);
            }
            //return tempBasalFunctions.setTempBasal(rate, 15, profile, rT, currenttemp);
        } else if (iTimeActivation === true && delta > 0 && delta <= 5 && bg >= 150 && bg < 200) {
            rT.reason += ". force basal because AIMI is running and delta < 6 : "+(basal*delta/60)*30;
            var durationReq = 30;
            rT.duration = durationReq;
            var rate = round_basal(basal*delta,profile);
            //return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }else if (aimi_activity === false && delta > 0 && profile.tddlastHrs !== null && profile.tdd7DaysPerHour !== null && profile.tddlastHrs < profile.tdd7DaysPerHour) {
             rT.reason += ". force basal because tddlastHrs < tdd7DaysPerHours : " + (profile.tdd7DaysPerHour - profile.tddlastHrs);
             var durationReq = 30;
             rT.duration = durationReq;
             var rate = round_basal(profile.tdd7DaysPerHour - profile.tddlastHrs, profile);

        //return tempBasalFunctions.setTempBasal(rate, 15, profile, rT, currenttemp);
        }else if(nosmb === true && delta > 0){
                rT.reason += ". force basal because no smb = true : "+(basal * 3);
                var durationReq = 30;
                rT.duration = durationReq;
                var rate = round_basal(basal * 3,profile);
                //return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }



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

