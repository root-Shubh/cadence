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
nothing. Phones also hold back the first 10 or so steps of a walk and drop very short walks, so
the first steps of a walk can appear a few seconds late, all at once. The number on screen is the
phone's step sensor; it does not go up and then back down while you stand still.

**Vehicles.** With location on, Cadence pauses counting when you move above 20 km/h for 20
seconds, and removes the steps counted during the ride. Counting resumes by itself when GPS sees
the ride end or, if GPS has no fix (indoors, underground), after about 20 steps of walking. You
can also press Record to resume at once.

**Activity by time of day.** The card under distance shows today as 96 bars, one per 15 minutes,
from midnight to midnight. Taller, darker (in dark mode, brighter) bars mean more steps: light is
under 300 steps in 15 minutes, moderate 300 to 899, brisk 900 to 1,499, peak 1,500 or more. Touch
or drag across the bars to read one. Below it, Walks lists each stretch of at least 200 steps at a
steady pace, with its times to the nearest 5 minutes. If Cadence was not running for part of the
day, the phone's step counter still returns those steps later, but not when they were taken: they
are in the day total and listed under the chart as steps with no time of day.

## Trend

**Steps by hour.** A grid of the last 7 days, one row per day and one cell per hour, shaded the
same way (light under 1,000 steps in an hour, moderate 1,000 to 2,999, brisk 3,000 to 4,999, peak
5,000 or more). Touch a cell to read it. The card names your most active hour. Days saved before
this version have no time of day, so their rows are empty.

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
