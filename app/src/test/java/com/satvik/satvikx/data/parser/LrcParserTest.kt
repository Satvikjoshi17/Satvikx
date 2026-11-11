package com.satvik.satvikx.data.parser

import com.satvik.satvikx.data.remote.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive unit test suite for [LrcParser].
 *
 * Covers:
 * 1. Standard 2-digit centisecond parsing ([mm:ss.xx])
 * 2. High-precision 3-digit millisecond parsing ([mm:ss.xxx])
 * 3. Single-digit fraction handling ([mm:ss.x])
 * 4. Sub-millisecond fraction truncation ([mm:ss.xxxx])
 * 5. Multi-timestamp per line parsing
 * 6. Metadata header tag stripping and offset handling
 * 7. Non-timestamp bracketed text handling
 * 8. Strict chronological sorting
 * 9. Inline syllable/karaoke tag stripping (<...>)
 * 10. Formatter serialization and round-trip fidelity
 * 11. Boundary conditions, errors, and playback search helpers
 */
class LrcParserTest {

    // =========================================================================
    // 1. Standard Centisecond Timestamps ([mm:ss.xx])
    // =========================================================================

    @Test
    fun parse_standardCentiseconds_calculatesCorrectMillis() {
        val lrc = "[01:23.45] Never gonna give you up"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(83450L, lines[0].startTimeMs)
        assertEquals("Never gonna give you up", lines[0].text)
    }

