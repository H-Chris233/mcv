package app.multicardvault.features.create

enum class SafeThresholdPreset(
    val threshold: Int,
    val total: Int,
    val label: String,
) {
    TwoOfThree(2, 3, "2-of-3"),
    ThreeOfFive(3, 5, "3-of-5");

    companion object {
        val Default: SafeThresholdPreset = ThreeOfFive

        fun isAllowed(
            threshold: Int,
            total: Int,
        ): Boolean = entries.any { it.threshold == threshold && it.total == total }

        fun from(
            threshold: Int,
            total: Int,
        ): SafeThresholdPreset =
            entries.firstOrNull { it.threshold == threshold && it.total == total }
                ?: Default
    }
}
