# Stepwise User Guide

## What it does

Stepwise counts your steps on the phone and estimates distance, walking
calories and metabolic rate. (Full feature list coming in a later release.)

## Profile

Enter your height, weight, sex and age. These values drive the stride length
and the metabolic estimates. (Coming in a later release.)

## Today

Steps, distance and calories for the current day. (Coming in a later release.)

## Trend (coming in a later release)

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

Stepwise works fully offline. No data leaves the device: no accounts, no
uploads, no analytics.