    @Test
    fun parse_zeroTimestamp_returnsZeroMs() {
        val lrc = "[00:00.00] Track start"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].startTimeMs)
        assertEquals("Track start", lines[0].text)
    }

    @Test
    fun parse_centisecondsUpperBoundary_calculatesCorrectMillis() {
        val lrc = "[02:59.99] End of section"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(179990L, lines[0].startTimeMs)
        assertEquals("End of section", lines[0].text)
    }

    @Test
    fun parse_colonFractionDelimiter_calculatesCorrectMillis() {
        val lrc = "[01:23:45] Colon separated centiseconds"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(83450L, lines[0].startTimeMs)
        assertEquals("Colon separated centiseconds", lines[0].text)
    }

    @Test
    fun parse_largeMinuteValues_calculatesCorrectMillis() {
        val lrc = "[75:30.00] Long DJ mix track"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // (75 * 60 + 30) * 1000 = 4,530,000 ms
        assertEquals(4530000L, lines[0].startTimeMs)
        assertEquals("Long DJ mix track", lines[0].text)
    }

    // =========================================================================
    // 2. High-Precision Millisecond Timestamps ([mm:ss.xxx])
    // =========================================================================

    @Test
    fun parse_highPrecisionMilliseconds_calculatesExactMillis() {
        val lrc = "[01:23.456] High precision line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(83456L, lines[0].startTimeMs)
        assertEquals("High precision line", lines[0].text)
    }

    @Test
    fun parse_fractionWithLeadingZeros_preservesMillisecondAccuracy() {
        val lrc = "[00:05.005] Small fraction line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // 5000 + 5 ms = 5005 ms
        assertEquals(5005L, lines[0].startTimeMs)
        assertEquals("Small fraction line", lines[0].text)
    }

    @Test
    fun parse_fractionWithTrailingZero_parsedCorrectly() {
        val lrc = "[00:10.050] Fifty millis line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // 10000 + 50 ms = 10050 ms
        assertEquals(10050L, lines[0].startTimeMs)
        assertEquals("Fifty millis line", lines[0].text)
    }

    @Test
    fun parse_mixedPrecisionInSameFile_parsesBothAccurately() {
        val lrc = """
            [00:10.50] Centisecond line
            [00:20.123] Millisecond line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(10500L, lines[0].startTimeMs)
        assertEquals("Centisecond line", lines[0].text)
        assertEquals(20123L, lines[1].startTimeMs)
        assertEquals("Millisecond line", lines[1].text)
    }

    // =========================================================================
    // 3. Single-Digit Fraction Handling ([mm:ss.x])
    // =========================================================================

    @Test
    fun parse_singleDigitFraction_expandsToTenthsOfASecond() {
        val lrc = "[01:23.4] Single digit fraction"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // .4 is 400 ms (4 tenths of a second), NOT 4 ms
        assertEquals(83400L, lines[0].startTimeMs)
        assertEquals("Single digit fraction", lines[0].text)
    }

    @Test
    fun parse_singleDigitZero_expandsToZeroMillis() {
        val lrc = "[00:02.0] Two seconds flat"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(2000L, lines[0].startTimeMs)
        assertEquals("Two seconds flat", lines[0].text)
    }

    @Test
    fun parse_singleDigitNine_expandsToNineHundredMillis() {
        val lrc = "[00:03.9] Almost four seconds"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(3900L, lines[0].startTimeMs)
        assertEquals("Almost four seconds", lines[0].text)
    }

    // =========================================================================
    // 4. Sub-Millisecond & Omitted Fractions
    // =========================================================================

    @Test
    fun parse_subMillisecondFourDigitFraction_truncatesToThreeDigits() {
        val lrc = "[01:23.4567] Four digit sub-millisecond"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        // 4567 truncated to 456 ms
        assertEquals(83456L, lines[0].startTimeMs)
        assertEquals("Four digit sub-millisecond", lines[0].text)
    }

    @Test
    fun parse_timestampWithoutFraction_defaultsToZeroMillis() {
        val lrc = "[01:23] No fraction provided"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(83000L, lines[0].startTimeMs)
        assertEquals("No fraction provided", lines[0].text)
    }

    // =========================================================================
    // 5. Multi-Timestamp Per Line Parsing
    // =========================================================================

    @Test
    fun parse_twoTimestampsOnSingleLine_producesTwoDistinctLines() {
        val lrc = "[00:12.34][00:45.67] Chorus line"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(12340L, lines[0].startTimeMs)
        assertEquals("Chorus line", lines[0].text)
        assertEquals(45670L, lines[1].startTimeMs)
        assertEquals("Chorus line", lines[1].text)
    }

    @Test
    fun parse_threeTimestampsOnSingleLine_producesThreeDistinctLines() {
        val lrc = "[00:05.00][00:15.00][00:25.00] Hook repeated thrice"
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(5000L, lines[0].startTimeMs)
        assertEquals(15000L, lines[1].startTimeMs)
        assertEquals(25000L, lines[2].startTimeMs)
        assertEquals("Hook repeated thrice", lines[0].text)
        assertEquals("Hook repeated thrice", lines[1].text)
        assertEquals("Hook repeated thrice", lines[2].text)
    }

    @Test
    fun parse_multipleTimestampsWithWhitespace_parsesCleanly() {
        val lrc = "[00:10.00]  [00:20.00]   [00:30.00] Repeated spaced hook"
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals(20000L, lines[1].startTimeMs)
        assertEquals(30000L, lines[2].startTimeMs)
        assertEquals("Repeated spaced hook", lines[0].text)
        assertEquals("Repeated spaced hook", lines[1].text)
        assertEquals("Repeated spaced hook", lines[2].text)
    }

    @Test
    fun parse_unorderedMultiTimestampsOnLine_sortsChronologically() {
        val lrc = "[00:30.00][00:10.00] Out of order line timestamps"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals(30000L, lines[1].startTimeMs)
        assertEquals("Out of order line timestamps", lines[0].text)
        assertEquals("Out of order line timestamps", lines[1].text)
    }

    // =========================================================================
    // 6. Metadata Header Tag Stripping & Offset Handling
    // =========================================================================

    @Test
    fun parse_standardMetadataHeaders_areCompletelyDiscarded() {
        val lrc = """
            [ti:Kesariya]
            [ar:Arijit Singh, Pritam]
            [al:Brahmastra]
            [by:SatvikX Editor]
            [length:04:28]
            [re:Android App]
            [ve:1.0]
            [00:15.20] Mujhko itna bataye koyi
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(15200L, lines[0].startTimeMs)
        assertEquals("Mujhko itna bataye koyi", lines[0].text)
    }

    @Test
    fun parse_headersWithWhitespaceAroundColon_areDiscarded() {
        val lrc = """
            [ ti : Title With Spaces ]
            [ ar : Artist With Spaces ]
            [00:05.00] Clean lyric line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(5000L, lines[0].startTimeMs)
        assertEquals("Clean lyric line", lines[0].text)
    }

    @Test
    fun parse_fileWithOnlyHeaders_returnsEmptyList() {
        val lrc = """
            [ti:Title Only]
            [ar:Artist Only]
            [al:Album Only]
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun parse_positiveOffsetHeader_shiftsTimestampsForward() {
        val lrc = """
            [offset:+500]
            [00:10.00] Shifted forward by 500ms
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(10500L, lines[0].startTimeMs)
        assertEquals("Shifted forward by 500ms", lines[0].text)
    }

    @Test
    fun parse_negativeOffsetHeader_clampsUnderflowToZero() {
        val lrc = """
            [offset:-500]
            [00:00.20] Underflow clamped to zero
            [00:10.00] Shifted backward
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        // 200 - 500 = -300, clamped to 0
        assertEquals(0L, lines[0].startTimeMs)
        assertEquals("Underflow clamped to zero", lines[0].text)
        // 10000 - 500 = 9500
        assertEquals(9500L, lines[1].startTimeMs)
        assertEquals("Shifted backward", lines[1].text)
    }

    // =========================================================================
    // 7. Non-Timestamp Bracketed Text Handling
    // =========================================================================

    @Test
    fun parse_timestampWithBracketedSectionName_preservesSectionText() {
        val lrc = "[00:15.00] [Chorus] We will rock you"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(15000L, lines[0].startTimeMs)
        assertEquals("[Chorus] We will rock you", lines[0].text)
    }

    @Test
    fun parse_bracketedTextInsideLyric_preservesBrackets() {
        val lrc = "[00:20.00] Singing [yeah, yeah] in the rain"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(20000L, lines[0].startTimeMs)
        assertEquals("Singing [yeah, yeah] in the rain", lines[0].text)
    }

    @Test
    fun parse_standaloneBracketedLineWithoutTimestamp_isSafelySkipped() {
        val lrc = """
            [Intro]
            [00:04.00] Start singing
            [Guitar Solo]
            [00:30.00] Keep singing
            [Outro]
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(4000L, lines[0].startTimeMs)
        assertEquals("Start singing", lines[0].text)
        assertEquals(30000L, lines[1].startTimeMs)
        assertEquals("Keep singing", lines[1].text)
    }

    @Test
    fun parse_bracketedRoleOrArtistTag_preservesBracketsInLyric() {
        val lrc = "[00:12.34] [Drake: Verse 1] Started from the bottom"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].startTimeMs)
        assertEquals("[Drake: Verse 1] Started from the bottom", lines[0].text)
    }

    // =========================================================================
    // 8. Out-of-Order Sorting
    // =========================================================================

    @Test
    fun parse_unorderedLines_sortsStrictlyChronologically() {
        val lrc = """
            [00:40.00] Third line
            [00:10.00] First line
            [00:25.00] Second line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals("First line", lines[0].text)
        assertEquals(25000L, lines[1].startTimeMs)
        assertEquals("Second line", lines[1].text)
        assertEquals(40000L, lines[2].startTimeMs)
        assertEquals("Third line", lines[2].text)
    }

    @Test
    fun parse_interleavedMultiTimestampsAndSingleTimestamps_sortsChronologically() {
        val lrc = """
            [00:50.00][00:15.00] Refrain hook
            [00:30.00] Verse 2
            [00:05.00] Intro line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals(5000L, lines[0].startTimeMs)
        assertEquals("Intro line", lines[0].text)
        assertEquals(15000L, lines[1].startTimeMs)
        assertEquals("Refrain hook", lines[1].text)
        assertEquals(30000L, lines[2].startTimeMs)
        assertEquals("Verse 2", lines[2].text)
        assertEquals(50000L, lines[3].startTimeMs)
        assertEquals("Refrain hook", lines[3].text)
    }

    @Test
    fun parse_identicalTimestamps_preservesBothEntries() {
        val lrc = """
            [00:10.00] Line A
            [00:10.00] Line B
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals(10000L, lines[1].startTimeMs)
    }

    // =========================================================================
    // 9. Inline Syllable/Karaoke Tag Stripping (<...>)
    // =========================================================================

    @Test
    fun parse_inlineSyllableWordTimestamps_stripsAngleBracketTags() {
        val lrc = "[00:12.00] Never <00:12.50> gonna <00:13.00> give <00:13.50> you up"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(12000L, lines[0].startTimeMs)
        assertEquals("Never gonna give you up", lines[0].text)
    }

    @Test
    fun parse_inlineFormattingAndCueTags_stripsCleanly() {
        val lrc = "[00:05.100] <v Singer><c>Hello</c> world</v>"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(5100L, lines[0].startTimeMs)
        assertEquals("Hello world", lines[0].text)
    }

    // =========================================================================
    // 10. Formatter Round-Trip Serialization
    // =========================================================================

    @Test
    fun format_singleLine_formatsStandardCentisecondLrcString() {
        val lines = listOf(LrcLine(83450L, "Hello world"))
        val formatted = LrcParser.format(lines)
        assertEquals("[01:23.45] Hello world", formatted)
    }

    @Test
    fun format_emptyList_returnsEmptyString() {
        val formatted = LrcParser.format(emptyList())
        assertEquals("", formatted)
    }

    @Test
    fun format_zeroTimestamp_formatsWithLeadingZeros() {
        val lines = listOf(LrcLine(0L, "Start track"))
        val formatted = LrcParser.format(lines)
        assertEquals("[00:00.00] Start track", formatted)
    }

    @Test
    fun format_lineWithEmptyText_formatsTimestampOnlyWithoutTrailingSpace() {
        val lines = listOf(LrcLine(90000L, ""))
        val formatted = LrcParser.format(lines)
        assertEquals("[01:30.00]", formatted)
    }

    @Test
    fun format_andParse_roundTripPreservesTimestampsAndText() {
        val original = listOf(
            LrcLine(5000L, "Intro"),
            LrcLine(12340L, "Verse 1"),
            LrcLine(65890L, "Chorus"),
            LrcLine(90000L, ""),
            LrcLine(185000L, "Outro")
        )
        val formatted = LrcParser.format(original)
        val reparsed = LrcParser.parse(formatted)
        assertEquals(original.size, reparsed.size)
        for (i in original.indices) {
            assertEquals(original[i].startTimeMs, reparsed[i].startTimeMs)
            assertEquals(original[i].text, reparsed[i].text)
        }
    }

    // =========================================================================
    // 11. Boundaries, Edge Cases, Errors & Helpers
    // =========================================================================

    @Test
    fun parse_emptyString_returnsEmptyList() {
        assertTrue(LrcParser.parse("").isEmpty())
    }

    @Test
    fun parse_nullInput_returnsEmptyList() {
        assertTrue(LrcParser.parse(null).isEmpty())
    }

    @Test
    fun parse_whitespaceOnly_returnsEmptyList() {
        assertTrue(LrcParser.parse("   \n\t  \r\n   ").isEmpty())
    }

    @Test
    fun parse_blankLinesInterspersed_areIgnored() {
        val lrc = "\n\n[00:05.00] Lyric 1\n\n\n[00:10.00] Lyric 2\n\n"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(5000L, lines[0].startTimeMs)
        assertEquals(10000L, lines[1].startTimeMs)
    }

    @Test
    fun parse_timestampWithEmptyLyricText_createsInterludeLine() {
        val lrc = """
            [00:10.00] First line
            [00:20.00]
            [00:25.00]    
            [00:30.00] Second line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals("First line", lines[0].text)
        assertEquals(20000L, lines[1].startTimeMs)
        assertEquals("", lines[1].text)
        assertEquals(25000L, lines[2].startTimeMs)
        assertEquals("", lines[2].text)
        assertEquals(30000L, lines[3].startTimeMs)
        assertEquals("Second line", lines[3].text)
    }

    @Test
    fun parse_crlfWindowsLineEndings_parsesCorrectly() {
        val lrc = "[00:01.00] Line 1\r\n[00:02.00] Line 2\r\n"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].startTimeMs)
        assertEquals(2000L, lines[1].startTimeMs)
    }

    @Test
    fun parse_unicodeAndMultilingualLyrics_preservesExactCharacters() {
        val lrc = "[00:15.20] केसरिया तेरा इश्क़ hai piya 🎶 (Official)"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(15200L, lines[0].startTimeMs)
        assertEquals("केसरिया तेरा इश्क़ hai piya 🎶 (Official)", lines[0].text)
    }

    @Test
    fun parse_leadingAndTrailingWhitespaceInLyric_isTrimmedCleanly() {
        val lrc = "[00:10.00]    Padded lyric text with trailing space    "
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Padded lyric text with trailing space", lines[0].text)
    }

    @Test
    fun parse_corruptedLinesAndGarbageText_doesNotThrowAndParsesValidLines() {
        val lrc = """
            Random garbage text without brackets
            [:20.00] Malformed minute
            [00:65.00] Invalid seconds
            [999999:99.99] Excessive digits
            [00:15.00] Valid lyric line
            <<<XML-Like corrupted junk>>>
            [ar]
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(15000L, lines[0].startTimeMs)
        assertEquals("Valid lyric line", lines[0].text)
    }

    @Test
    fun testIsValidLrc() {
        assertTrue(LrcParser.isValidLrc("[00:12.34] Hello"))
        assertTrue(LrcParser.isValidLrc("[ti:Song]\n[01:00.00] Hook"))
        assertFalse(LrcParser.isValidLrc("Plain lyrics line 1\nLine 2"))
        assertFalse(LrcParser.isValidLrc(null))
        assertFalse(LrcParser.isValidLrc(""))
        assertFalse(LrcParser.isValidLrc("   [Chorus]   "))
    }

    @Test
    fun testFindActiveLineIndexBinarySearch() {
        val lines = listOf(
            LrcLine(1000L, "Line 0"),
            LrcLine(5000L, "Line 1"),
            LrcLine(10000L, "Line 2")
        )

        // Before first line
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 999L))

        // Exactly on Line 0
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 4999L))

        // Exactly on Line 1
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 5000L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 9999L))

        // Exactly on Line 2 and beyond
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 10000L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 50000L))

        // Empty list boundary
        assertEquals(-1, LrcParser.findActiveLineIndex(emptyList(), 1000L))
    }
}

