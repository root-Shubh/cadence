package com.cadence.domain

/** Biological sex used for BMR and stride estimates. OTHER uses the average of MALE and FEMALE. */
enum class Sex { MALE, FEMALE, OTHER }

/**
 * Assumption: resting metabolic rate is approximated as BMR * 1.1 for the whole day.
 */
const val RMR_FACTOR = 1.1

/** Walking energy cost: kcal per kg of body weight per km walked. */
const val WALK_KCAL_PER_KG_KM = 0.5

/** Stride length as a fraction of height, per sex. */
private const val STRIDE_FACTOR_MALE = 0.415
private const val STRIDE_FACTOR_FEMALE = 0.413

/**
 * User body profile. [strideOverrideCm], when set, replaces the height-based stride estimate.
 * Throws [IllegalArgumentException] for non-positive height, weight, age or stride override.
 */
data class Profile(
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sex: Sex,
    val strideOverrideCm: Double? = null,
) {
    init {
        require(heightCm > 0) { "heightCm must be > 0, was $heightCm" }
        require(weightKg > 0) { "weightKg must be > 0, was $weightKg" }
        require(age > 0) { "age must be > 0, was $age" }
        strideOverrideCm?.let { require(it > 0) { "strideOverrideCm must be > 0, was $it" } }
    }
}

data class DayMetrics(
    val steps: Int,
    val distanceM: Double,
    val activeKcal: Double,
    val bmrKcal: Double,
    val rmrKcal: Double,
    val totalKcal: Double,
)

/** Mifflin-St Jeor basal metabolic rate in kcal/day. */
fun bmrKcal(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
    require(weightKg > 0) { "weightKg must be > 0, was $weightKg" }
    require(heightCm > 0) { "heightCm must be > 0, was $heightCm" }
    require(age > 0) { "age must be > 0, was $age" }
    val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
    return base + when (sex) {
        Sex.MALE -> 5.0
        Sex.FEMALE -> -161.0
        Sex.OTHER -> (5.0 + -161.0) / 2.0
    }
}

/**
 * Stride length in cm. Order: the profile override if set (it always wins), then the GPS-learned
 * stride [learnedCm] if given, otherwise the height-based estimate.
 */
fun strideCm(heightCm: Double, sex: Sex, overrideCm: Double? = null, learnedCm: Double? = null): Double {
    overrideCm?.let {
        require(it > 0) { "strideOverrideCm must be > 0, was $it" }
        return it
    }
    learnedCm?.let { if (it > 0) return it }
    require(heightCm > 0) { "heightCm must be > 0, was $heightCm" }
    return when (sex) {
        Sex.MALE -> heightCm * STRIDE_FACTOR_MALE
        Sex.FEMALE -> heightCm * STRIDE_FACTOR_FEMALE
        Sex.OTHER -> heightCm * (STRIDE_FACTOR_MALE + STRIDE_FACTOR_FEMALE) / 2.0
    }
}

/** Computes all metrics for one day from a step count. Negative steps throw. */
fun computeDay(profile: Profile, steps: Int, learnedCm: Double? = null): DayMetrics {
    require(steps >= 0) { "steps must be >= 0, was $steps" }
    val stride = strideCm(profile.heightCm, profile.sex, profile.strideOverrideCm, learnedCm)
    val distanceM = steps * stride / 100.0
    val distanceKm = distanceM / 1000.0
    val activeKcal = WALK_KCAL_PER_KG_KM * profile.weightKg * distanceKm
    val bmr = bmrKcal(profile.weightKg, profile.heightCm, profile.age, profile.sex)
    val rmr = bmr * RMR_FACTOR
    return DayMetrics(
        steps = steps,
        distanceM = distanceM,
        activeKcal = activeKcal,
        bmrKcal = bmr,
        rmrKcal = rmr,
        totalKcal = rmr + activeKcal,
    )
}
