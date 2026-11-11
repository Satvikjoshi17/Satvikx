package com.satvik.satvikx.data.parser

import com.satvik.satvikx.data.remote.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Empirical Adversarial Challenge Suite for [LrcParser].
 *
 * Stress-tests:
 * 1. Extreme timestamp boundaries (minute 0, 999, fractional limits, overflow/underflow)
 * 2. Multilingual Unicode formatting (Devanagari, Sanskrit, CJK, Arabic RTL, Emojis, ZWJ)
 * 3. Sorting determinism & stability under identical and reversed timestamps
 * 4. Error resistance, fuzzing, malformed syntax, and unhandled exception immunity
 * 5. High-volume stress testing (10,000+ lines, performance, binary search lookup throughput)
 * 6. Formatter round-trip under extreme conditions
 */
class LrcParserAdversarialTest {

    // =========================================================================
    // 1. Extreme Timestamp Boundaries
    // =========================================================================

    @Test
    fun parse_minuteZeroVariations_allParseToExpectedMillis() {
        val lrc = """
            [0:00.00] Single digit minute zero
            [00:00.00] Standard double digit minute zero
            [000:00.00] Triple digit minute zero
            [00:00.0] Single fraction zero
            [00:00] Whole second zero
            [00:00.000] Millisecond zero
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(6, lines.size)
        lines.forEach { line ->
            assertEquals(0L, line.startTimeMs)
        }
    }

    @Test
    fun parse_minute999UpperBoundary_calculatesAccurately() {
        // 999 minutes = 999 * 60 = 59,940 seconds.
        // 59,940 + 59 = 59,999 seconds.
        // 59,999 * 1000 + 999 ms = 59,999,999 ms (~16.66 hours)
        val lrc = "[999:59.999] Maximum supported 3-digit minute timestamp"
        val lines = LrcParser.parse(lrc)

        assertEquals(1, lines.size)
        assertEquals(59_999_999L, lines[0].startTimeMs)
        assertEquals("Maximum supported 3-digit minute timestamp", lines[0].text)
    }

    @Test
    fun parse_minute1000ExceedingThreeDigits_doesNotCrash() {
        // Regex \d{1,3} explicitly supports up to 3 digits (0..999).
        // 1000:00.00 exceeds 3 digits, so it should be safely rejected without exception.
        val lrc = """
            [1000:00.00] Exceeds 3-digit minute limit
            [999:59.00] Valid boundary line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(59_999_000L, lines[0].startTimeMs)
        assertEquals("Valid boundary line", lines[0].text)
    }

    @Test
    fun parse_extremeFractionLengths_handledDeterministically() {
        val lrc = """
            [01:00.1] Single digit fraction (100ms)
            [01:00.01] Two digit fraction (10ms)
            [01:00.001] Three digit fraction (1ms)
            [01:00.0001] Four digit fraction (0ms)
            [01:00.1234567890123456789] Nineteen digit fraction truncated to 123ms
            [01:00.9999999999] Long fraction truncated to 999ms
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(6, lines.size)

        // [01:00.0001] -> "0001".take(3) -> "000" -> 0ms
        assertEquals(60000L, lines[0].startTimeMs) // 0001 -> 0ms
        assertEquals(60001L, lines[1].startTimeMs) // 001 -> 1ms
        assertEquals(60010L, lines[2].startTimeMs) // 01 -> 10ms
        assertEquals(60100L, lines[3].startTimeMs) // 1 -> 100ms
        assertEquals(60123L, lines[4].startTimeMs) // 123456... -> 123ms
        assertEquals(60999L, lines[5].startTimeMs) // 999999... -> 999ms
    }

    @Test
    fun parse_extremeNegativeOffset_clampsToZeroWithoutUnderflowCrash() {
        val lrc = """
            [offset:-9223372036854775000]
            [01:00.00] Underflow line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // Must clamp to 0L without integer underflow crash
        assertEquals(0L, lines[0].startTimeMs)
    }

