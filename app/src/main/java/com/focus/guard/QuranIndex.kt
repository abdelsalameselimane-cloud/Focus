package com.focus.guard

/** Pure helpers for addressing verses; no Android dependencies. */
object QuranIndex {
    /** Number of verses in each of the 114 surahs. */
    val COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227,
        93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96,
        29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17,
        19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )
    const val TOTAL = 6236

    /** Position of verse [ayah] of [surah] counted from the start of the Quran (1..6236). */
    fun global(surah: Int, ayah: Int): Int {
        var n = 0
        for (i in 0 until surah - 1) n += COUNTS[i]
        return n + ayah
    }

    /**
     * Uplifting passages as (surah, from, to). Each one is complete in meaning on its own: whole short surahs,
     * or a few connected verses, never a verse whose sense depends on a story or an event around it.
     */
    val PICKS = listOf(
        Triple(1, 1, 7), Triple(2, 153, 157), Triple(2, 186, 186), Triple(2, 255, 255), Triple(2, 285, 286),
        Triple(3, 8, 9), Triple(3, 139, 140), Triple(3, 173, 174), Triple(3, 190, 194), Triple(3, 200, 200),
        Triple(6, 162, 163), Triple(7, 156, 156), Triple(8, 2, 4), Triple(10, 57, 58), Triple(11, 115, 115),
        Triple(13, 28, 29), Triple(14, 7, 7), Triple(16, 90, 90), Triple(16, 97, 97), Triple(17, 23, 24),
        Triple(18, 107, 110), Triple(20, 25, 32), Triple(21, 87, 88), Triple(24, 35, 35), Triple(25, 63, 67),
        Triple(25, 74, 74), Triple(29, 69, 69), Triple(30, 21, 21), Triple(31, 13, 19), Triple(33, 41, 43),
        Triple(39, 10, 10), Triple(39, 53, 55), Triple(40, 60, 60), Triple(41, 30, 35), Triple(49, 10, 13),
        Triple(51, 56, 58), Triple(55, 1, 13), Triple(59, 22, 24), Triple(64, 11, 11), Triple(65, 2, 3),
        Triple(67, 1, 3), Triple(93, 1, 11), Triple(94, 1, 8), Triple(103, 1, 3), Triple(108, 1, 3),
        Triple(110, 1, 3), Triple(112, 1, 4), Triple(113, 1, 5), Triple(114, 1, 6)
    )

    /** Beautiful, general-purpose hadiths: numbers inside "ara-nawawi" (Forty Hadith of an-Nawawi). */
    val HADITH_PICKS = listOf(1, 7, 12, 13, 15, 16, 17, 18, 19, 20, 21, 26, 27, 29, 31, 36, 40, 42)
}
