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


AIMI V2 DANARS TDD:

It's a plugin, need to be select in the config builder.
For now AIMI settings are :
-insulinReq in %
-UAM_bolusCAP
-Start and End Time

TDD code from Tim is in this version.
THE TDD will determine ISF automaticaly regarding TDD average on 7 days and the total daily unit.
CSF will be determine following the action 50 for 80 mg / 100 ml in the blood, in translation,
i will take the bg value divide by 0.16 to determine csf
IC (eRatio) will be determine by csf / TDD ISF
stile in test.
If you use it with prebolus, you will not get a scale smb, but just the ISF/CSF/IC who will play
with the bg evolution and determine the insulinreq, regarding the eventualBG value.

BOOST Plugin:

This code is designed to be used with a Circadian profile and variable ISF rates throughout the day that align with the circadian profile.

The intention of htis code is to deliver an early, larger bolus when rises are detected to intiate UAM deviations and to allow the algorithm to be more aggressive. Other than Boost, it relies on oref1 adjusted to use the variable ISF function and some SMB scaling.

All of the additional code outside of the standard SMB calculation requires a time period to be specified within which it is active. The default time settings disable the code. The time period is specified in hours in the Boost preferences section.

When an initial rise is detected with a meal, delta, short_avgDelta and long_avgDelta are used to trigger the early bolus (assuming IOB is below a user defined amount). The early bolus value is a multiple of insulin required, calculated using a multiplier that is 
`` Boost Scale Value  * ( eventualBG / target_bg ) ``

If Boost Scale Value is less than 2, Boost is enabled.

The short and long average delta clauses disable boost once delta and the average deltas are aligned. There is a preferences setting (Boost Bolus Cap) that limits the maximum bolus that can be delivered by Boost outside of the standard UAMSMBBasalMinutes limit.

If glucose levels are predicted above 180, or there is a delta > 8, then 100% of insulin required is delivered.

If no other criteria are met, and glucose level is > 108, and delta > 3, then insulin required is scaled up to 100% up to 180mg/dl.

If none of the conditions are met, standard SMB logic is used to size SMBs, with the insulin required PCT entered in prefernces.

Settings that have been added to the BOOST settings are:
Boost Bolus Cap - defaults to 0.1
UAM Boost max IOB - defaults to 0.1
UAM Boost Start Time (in hours) - defaults to 7
UAM Boost end time (in hours) - defaults to 8