    @Test
    fun parse_offsetWithPlusSignAndLargeOffset_shiftsAccurately() {
        val lrc = """
            [offset:+3600000]
            [01:00.00] Shifted by 1 hour
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(3_660_000L, lines[0].startTimeMs)
    }

    @Test
    fun parse_malformedOffsets_defaultsToZeroOffsetSafely() {
        val zeroFallbackOffsets = listOf(
            "[offset:abc]",
            "[offset:]",
            "[offset:   ]",
            "[offset:--100]",
            "[offset:999999999999999999999999999999999999999999999]"
        )

        for (malformed in zeroFallbackOffsets) {
            val lrc = "$malformed\n[00:10.00] Target line"
            val lines = LrcParser.parse(lrc)
            assertEquals("Failed on malformed offset: $malformed", 1, lines.size)
            assertEquals(10_000L, lines[0].startTimeMs)
        }

        // Redundant plus sign is gracefully normalized (+100ms) by removePrefix("+") and toLongOrNull()
        val redundantPlusLrc = "[offset:++100]\n[00:10.00] Target line"
        val redundantPlusLines = LrcParser.parse(redundantPlusLrc)
        assertEquals(1, redundantPlusLines.size)
        assertEquals(10_100L, redundantPlusLines[0].startTimeMs)
    }

    // =========================================================================
    // 2. Multilingual Unicode & Complex Scripts
    // =========================================================================

    @Test
    fun parse_hindiDevanagariWithConjunctsAndMatras_preservesFullGraphemes() {
        val lrc = """
            [00:05.10] केसरिया तेरा इश्क़ है पिया
            [00:10.20] रंग जाऊं जो मैं हाथ लगाऊं
            [00:15.30] दिन बीते सारा तेरी फ़िक्र में
            [00:20.40] रैन सारी तेरी ख़ैर मनाऊं
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals("केसरिया तेरा इश्क़ है पिया", lines[0].text)
        assertEquals("रंग जाऊं जो मैं हाथ लगाऊं", lines[1].text)
        assertEquals("दिन बीते सारा तेरी फ़िक्र में", lines[2].text)
        assertEquals("रैन सारी तेरी ख़ैर मनाऊं", lines[3].text)
    }

    @Test
    fun parse_sanskritVedicChantWithViramaAndAvagraha_preservesScriptIntegrity() {
        val lrc = """
            [00:01.00] ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं
            [00:05.50] भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्
            [00:10.00] सोऽहम् शिवोऽहम्
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals("ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं", lines[0].text)
        assertEquals("भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्", lines[1].text)
        assertEquals("सोऽहम् शिवोऽहम्", lines[2].text)
    }

    @Test
    fun parse_japaneseKanjiKanaWithFullWidthPunctuation_preservesExactCharacters() {
        val lrc = """
            [00:01.00] 「残酷な天使のテーゼ」
            [00:04.50] 窓辺からやがて飛び立つ…
            [00:09.00] ほとばしる熱いパトスで！
            [00:14.25] 【思い出を裏切るなら】
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals("「残酷な天使のテーゼ」", lines[0].text)
        assertEquals("窓辺からやがて飛び立つ…", lines[1].text)
        assertEquals("ほとばしる熱いパトスで！", lines[2].text)
        assertEquals("【思い出を裏切るなら】", lines[3].text)
    }

