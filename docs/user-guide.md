# Cadence User Guide

## What it does

Cadence counts your steps on the phone and estimates distance, walking
calories and metabolic rate. (Full feature list coming in a later release.)

## Profile

Enter your height, weight, sex and age. These values drive the stride length
and the metabolic estimates. (Coming in a later release.)

## Today

Steps, distance and calories for the current day. Press Record to start counting; counting carries
on with the phone locked and restarts by itself after a reboot.

**Carry the phone in a pocket or on your arm.** The phone's step sensor looks for the swing of a
walking body. Held steady in front of you, it often does not recognise the steps and counts
nothing. Phones also hold back the first 10 or so steps of a walk and drop very short walks; the
first steps show at once as provisional steps until the sensor confirms them.

## Trend (coming in a later release)

## Pattern

Draws your walk step by step, without GPS. Each step moves the pen one stride in the direction the
phone faces, so hold the phone in front of you, pointing where you walk. The drawing scales to fit
as it grows. Pen up moves without drawing, so you can walk letters; New drawing starts again.
Holding the phone in front of you can stop the step sensor from counting those steps (see Today).

## Calibration (coming in a later release)

## How numbers are calculated

- **BMR** (basal metabolic rate): calculated from your profile. The formula is
  documented with the calculation code. (Coming in a later release.)
- **RMR** (resting metabolic rate): RMR = BMR x 1.1.
- **Stride length**: 0.415 x height for male profiles, 0.413 x height for
  female profiles.
- **Distance**: steps x stride length.
- **Walking calories**: 0.5 kcal per kg of body weight per km walked.

Assumptions: estimates only. Stride, BMR and calorie values are population
averages and are not a substitute for measured data. (Calibration against your
own data is coming in a later release.)

## Privacy

Cadence works fully offline. No data leaves the device: no accounts, no
uploads, no analytics.
