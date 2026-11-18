package com.cadence.data

import com.cadence.export.CsvDailyRow

fun StepDayEntity.toCsvRow(): CsvDailyRow = CsvDailyRow(
    dateIso = dateIso,
    steps = steps,
    distanceM = distanceM,
    bmrKcal = bmrKcal,
    rmrKcal = rmrKcal,
    activeKcal = activeKcal,
    totalKcal = totalKcal,
)

fun List<StepDayEntity>.toCsvRows(): List<CsvDailyRow> = map { it.toCsvRow() }