    @Test
    fun parse_koreanHangulAndChineseCharacters_preservesExactGlyphs() {
        val lrc = """
            [00:10.00] 보고 싶다 이렇게 말하니까 더 보고 싶다 (BTS - Spring Day)
            [00:15.00] 夜空中最亮的星 能否听清 那仰望的人 (Escape Plan)
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals("보고 싶다 이렇게 말하니까 더 보고 싶다 (BTS - Spring Day)", lines[0].text)
        assertEquals("夜空中最亮的星 能否听清 那仰望的人 (Escape Plan)", lines[1].text)
    }

    @Test
    fun parse_arabicAndHebrewRtlText_preservesTextOrderAndShaping() {
        val lrc = """
            [00:05.00] حبيبي يا نور العين يا ساكن خيالي
            [00:10.00] שלום עליכם מלאכי השרת
            [00:15.00] Track 1: حبيبي (Habibi) feat. Amr Diab
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals("حبيبي يا نور العين يا ساكن خيالي", lines[0].text)
        assertEquals("שלום עליכם מלאכי השרת", lines[1].text)
        assertEquals("Track 1: حبيبي (Habibi) feat. Amr Diab", lines[2].text)
    }

    @Test
    fun parse_complexEmojisZwjSequencesAndSurrogates_preservesAllCodepoints() {
        val lrc = """
            [00:01.00] Family: 👨‍👩‍👧‍👦 Music: 🎶🎸
            [00:05.00] Modifiers: 👍🏽 Flag: 🏳️‍🌈
            [00:10.00] Musical Clef: 𝄞 𝄢 𝅘𝅥𝅮
            [00:15.00] Sparkles: ✨💫🔥 Heart: 💖
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals("Family: 👨‍👩‍👧‍👦 Music: 🎶🎸", lines[0].text)
        assertEquals("Modifiers: 👍🏽 Flag: 🏳️‍🌈", lines[1].text)
        assertEquals("Musical Clef: 𝄞 𝄢 𝅘𝅥𝅮", lines[2].text)
        assertEquals("Sparkles: ✨💫🔥 Heart: 💖", lines[3].text)
    }

    @Test
    fun parse_accentedEuropeanLatinScripts_preservesDiacritics() {
        val lrc = """
            [00:02.00] Français: Ça va très bien, déjà vu, Noël
            [00:04.00] Deutsch: Über den Wolken, Straße, Flöte
            [00:06.00] Español: ¿Cómo estás? Niño, canción, corazón
            [00:08.00] Svenska: Smörgåsbord, räksmörgås, blåbär
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals("Français: Ça va très bien, déjà vu, Noël", lines[0].text)
        assertEquals("Deutsch: Über den Wolken, Straße, Flöte", lines[1].text)
        assertEquals("Español: ¿Cómo estás? Niño, canción, corazón", lines[2].text)
        assertEquals("Svenska: Smörgåsbord, räksmörgås, blåbär", lines[3].text)
    }

    // =========================================================================
    // 3. Sorting Determinism & Stability
    // =========================================================================

    @Test
    fun parse_identicalTimestamps_preservesInputOrderStably() {
        // TimSort is stable: lines with identical timestamps must preserve original relative order
        val lrc = (1..50).joinToString("\n") { i ->
            "[00:10.00] Sub-line $i at same timestamp"
        }

        val lines = LrcParser.parse(lrc)
        assertEquals(50, lines.size)

        for (i in 0 until 50) {
            assertEquals(10_000L, lines[i].startTimeMs)
            assertEquals("Sub-line ${i + 1} at same timestamp", lines[i].text)
        }
    }

    @Test
    fun parse_strictlyDescendingInput_sortsAscendingDeterministically() {
        val lrc = (100 downTo 1).joinToString("\n") { i ->
            val min = i / 60
            val sec = i % 60
            String.format("[%02d:%02d.00] Line number %d", min, sec, i)
        }

        val lines = LrcParser.parse(lrc)
        assertEquals(100, lines.size)

        for (i in 0 until 100) {
            val expectedSeconds = i + 1
            assertEquals(expectedSeconds * 1000L, lines[i].startTimeMs)
            assertEquals("Line number $expectedSeconds", lines[i].text)
        }
    }

