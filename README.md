# AndroidAPS

* Check the wiki: https://androidaps.readthedocs.io
*  Everyone who’s been looping with AndroidAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![Build status](https://travis-ci.org/nightscout/AndroidAPS.svg?branch=master)](https://travis-ci.org/nightscout/AndroidAPS)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.androidaps.org/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://androidaps.readthedocs.io/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/master/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)
dev: [![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/dev/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)


![BTC](https://bitit.io/assets/coins/icon-btc-1e5a37bc0eb730ac83130d7aa859052bd4b53ac3f86f99966627801f7b0410be.svg) 3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2

########################################
This version of AAPS has evolved over time using elements from AIMI and Boost.
This AAPS variation is called "Eating Now" (EN) as it is a reactive operating mode without needing to inform the system.
The intent of this plugin is the same, to deliver insulin earlier using mostly openAPS predictions.
This has been tested successfully with a blend of Fiasp 80% and Novorapid 20% (F80N).
The code can be used with or without bolusing or COB entries.
However it will not become active until a treatment has been performed after the active start time.
Allowing a delayed start time for example if you sleep in. :)
This treatment can be 1g or 0.1U for example.
After this the EN mode is active until the end time specified in the preferences.
It is recommended to set maxSMBBasalMinutes and maxUAMSMBBasalMinutes to 60 minutes max as these will be used when EN is not active.

These are the methods utilised within this version:

UAM:
This is based upon Boost and is used when there is a sudden increase in BGL of >=9 (0.5 mmol)
UAMBoost will only operate when there are no COB.
TDD is used as a reference point for initial insulin dose that can be scaled within preferences.

COB:
When carbs are entered there is a time window like AIMI.
The COBpredBG prediction uses the dynamic ISF from Boost to increase insulinReq.
If within the COBBoost Window the calculated insulinReq may be delivered via a larger SMB using the COBBoost maxBolus.
Once the time window has elapsed COBBoost maxBolus is no longer used.

Predictions leverage the dynamic ISF concept within the Boost plugin.
Using the eventualBG mostly to determine the insulinReq.
The main difference is the initial ISF used to determine the predictions is based on the profile ISF.
If BG is currently the normalTarget BG from the profile the ISF will be the same as the profile.
Once BG rises the ISF number reduces, and as BG lowers the ISF number will increase.
ISF scaling can be adjusted and eventualBG weighting for UAM and COB predictions can be applied.

These are the preferences utilised for EN mode:

General:
    Start Time:     The time the EN mode will start in hours as 24h clock format
                    EN mode will be active after this time when there has been a COB or manual bolus entry of any size
    End Time:       The time that EN mode will finish. Normal maxBolus of 65% is resumed.
                    If there are COB or a TT of normalTarget EN will be active after this time, however AAPS maxBolus will be used.
                    No SMB will be given when inactive unless there is detected resistance from autosens or BG is above BG Threshold.
    InsulinReqPct:  Percentage that will be used for EN insulinReq as SMB to utilise prior to maxBolus restriction.
                    This will be 65% when EN is not active.
    Max IOB:        The percentage of current max-iob setting that will be used as the limit for EN.
                    EN will not add insulin when above this limit.
    BG Threshold:   No SMB will be given when EN is outside operating hours and BG below this threshold.
                    If there is detected resistance from autosens or BG exceeds this threshold SMB will be resumed using normal AAPS maxBolus.
    ISF BG Scaler:  As BG increases ISF will become stronger. The level of scaling can be adjusted.
                    0 = normal scaling, 5 is 5% stronger, -5 is 5% weaker ISF scaling.
    Max ISF Limit:  As BG increases ISF will become stronger. This will Limit the ISF.
                    1 is profile ISF, 1.5 would be a 150% smaller ISF, similar to a profile switch.


UAM:
    UAMBoost Bolus Scale:       Multiply the initial UAMBoost bolus by this amount. 0 will disable UAMBoost.
    UAMBoost maxBolus:          maxBolus to use for all BG rises without COB.  0 will use maxSMBBasalMinutes or maxUAMSMBBasalMinutes.
    UAM eventualBG Weighting:   This will be the initial weighting for eventualBG predictions without COB.
                                As ISF grows stronger the weighting will reduce favouring current BG ISF.
                                Setting to 50 will make this 50%. 0 will always use currentBG ISF without max ISF Limit applied.
COB:
    Use GhostCOB:               Ignore COB predictions after the COBBoost Window and rely purely on UAM. This setting can be handy when COB lingers for too long.
    COBBoost InsulinReqPct:     Percentage that will be used for EN insulinReq within the COBBoost Window.
    COBBoost Window:            If within the COBBoost Window the calculated insulinReq from COBPredBG may be delivered via a larger SMB using the COBBoost maxBolus.
                                Once the time window has elapsed COBBoost maxBolus is no longer used.
                                0 minutes will disable this functionality.
    COBBoost maxBolus:          maxBolus to use within the COBBoost Window. 0 will use AAPS maxBolus.
    COB maxBolus:               maxBolus to use with COB outside of the initial COBBoost Window. 0 will use AAPS maxBolus.
    COB eventualBG Weighting:   This will be the initial weighting for eventualBG predictions with COB.
                                As ISF grows stronger the weighting will reduce favouring current BG ISF.
                                Setting to 50 will make this 50%. 0 will always use currentBG ISF without max ISF Limit applied.
