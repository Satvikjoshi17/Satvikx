package com.satvik.satvikx

import com.satvik.satvikx.data.parser.LrcParser
import com.satvik.satvikx.data.remote.model.LrcLine
import java.util.Random

fun main() {
    println("=================================================================")
    println("  SATVIK-X LrcParser.findActiveLineIndex EMPIRICAL ORACLE TEST   ")
    println("=================================================================")

    var passed = 0
    var total = 0

    fun check(name: String, condition: Boolean, detail: String = "") {
        total++
        if (condition) {
            passed++
            println("  [PASS] $name")
        } else {
            System.err.println("  [FAIL] $name: $detail")
            throw AssertionError("Check failed: $name - $detail")
        }
    }

    fun linearSearchOracle(lines: List<LrcLine>, positionMs: Long): Int {
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

    // 1. Empty list
    val empty = emptyList<LrcLine>()
    for (pos in listOf(Long.MIN_VALUE, -100L, -1L, 0L, 1L, 5000L, Long.MAX_VALUE)) {
        check("Empty list pos=$pos returns -1", LrcParser.findActiveLineIndex(empty, pos) == -1)
    }

    // 2. Single line
    val single = listOf(LrcLine(1000L, "Single"))
    check("Single line: before", LrcParser.findActiveLineIndex(single, 999L) == -1)
    check("Single line: exact", LrcParser.findActiveLineIndex(single, 1000L) == 0)
    check("Single line: after", LrcParser.findActiveLineIndex(single, 1001L) == 0)
    check("Single line: far after", LrcParser.findActiveLineIndex(single, 1_000_000L) == 0)

    // 3. Two lines
    val two = listOf(LrcLine(1000L, "First"), LrcLine(3000L, "Second"))
    check("Two lines: before", LrcParser.findActiveLineIndex(two, 999L) == -1)
    check("Two lines: exact first", LrcParser.findActiveLineIndex(two, 1000L) == 0)
    check("Two lines: between", LrcParser.findActiveLineIndex(two, 2000L) == 0)
    check("Two lines: exact second", LrcParser.findActiveLineIndex(two, 3000L) == 1)
    check("Two lines: beyond second", LrcParser.findActiveLineIndex(two, 5000L) == 1)

    // 4. Zero start time
    val zeroStart = listOf(LrcLine(0L, "Start"), LrcLine(5000L, "Later"))
    check("Zero start: negative pos", LrcParser.findActiveLineIndex(zeroStart, -1L) == -1)
    check("Zero start: exact 0ms", LrcParser.findActiveLineIndex(zeroStart, 0L) == 0)
    check("Zero start: 1ms", LrcParser.findActiveLineIndex(zeroStart, 1L) == 0)
    check("Zero start: exact 5000ms", LrcParser.findActiveLineIndex(zeroStart, 5000L) == 1)

    // 5. Rapid 1ms timestamps
    val rapid = (0L..20L).map { LrcLine(it, "Line $it") }
    for (p in -5L..25L) {
        val expected = linearSearchOracle(rapid, p)
        val actual = LrcParser.findActiveLineIndex(rapid, p)
        check("Rapid 1ms pos=$p", expected == actual)
    }

    // 6. Duplicate timestamps (collisions)
    val dupsStart = listOf(LrcLine(1000L, "A"), LrcLine(1000L, "B"), LrcLine(1000L, "C"), LrcLine(2000L, "D"))
    check("Duplicates start: exact", LrcParser.findActiveLineIndex(dupsStart, 1000L) == linearSearchOracle(dupsStart, 1000L))
    check("Duplicates start: between", LrcParser.findActiveLineIndex(dupsStart, 1500L) == linearSearchOracle(dupsStart, 1500L))

    val dupsMiddle = listOf(LrcLine(500L, "A"), LrcLine(1000L, "B"), LrcLine(1000L, "C"), LrcLine(2000L, "D"))
    check("Duplicates middle: exact", LrcParser.findActiveLineIndex(dupsMiddle, 1000L) == linearSearchOracle(dupsMiddle, 1000L))

    val dupsAll = (0..5).map { LrcLine(5000L, "Dup $it") }
    check("Duplicates all: exact", LrcParser.findActiveLineIndex(dupsAll, 5000L) == 5)

    // 7. Massive array (10,000 lines)
    println("  Running 10,000-line array boundary test...")
    val massiveCount = 10_000
    val massive = (0 until massiveCount).map { LrcLine(it * 100L, "Line $it") }
    for (i in 0 until massiveCount step 50) {
        val exact = LrcParser.findActiveLineIndex(massive, i * 100L)
        if (exact != i) throw AssertionError("Massive exact fail at $i: got $exact")
        val mid = LrcParser.findActiveLineIndex(massive, i * 100L + 50L)
        if (mid != i) throw AssertionError("Massive mid fail at $i: got $mid")
    }
    check("Massive 10,000 lines sampled verification passed", true)

    // 8. Exhaustive Randomized Fuzz Testing (1,000 lists x 100 queries = 100,000 verifications)
    println("  Running 100,000 randomized fuzz queries against linear search oracle...")
    val rng = Random(42L)
    var fuzzMismatches = 0
    for (iter in 0 until 1_000) {
        val size = rng.nextInt(300) + 1
        val times = ArrayList<Long>(size)
        var cur = (rng.nextLong() and 0x7FFFFFFF) % 10_000L
        for (i in 0 until size) {
            cur += rng.nextInt(1001).toLong()
            times.add(cur)
        }
        times.sort()
        val lines = times.mapIndexed { idx, t -> LrcLine(t, "Line $idx") }
        val minT = lines.first().startTimeMs
        val maxT = lines.last().startTimeMs

        for (q in 0 until 100) {
            val queryPos: Long = when (rng.nextInt(6)) {
                0 -> lines[rng.nextInt(size)].startTimeMs
                1 -> lines[rng.nextInt(size)].startTimeMs - 1L
                2 -> lines[rng.nextInt(size)].startTimeMs + 1L
                3 -> minT - (rng.nextInt(1000) + 1L)
                4 -> maxT + (rng.nextInt(5000) + 1L)
                else -> minT + ((rng.nextLong() and 0x7FFFFFFF) % (maxOf(1L, maxT - minT + 1L)))
            }
            val oracleAns = linearSearchOracle(lines, queryPos)
            val binaryAns = LrcParser.findActiveLineIndex(lines, queryPos)
            if (oracleAns != binaryAns) {
                fuzzMismatches++
            }
        }
    }
    check("100,000 randomized fuzz queries against linear oracle", fuzzMismatches == 0, "$fuzzMismatches mismatches")

    // 9. O(log N) Performance Benchmark
    val benchSize = 5_000
    val benchLines = (0 until benchSize).map { LrcLine(it * 100L, "Bench $it") }
    val benchQueries = (0..20_000).map { (it * 73L) % (benchSize * 100L) }
    
    // Warmup
    for (q in benchQueries.take(500)) {
        LrcParser.findActiveLineIndex(benchLines, q)
        linearSearchOracle(benchLines, q)
    }

    val t0 = System.nanoTime()
    var binaryChecksum = 0
    for (q in benchQueries) {
        binaryChecksum += LrcParser.findActiveLineIndex(benchLines, q)
    }
    val binaryNs = System.nanoTime() - t0

    val t1 = System.nanoTime()
    var linearChecksum = 0
    for (q in benchQueries) {
        linearChecksum += linearSearchOracle(benchLines, q)
    }
    val linearNs = System.nanoTime() - t1

    check("Benchmark checksums match", binaryChecksum == linearChecksum)
    val speedup = linearNs.toDouble() / maxOf(1L, binaryNs).toDouble()
    println("  Binary Search: ${binaryNs / 1_000_000.0} ms | Linear Search: ${linearNs / 1_000_000.0} ms (Speedup: ${String.format(java.util.Locale.US, "%.1f", speedup)}x)")
    check("Binary search is strictly faster than linear search", binaryNs < linearNs)

    println("\n=================================================================")
    println("  BINARY SEARCH EMPIRICAL CHECKS PASSED: $passed / $total        ")
    println("  VERDICT: APPROVE                                               ")
    println("=================================================================")
}