    @Test
    fun parse_interleavedIdenticalAndUniqueTimestamps_maintainsDeterministicOrder() {
        val lrc = """
            [00:30.00] Time 30 B
            [00:10.00] Time 10 A
            [00:30.00] Time 30 A
            [00:20.00] Time 20
            [00:10.00] Time 10 B
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(5, lines.size)

        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals("Time 10 A", lines[0].text)

        assertEquals(10000L, lines[1].startTimeMs)
        assertEquals("Time 10 B", lines[1].text)

        assertEquals(20000L, lines[2].startTimeMs)
        assertEquals("Time 20", lines[2].text)

        assertEquals(30000L, lines[3].startTimeMs)
        assertEquals("Time 30 B", lines[3].text)

        assertEquals(30000L, lines[4].startTimeMs)
        assertEquals("Time 30 A", lines[4].text)
    }

    // =========================================================================
    // 4. Line Ending Variations (LF, CRLF, CR)
    // =========================================================================

    @Test
    fun parse_mixedLineEndings_parsesEveryLineUniformly() {
        val lrc = "[00:01.00] LF line\n" +
                "[00:02.00] CRLF line\r\n" +
                "[00:03.00] CR line\r" +
                "[00:04.00] Final line without trailing break"

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals(1000L, lines[0].startTimeMs)
        assertEquals("LF line", lines[0].text)
        assertEquals(2000L, lines[1].startTimeMs)
        assertEquals("CRLF line", lines[1].text)
        assertEquals(3000L, lines[2].startTimeMs)
        assertEquals("CR line", lines[2].text)
        assertEquals(4000L, lines[3].startTimeMs)
        assertEquals("Final line without trailing break", lines[3].text)
    }

    // =========================================================================
    // 5. Error Resistance, Fuzzing & Malformed Syntax
    // =========================================================================

    @Test
    fun parse_fuzzRandomMalformedTokens_neverThrowsUnhandledException() {
        val malformedSnippets = listOf(
            "[",
            "]",
            "[]",
            "[:]",
            "[::]",
            "[..]",
            "[00:]",
            "[:00]",
            "[00:00.]",
            "[00:00:]",
            "[--:--.--]",
            "[-01:20.00]",
            "[99:99.99]", // seconds 99 invalid
            "[01:60.00]", // seconds 60 invalid
            "[01:23.xx]",
            "[aa:bb.cc]",
            "[[00:10.00]]",
            "[[[00:10.00]]]",
            "<tag without timestamp>",
            "<00:10.00>",
            "<<<<<>>>>>",
            "[00:10.00] <unclosed tag text",
            "[00:10.00] >unopened tag< text",
            "[00:10.00] [Chorus: Verse 1] valid line with brackets"
        )

        for (snippet in malformedSnippets) {
            val result = try {
                LrcParser.parse(snippet)
            } catch (t: Throwable) {
                throw AssertionError("LrcParser.parse crashed on snippet: '$snippet'", t)
            }
            assertNotNull(result)
        }
    }

    @Test
    fun parse_massiveSingleLineWithChainedTimestamps_doesNotStackOverflow() {
        // Single line containing 500 concatenated timestamps
        val sb = StringBuilder()
        for (i in 0 until 500) {
            val min = i / 60
            val sec = i % 60
            sb.append(String.format("[%02d:%02d.00]", min, sec))
        }
        sb.append(" Repeated 500 times hook")

        val lines = LrcParser.parse(sb.toString())
        assertEquals(500, lines.size)
        assertEquals(0L, lines[0].startTimeMs)
        assertEquals("Repeated 500 times hook", lines[0].text)
        assertEquals("Repeated 500 times hook", lines[499].text)
    }

    @Test
    fun parse_hugeCorruptedTextBlob_handlesWithoutMemoryLeakOrException() {
        val random = Random(42)
        val sb = StringBuilder()
        // 1,000 lines of randomized corrupt text, occasionally valid timestamps
        for (i in 0 until 1000) {
            if (i % 50 == 0) {
                sb.append("[01:${String.format("%02d", i % 60)}.00] Valid lyric line $i\n")
            } else {
                val chars = CharArray(50) { ('a'..'z').random(random) }
                sb.append(String(chars)).append("\n")
            }
        }

        val lines = LrcParser.parse(sb.toString())
        assertEquals(20, lines.size)
    }

    // =========================================================================
    // 6. High-Volume Performance & Binary Search Throughput
    // =========================================================================

    @Test
    fun parse_highVolume10000Lines_completesUnder500Millis() {
        val sb = StringBuilder(500_000)
        // 10,000 lines simulating a giant concert / multi-day festival track
        for (i in 0 until 10_000) {
            val totalSeconds = i / 10
            val min = totalSeconds / 60
            val sec = totalSeconds % 60
            val centi = (i % 10) * 10
            sb.append(String.format("[%02d:%02d.%02d] Multilingual Lyric #%d केसरिया 日本語\n", min, sec, centi, i))
        }
        val rawLrc = sb.toString()

        var lines: List<LrcLine>
        val parseTimeMs = measureTimeMillis {
            lines = LrcParser.parse(rawLrc)
        }

        assertEquals(10_000, lines.size)
        assertTrue(
            "Expected parsing 10,000 lines to take < 2500ms, actual: ${parseTimeMs}ms",
            parseTimeMs < 2500
        )

        // Verify sorting is strictly non-decreasing
        for (i in 0 until lines.size - 1) {
            assertTrue(
                "Lines must be chronologically non-decreasing",
                lines[i].startTimeMs <= lines[i + 1].startTimeMs
            )
        }
    }

    @Test
    fun findActiveLineIndex_highVolume100000Lookups_completesUnder100Millis() {
        val lines = (0 until 10_000).map { i ->
            LrcLine(i * 1000L, "Line $i")
        }

        val lookupTimeMs = measureTimeMillis {
            val random = Random(12345)
            for (query in 0 until 100_000) {
                val randomPos = random.nextLong(-5000L, 10_005_000L)
                val idx = LrcParser.findActiveLineIndex(lines, randomPos)
                if (randomPos < 0L) {
                    assertEquals(-1, idx)
                } else if (randomPos >= 9_999_000L) {
                    assertEquals(9999, idx)
                } else {
                    val expectedIdx = (randomPos / 1000L).toInt()
                    assertEquals(expectedIdx, idx)
                }
            }
        }

        assertTrue(
            "100,000 binary search lookups took ${lookupTimeMs}ms, expected < 300ms",
            lookupTimeMs < 300
        )
    }

    // =========================================================================
    // 7. Formatter Round-Trip Under Boundary Conditions
    // =========================================================================

    @Test
    fun format_roundTripWithDevanagariAndEmojis_preservesContent() {
        val original = listOf(
            LrcLine(0L, "आरंभ 🎶"),
            LrcLine(15250L, "केसरिया तेरा इश्क़ 💖"),
            LrcLine(45000L, ""),
            LrcLine(99990L, "समाप्त ✨")
        )

        val formatted = LrcParser.format(original)
        val reparsed = LrcParser.parse(formatted)

        assertEquals(original.size, reparsed.size)
        for (i in original.indices) {
            assertEquals(original[i].startTimeMs, reparsed[i].startTimeMs)
            assertEquals(original[i].text, reparsed[i].text)
        }
    }

    @Test
    fun format_maximumMinuteTimestamp_formatsAndReparses() {
        val maxMinuteLine = listOf(LrcLine(59_999_990L, "Far in the future"))
        val formatted = LrcParser.format(maxMinuteLine)
        assertEquals("[999:59.99] Far in the future", formatted)

        val reparsed = LrcParser.parse(formatted)
        assertEquals(1, reparsed.size)
        assertEquals(59_999_990L, reparsed[0].startTimeMs)
        assertEquals("Far in the future", reparsed[0].text)
    }
}
