package com.satvik.satvikx.data.parser

import com.satvik.satvikx.data.remote.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Empirical Adversarial Challenger Test for [LrcParser.findActiveLineIndex].
 *
 * Verifies the binary search implementation against a formal linear search oracle
 * across exhaustive edge cases, boundary conditions, and 100,000+ randomized fuzz positions.
 */
class BinarySearchOracleStressTest {

    /**
     * Formal Linear Search Oracle.
     * Specification: Returns the index of the active lyric line for the given positionMs.
     * If the list is empty or positionMs is before the first line, returns -1.
     * Otherwise, returns the greatest index i such that lines[i].startTimeMs <= positionMs.
     */
    private fun linearSearchOracle(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines[0].startTimeMs) return -1
        var lastIndex = -1
        for (i in lines.indices) {
            if (lines[i].startTimeMs <= positionMs) {
                lastIndex = i
            } else {
                break
            }
        }
        return lastIndex
    }

    // =========================================================================
    // 1. Boundary & Corner Cases
    // =========================================================================

    @Test
    fun testEmptyList_returnsNegativeOneForAllPositions() {
        val empty = emptyList<LrcLine>()
        val testPositions = listOf(
            Long.MIN_VALUE, -1_000_000L, -1L, 0L, 1L, 500L, 100_000L, Long.MAX_VALUE
        )
        for (pos in testPositions) {
            val oracleResult = linearSearchOracle(empty, pos)
            val actualResult = LrcParser.findActiveLineIndex(empty, pos)
            assertEquals(-1, oracleResult)
            assertEquals("Empty list must return -1 for pos=$pos", oracleResult, actualResult)
        }
    }

    @Test
    fun testSingleLineList_exhaustiveBoundaries() {
        val lines = listOf(LrcLine(1000L, "Only line"))

        // Positions before line
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, Long.MIN_VALUE))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, -100L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 999L))

        // Position exactly on line
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1000L))

        // Positions after line
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1001L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 50_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, Long.MAX_VALUE))
    }

    @Test
    fun testTwoLinesList_exhaustiveBoundaries() {
        val lines = listOf(
            LrcLine(1000L, "First line"),
            LrcLine(3000L, "Second line")
        )

        // Before first line
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 999L))

        // Exactly on first line
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1000L))

        // Between first and second line
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1001L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 2000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 2999L))

        // Exactly on second line
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 3000L))

        // Beyond second line
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 3001L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 100_000L))
    }

    @Test
    fun testZeroStartTime_handlesZeroPositionCorrectly() {
        val lines = listOf(
            LrcLine(0L, "Starts immediately at 0ms"),
            LrcLine(5000L, "Verse at 5000ms")
        )

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, -1L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, -500L))

        assertEquals(0, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 4999L))

        assertEquals(1, LrcParser.findActiveLineIndex(lines, 5000L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 10000L))
    }

    @Test
    fun testRapidConsecutiveTimestamps_singleMillisecondGaps() {
        val lines = (0L..20L).map { LrcLine(it, "Line at $it ms") }

        for (pos in -5L..25L) {
            val expected = linearSearchOracle(lines, pos)
            val actual = LrcParser.findActiveLineIndex(lines, pos)
            assertEquals("Mismatch for pos=$pos", expected, actual)
        }
    }

    // =========================================================================
    // 2. Duplicate Timestamps (Adversarial Multi-Line Collisions)
    // =========================================================================

    @Test
    fun testDuplicateTimestamps_atBeginning() {
        val lines = listOf(
            LrcLine(1000L, "Line 0 (1000ms)"),
            LrcLine(1000L, "Line 1 (1000ms duplicate)"),
            LrcLine(1000L, "Line 2 (1000ms duplicate)"),
            LrcLine(2000L, "Line 3 (2000ms)")
        )

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 999L))

        // For identical timestamps, the oracle and binary search advance to the highest index
        val oracleAt1000 = linearSearchOracle(lines, 1000L)
        val actualAt1000 = LrcParser.findActiveLineIndex(lines, 1000L)
        assertEquals(2, oracleAt1000)
        assertEquals(oracleAt1000, actualAt1000)

        // Between 1000 and 2000
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 1500L))

        // At 2000
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 2000L))
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 3000L))
    }

    @Test
    fun testDuplicateTimestamps_inMiddle() {
        val lines = listOf(
            LrcLine(500L, "Line 0"),
            LrcLine(1000L, "Line 1"),
            LrcLine(1000L, "Line 2"),
            LrcLine(1000L, "Line 3"),
            LrcLine(2000L, "Line 4")
        )

        for (pos in listOf(0L, 499L, 500L, 750L, 999L, 1000L, 1001L, 1500L, 1999L, 2000L, 5000L)) {
            val expected = linearSearchOracle(lines, pos)
            val actual = LrcParser.findActiveLineIndex(lines, pos)
            assertEquals("Mismatch at pos=$pos", expected, actual)
        }
    }

    @Test
    fun testDuplicateTimestamps_atEnd() {
        val lines = listOf(
            LrcLine(500L, "Line 0"),
            LrcLine(1000L, "Line 1"),
            LrcLine(2000L, "Line 2"),
            LrcLine(2000L, "Line 3"),
            LrcLine(2000L, "Line 4")
        )

        for (pos in listOf(0L, 500L, 1000L, 1999L, 2000L, 2001L, 10000L)) {
            val expected = linearSearchOracle(lines, pos)
            val actual = LrcParser.findActiveLineIndex(lines, pos)
            assertEquals("Mismatch at pos=$pos", expected, actual)
        }
    }

    @Test
    fun testAllTimestampsIdentical() {
        val lines = (0..5).map { LrcLine(5000L, "Duplicate $it") }

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 4999L))
        assertEquals(5, LrcParser.findActiveLineIndex(lines, 5000L))
        assertEquals(5, LrcParser.findActiveLineIndex(lines, 6000L))
    }

    // =========================================================================
    // 3. Very Large Arrays & Massive Timestamps
    // =========================================================================

    @Test
    fun testMassiveList_10000Lines_exactAndIntermediatePositions() {
        val count = 10_000
        val lines = ArrayList<LrcLine>(count)
        for (i in 0 until count) {
            lines.add(LrcLine(i * 100L, "Line #$i"))
        }

        // Test every boundary point
        for (i in 0 until count) {
            val exactTime = i * 100L
            val actualExact = LrcParser.findActiveLineIndex(lines, exactTime)
            assertEquals("Failed on exact timestamp for index $i", i, actualExact)

            if (i < count - 1) {
                val midTime = exactTime + 50L
                val actualMid = LrcParser.findActiveLineIndex(lines, midTime)
                assertEquals("Failed on midpoint timestamp for index $i", i, actualMid)
            }
        }

        // Before first
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, -1L))
        // Beyond last
        assertEquals(count - 1, LrcParser.findActiveLineIndex(lines, count * 100L + 500L))
    }

    @Test
    fun testExtremelyLargeTimestamps_hoursAndDays() {
        val lines = listOf(
            LrcLine(0L, "Track start"),
            LrcLine(3_600_000L, "1 hour mark"),
            LrcLine(86_400_000L, "24 hour mark"),
            LrcLine(1_000_000_000L, "Very large timestamp"),
            LrcLine(Long.MAX_VALUE / 2, "Half Long.MAX_VALUE")
        )

        for (line in lines) {
            val actual = LrcParser.findActiveLineIndex(lines, line.startTimeMs)
            val expected = linearSearchOracle(lines, line.startTimeMs)
            assertEquals(expected, actual)
        }

        // Test near Long.MAX_VALUE
        val posFar = Long.MAX_VALUE - 100L
        assertEquals(lines.lastIndex, LrcParser.findActiveLineIndex(lines, posFar))
    }

    // =========================================================================
    // 4. Exhaustive Randomized Fuzz Testing Against Linear Oracle
    // =========================================================================

    @Test
    fun testRandomFuzzAgainstLinearOracle_1000Lists_100QueriesEach() {
        val rng = Random(42L) // Deterministic seed for reproducible testing
        val listCount = 1_000
        val queriesPerList = 100

        var totalVerifications = 0

        for (testIter in 0 until listCount) {
            // Random list size between 1 and 300
            val size = rng.nextInt(300) + 1
            val rawTimes = ArrayList<Long>(size)

            var currentTime = rng.nextLong().coerceAtLeast(0L) % 10_000L
            for (i in 0 until size) {
                // Random time delta (0 to 1000ms), 0 allows duplicates
                val delta = (rng.nextInt(1001)).toLong()
                currentTime += delta
                rawTimes.add(currentTime)
            }
            rawTimes.sort()

            val lines = rawTimes.mapIndexed { index, time -> LrcLine(time, "Lyric line $index") }

            val minTime = lines.first().startTimeMs
            val maxTime = lines.last().startTimeMs

            // Generate diverse query positions:
            // 1. Exactly on lines
            // 2. 1ms before / after line
            // 3. Random positions in range [minTime - 5000, maxTime + 5000]
            // 4. Extreme values
            for (q in 0 until queriesPerList) {
                val queryPos: Long = when (rng.nextInt(6)) {
                    0 -> lines[rng.nextInt(size)].startTimeMs // Exactly on line
                    1 -> lines[rng.nextInt(size)].startTimeMs - 1L // 1ms before line
                    2 -> lines[rng.nextInt(size)].startTimeMs + 1L // 1ms after line
                    3 -> minTime - (rng.nextInt(1000) + 1L) // Before first line
                    4 -> maxTime + (rng.nextInt(5000) + 1L) // After last line
                    else -> minTime + (rng.nextLong().coerceAtLeast(0L) % (maxOf(1L, maxTime - minTime + 1L))) // Inside range
                }

                val oracleIndex = linearSearchOracle(lines, queryPos)
                val binarySearchIndex = LrcParser.findActiveLineIndex(lines, queryPos)

                if (oracleIndex != binarySearchIndex) {
                    val message = "Oracle mismatch! Iteration=$testIter, queryPos=$queryPos, " +
                            "Oracle=$oracleIndex, BinarySearch=$binarySearchIndex, " +
                            "LinesNear=${lines.subList(maxOf(0, oracleIndex - 2), minOf(size, oracleIndex + 3))}"
                    assertEquals(message, oracleIndex, binarySearchIndex)
                }

                totalVerifications++
            }
        }

        assertEquals(100_000, totalVerifications)
    }

    // =========================================================================
    // 5. Performance Stress: O(log N) Scalability
    // =========================================================================

    @Test
    fun testBinarySearchScalability_significantlyOutperformsLinearSearch() {
        val size = 5_000
        val lines = (0 until size).map { LrcLine(it * 100L, "Line $it") }

        val queries = (0..10_000).map { (it * 73L) % (size * 100L) }

        // Warm up JIT
        for (q in queries.take(500)) {
            LrcParser.findActiveLineIndex(lines, q)
            linearSearchOracle(lines, q)
        }

        // Measure Binary Search
        val t0 = System.nanoTime()
        var checksumBinary = 0
        for (q in queries) {
            checksumBinary += LrcParser.findActiveLineIndex(lines, q)
        }
        val binaryDurationNs = System.nanoTime() - t0

        // Measure Linear Search
        val t1 = System.nanoTime()
        var checksumLinear = 0
        for (q in queries) {
            checksumLinear += linearSearchOracle(lines, q)
        }
        val linearDurationNs = System.nanoTime() - t1

        assertEquals("Checksums must match between binary and linear search", checksumLinear, checksumBinary)
        assertTrue("Binary search ($binaryDurationNs ns) should be faster than linear search ($linearDurationNs ns)",
            binaryDurationNs <= linearDurationNs)
    }
}

