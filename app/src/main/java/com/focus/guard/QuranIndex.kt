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

    /** Uplifting verses, as (surah, verse): reassurance, hope, gratitude, remembrance, dua. */
    val PICKS = listOf(
        2 to 255, 2 to 286, 2 to 152, 2 to 186, 2 to 153, 2 to 216, 3 to 139, 3 to 173, 3 to 200, 3 to 8, 6 to 162,
        7 to 156, 8 to 2, 9 to 40, 10 to 57, 11 to 115, 12 to 87, 13 to 28, 14 to 7, 16 to 97, 17 to 80, 18 to 10,
        20 to 25, 20 to 46, 21 to 87, 24 to 22, 25 to 74, 29 to 69, 30 to 21, 33 to 41, 39 to 10, 39 to 53, 40 to 60,
        41 to 34, 49 to 13, 51 to 56, 55 to 13, 59 to 22, 64 to 11, 65 to 2, 65 to 3, 67 to 2, 93 to 3, 94 to 5, 94 to 6, 112 to 1
    )

    /** Beautiful, general-purpose hadiths: numbers inside "ara-nawawi" (Forty Hadith of an-Nawawi). */
    val HADITH_PICKS = listOf(1, 7, 12, 13, 15, 16, 17, 18, 19, 20, 21, 26, 27, 29, 31, 36, 40, 42)
}
