package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao
import com.satvik.satvikx.data.local.entity.PlaybackTelemetryEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.remote.model.AudioStreamResult
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import com.satvik.satvikx.data.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.math.exp

/**
 * Enterprise Unit Test Suite for SatvikX Chief Recommendation Engine.
 *
 * Exhaustively validates:
 * 1. Mathematical exponential time decay: Weight = exp(-days / 30.0).
 * 2. Intelligent session vibe classification across all 8 canonical modes.
 * 3. 8-stream candidate retrieval pool guarantee (>= 300 candidates).
 * 4. Enterprise multi-objective probability ranking formula.
 * 5. Human-curated queue sequencer constraints (80/20 ratio, 1-in-5 discovery, no duplicates, max 2 artist, max 3 genre).
 * 6. Real-time adaptive session learning & triggers (3 skips, 2 replays, major genre switch, like event).
 */
class RecommendationEngineTest {

    private lateinit var vibeClassifier: SessionVibeClassifier
    private lateinit var queueSequencer: HumanCuratedQueueSequencer
    private lateinit var sessionManager: AdaptiveSessionManager
    private lateinit var fakeTelemetryDao: FakePlaybackTelemetryDao
    private lateinit var rankingScoringEngine: RankingScoringEngine

    @Before
    fun setup() {
        vibeClassifier = SessionVibeClassifier()
        queueSequencer = HumanCuratedQueueSequencer(vibeClassifier)
        sessionManager = AdaptiveSessionManager(vibeClassifier)
        fakeTelemetryDao = FakePlaybackTelemetryDao()
        rankingScoringEngine = RankingScoringEngine(vibeClassifier, fakeTelemetryDao)
    }

    // =========================================================================
    // 1. UserTasteProfiler & Mathematical Time Decay Tests
    // =========================================================================

    @Test
    fun timeDecay_zeroDays_returnsExactlyOne() {
        val now = 1000000000000L
        val weight = UserTasteProfiler.calculateDecayWeight(now, now)
        assertEquals(1.0f, weight, 0.0001f)
    }

    @Test
    fun timeDecay_fifteenDays_returnsExpectedExponentialDecay() {
        val now = 1000000000000L
        val fifteenDaysAgo = now - (15L * 86400000L)
        val expected = exp(-15.0 / 30.0).toFloat() // ~0.60653
        val weight = UserTasteProfiler.calculateDecayWeight(fifteenDaysAgo, now)
        assertEquals(expected, weight, 0.001f)
    }

    @Test
    fun timeDecay_thirtyDays_returnsOneOverE() {
        val now = 1000000000000L
        val thirtyDaysAgo = now - (30L * 86400000L)
        val expected = exp(-1.0).toFloat() // ~0.367879
        val weight = UserTasteProfiler.calculateDecayWeight(thirtyDaysAgo, now)
        assertEquals(expected, weight, 0.001f)
    }

    @Test
    fun timeDecay_sixtyDays_returnsOneOverESquared() {
        val now = 1000000000000L
        val sixtyDaysAgo = now - (60L * 86400000L)
        val expected = exp(-2.0).toFloat() // ~0.135335
        val weight = UserTasteProfiler.calculateDecayWeight(sixtyDaysAgo, now)
        assertEquals(expected, weight, 0.001f)
    }

    @Test
    fun timeDecay_futureTimestamp_clampedToOne() {
        val now = 1000000000000L
        val future = now + (10L * 86400000L)
        val weight = UserTasteProfiler.calculateDecayWeight(future, now)
        assertEquals(1.0f, weight, 0.0001f)
    }

    @Test
    fun timeDecay_ancientTimestamp_clampedToMinimumThreshold() {
        val now = 1000000000000L
        val twoYearsAgo = now - (730L * 86400000L)
        val weight = UserTasteProfiler.calculateDecayWeight(twoYearsAgo, now)
        assertEquals(0.01f, weight, 0.0001f)
    }

    // =========================================================================
    // 2. SessionVibeClassifier Tests (All 8 Canonical Modes)
    // =========================================================================

    @Test
    fun classifyVibe_gymMode_detectedAccurately() {
        val phonkTrack = TrackEntity(id = "gym1", title = "Murder In My Mind", artist = "KORDHELL")
        val hardstyleTrack = TrackEntity(id = "gym2", title = "Speed Up Hardstyle Phonk", artist = "Tevvez")
        val workoutTrack = TrackEntity(id = "gym3", title = "Gym Motivation Drift Workout", artist = "SXID")

        assertEquals(SessionVibeMode.GYM_MODE, vibeClassifier.classifyTrackVibe(phonkTrack))
        assertEquals(SessionVibeMode.GYM_MODE, vibeClassifier.classifyTrackVibe(hardstyleTrack))
        assertEquals(SessionVibeMode.GYM_MODE, vibeClassifier.classifyTrackVibe(workoutTrack))
    }

    @Test
    fun classifyVibe_nightDriveMode_detectedAccurately() {
        val synthTrack = TrackEntity(id = "nd1", title = "Sunset", artist = "The Midnight")
        val retroTrack = TrackEntity(id = "nd2", title = "Running in the Night", artist = "FM-84")
        val driveTrack = TrackEntity(id = "nd3", title = "Cyberpunk 2077 Night Drive Neon", artist = "Hyper")

        assertEquals(SessionVibeMode.NIGHT_DRIVE, vibeClassifier.classifyTrackVibe(synthTrack))
        assertEquals(SessionVibeMode.NIGHT_DRIVE, vibeClassifier.classifyTrackVibe(retroTrack))
        assertEquals(SessionVibeMode.NIGHT_DRIVE, vibeClassifier.classifyTrackVibe(driveTrack))
    }

    @Test
    fun classifyVibe_lofiFocusMode_detectedAccurately() {
        val anuvTrack = TrackEntity(id = "lofi1", title = "Baarishein", artist = "Anuv Jain")
        val kuhadTrack = TrackEntity(id = "lofi2", title = "cold/mess", artist = "Prateek Kuhad")
        val chillhopTrack = TrackEntity(id = "lofi3", title = "Late Night Study Chill Lofi Beats", artist = "Chillhop")

        assertEquals(SessionVibeMode.LOFI_FOCUS, vibeClassifier.classifyTrackVibe(anuvTrack))
        assertEquals(SessionVibeMode.LOFI_FOCUS, vibeClassifier.classifyTrackVibe(kuhadTrack))
        assertEquals(SessionVibeMode.LOFI_FOCUS, vibeClassifier.classifyTrackVibe(chillhopTrack))
    }

    @Test
    fun classifyVibe_punjabiMode_detectedAccurately() {
        val sidhuTrack = TrackEntity(id = "pb1", title = "295", artist = "Sidhu Moose Wala")
        val aujlaTrack = TrackEntity(id = "pb2", title = "Softly", artist = "Karan Aujla")
        val diljitTrack = TrackEntity(id = "pb3", title = "Born to Shine", artist = "Diljit Dosanjh")
        val shubhTrack = TrackEntity(id = "pb4", title = "Elevated", artist = "Shubh")

        assertEquals(SessionVibeMode.PUNJABI, vibeClassifier.classifyTrackVibe(sidhuTrack))
        assertEquals(SessionVibeMode.PUNJABI, vibeClassifier.classifyTrackVibe(aujlaTrack))
        assertEquals(SessionVibeMode.PUNJABI, vibeClassifier.classifyTrackVibe(diljitTrack))
        assertEquals(SessionVibeMode.PUNJABI, vibeClassifier.classifyTrackVibe(shubhTrack))
    }

    @Test
    fun classifyVibe_rapMode_detectedAccurately() {
        val kendrickTrack = TrackEntity(id = "rap1", title = "HUMBLE.", artist = "Kendrick Lamar")
        val divineTrack = TrackEntity(id = "rap2", title = "Mirchi", artist = "DIVINE")
        val krsnaTrack = TrackEntity(id = "rap3", title = "Makasam", artist = "KR\$NA")
        val stanTrack = TrackEntity(id = "rap4", title = "Basti Ka Hasti", artist = "MC Stan")

        assertEquals(SessionVibeMode.RAP, vibeClassifier.classifyTrackVibe(kendrickTrack))
        assertEquals(SessionVibeMode.RAP, vibeClassifier.classifyTrackVibe(divineTrack))
        assertEquals(SessionVibeMode.RAP, vibeClassifier.classifyTrackVibe(krsnaTrack))
        assertEquals(SessionVibeMode.RAP, vibeClassifier.classifyTrackVibe(stanTrack))
    }

    @Test
    fun classifyVibe_romanticMode_detectedAccurately() {
        val arijitTrack = TrackEntity(id = "rom1", title = "Kesariya", artist = "Arijit Singh, Pritam")
        val mithoonTrack = TrackEntity(id = "rom2", title = "Tum Hi Ho", artist = "Arijit Singh, Mithoon")
        val jubinTrack = TrackEntity(id = "rom3", title = "Raataan Lambiyan", artist = "Jubin Nautiyal")

        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(arijitTrack))
        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(mithoonTrack))
        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(jubinTrack))
    }

    @Test
    fun classifyVibe_globalPopBallads_detectedAsRomantic() {
        val edTrack = TrackEntity(id = "pop_rom1", title = "Perfect", artist = "Ed Sheeran")
        val lewisTrack = TrackEntity(id = "pop_rom2", title = "Someone You Loved", artist = "Lewis Capaldi")
        val sanchezTrack = TrackEntity(id = "pop_rom3", title = "Until I Found You", artist = "Stephen Sanchez")

        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(edTrack))
        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(lewisTrack))
        assertEquals(SessionVibeMode.ROMANTIC, vibeClassifier.classifyTrackVibe(sanchezTrack))
    }

    @Test
    fun classifyVibe_devotionalMode_detectedAccurately() {
        val hanumanTrack = TrackEntity(id = "dev1", title = "Hanuman Chalisa", artist = "Gulshan Kumar")
        val shivTrack = TrackEntity(id = "dev2", title = "Shiv Tandav Stotram", artist = "Shankar Mahadevan")
        val krishnaTrack = TrackEntity(id = "dev3", title = "Shri Krishna Govind Hare Murari", artist = "Jubin Nautiyal")

        assertEquals(SessionVibeMode.DEVOTIONAL, vibeClassifier.classifyTrackVibe(hanumanTrack))
        assertEquals(SessionVibeMode.DEVOTIONAL, vibeClassifier.classifyTrackVibe(shivTrack))
        assertEquals(SessionVibeMode.DEVOTIONAL, vibeClassifier.classifyTrackVibe(krishnaTrack))
    }

    @Test
    fun classifyVibe_partyMode_detectedAccurately() {
        val garrixTrack = TrackEntity(id = "party1", title = "Animals", artist = "Martin Garrix")
        val walkerTrack = TrackEntity(id = "party2", title = "Faded EDM Club Mix", artist = "Alan Walker")
        val guettaTrack = TrackEntity(id = "party3", title = "Titanium Dance Festival", artist = "David Guetta")

        assertEquals(SessionVibeMode.PARTY, vibeClassifier.classifyTrackVibe(garrixTrack))
        assertEquals(SessionVibeMode.PARTY, vibeClassifier.classifyTrackVibe(walkerTrack))
        assertEquals(SessionVibeMode.PARTY, vibeClassifier.classifyTrackVibe(guettaTrack))
    }

    @Test
    fun classifyVibe_unknown_defaultsToDiscovery() {
        val unknownTrack = TrackEntity(id = "unk1", title = "Uncatalogued Echoes", artist = "Indie Unknown Ensemble")
        assertEquals(SessionVibeMode.DISCOVERY, vibeClassifier.classifyTrackVibe(unknownTrack))
    }

    @Test
    fun estimateEnergyLevel_adjustsForSpeedAndAcousticModifiers() {
        val baseTrack = TrackEntity(id = "e1", title = "Some Track", artist = "Some Artist")
        val slowedTrack = TrackEntity(id = "e2", title = "Some Track Slowed Reverb Acoustic", artist = "Some Artist")
        val fastTrack = TrackEntity(id = "e3", title = "Some Track Remix Speed Up Phonk", artist = "Some Artist")

        val baseEnergy = vibeClassifier.estimateEnergyLevel(baseTrack)
        val slowedEnergy = vibeClassifier.estimateEnergyLevel(slowedTrack)
        val fastEnergy = vibeClassifier.estimateEnergyLevel(fastTrack)

        assertTrue(slowedEnergy < baseEnergy)
        assertTrue(fastEnergy > baseEnergy)
    }

    // =========================================================================
    // 3. CandidateRetrievalEngine Candidate Pool Guarantee Tests
    // =========================================================================

    @Test
    fun candidatePool_guaranteesMinimum300CandidatesEvenOffline() = runBlocking {
        val fakeSaavn = FakeSaavnMediaEngine()
        val fakeNewPipe = FakeNewPipeYouTubeEngine()
        val fakeStreamRepo = FakeStreamRepository()

        val engine = CandidateRetrievalEngine(fakeSaavn, fakeNewPipe, fakeStreamRepo, vibeClassifier)

        val seedTrack = TrackEntity(id = "seed1", title = "Kesariya", artist = "Arijit Singh")
        val sessionProfile = ShortTermSessionProfile(
            sessionTracks = listOf(seedTrack),
            currentVibe = SessionVibeMode.ROMANTIC,
            currentEnergyLevel = 0.55f,
            sessionArtistWeights = mapOf("arijit singh" to 3.0f),
            sessionGenreWeights = mapOf("romantic" to 2.0f),
            consecutiveSkips = 0,
            consecutiveReplays = 0,
            explorationTemperature = 0.20f,
            lastInteractionTimeMs = System.currentTimeMillis()
        )
        val longTermProfile = LongTermProfile(
            artistScores = mapOf("arijit singh" to 10.0f),
            genreScores = mapOf("romantic" to 5.0f),
            topArtists = listOf("arijit singh"),
            topGenres = listOf("romantic"),
            vaultTracks = emptyList(),
            heavyRotationTracks = emptyList(),
            profileGeneratedTimestamp = System.currentTimeMillis()
        )

        val candidates = engine.retrieveCandidatePool(seedTrack, sessionProfile, longTermProfile)

        assertTrue(
            "Candidate pool must contain at least 300 candidates before ranking, but got ${candidates.size}",
            candidates.size >= 300
        )
    }

    // =========================================================================
    // 4. RankingScoringEngine Multi-Objective Probability Ranking Tests
    // =========================================================================

    @Test
    fun rankingScoring_calculatesExactLinearCombinationFormula() = runBlocking {
        val candidate = TrackEntity(
            id = "c1",
            title = "Kesariya",
            artist = "Arijit Singh",
            durationSeconds = 268L
        )
        val sessionProfile = ShortTermSessionProfile(
            sessionTracks = emptyList(),
            currentVibe = SessionVibeMode.ROMANTIC,
            currentEnergyLevel = 0.55f,
            sessionArtistWeights = mapOf("arijit singh" to 5.0f),
            sessionGenreWeights = mapOf("romantic" to 3.0f),
            consecutiveSkips = 0,
            consecutiveReplays = 0,
            explorationTemperature = 0.20f,
            lastInteractionTimeMs = System.currentTimeMillis()
        )
        val longTermProfile = LongTermProfile(
            artistScores = mapOf("arijit singh" to 15.0f),
            genreScores = mapOf("romantic" to 8.0f),
            topArtists = listOf("arijit singh"),
            topGenres = listOf("romantic"),
            vaultTracks = listOf(candidate),
            heavyRotationTracks = listOf(candidate),
            profileGeneratedTimestamp = System.currentTimeMillis()
        )

        val scores = rankingScoringEngine.scoreAndRankCandidates(
            candidates = listOf(candidate),
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = emptyList()
        )

        assertEquals(1, scores.size)
        val score = scores.first()

        val expectedLinear = (0.30f * score.sessionMatch) +
                (0.25f * score.tasteMatch) +
                (0.15f * score.completionProbability) +
                (0.10f * score.replayProbability) +
                (0.10f * score.discoveryPotential) +
                (0.05f * score.freshness) +
                (0.05f * score.trendMomentum) -
                (0.20f * score.skipProbability)

        assertEquals(expectedLinear, score.finalScore, 0.0001f)
    }

    @Test
    fun rankingScoring_penalizesSkippedArtistsAndLowersFinalScore() = runBlocking {
        val highAffinityCandidate = TrackEntity(id = "fav", title = "Kesariya", artist = "Arijit Singh")
        val skippedCandidate = TrackEntity(id = "skip", title = "Skipped Song", artist = "Skipped Artist")

        val sessionProfile = ShortTermSessionProfile(
            sessionTracks = emptyList(),
            currentVibe = SessionVibeMode.ROMANTIC,
            currentEnergyLevel = 0.55f,
            sessionArtistWeights = mapOf("arijit singh" to 6.0f, "skipped artist" to -5.0f),
            sessionGenreWeights = mapOf("romantic" to 4.0f),
            consecutiveSkips = 2,
            consecutiveReplays = 0,
            explorationTemperature = 0.50f,
            lastInteractionTimeMs = System.currentTimeMillis()
        )
        val longTermProfile = LongTermProfile(
            artistScores = mapOf("arijit singh" to 10.0f),
            genreScores = mapOf("romantic" to 5.0f),
            topArtists = listOf("arijit singh"),
            topGenres = listOf("romantic"),
            vaultTracks = listOf(highAffinityCandidate),
            heavyRotationTracks = emptyList(),
            profileGeneratedTimestamp = System.currentTimeMillis()
        )

        val ranked = rankingScoringEngine.scoreAndRankCandidates(
            candidates = listOf(skippedCandidate, highAffinityCandidate),
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = emptyList()
        )

        assertTrue(ranked.first().track.id == highAffinityCandidate.id)
        val skippedScore = ranked.first { it.track.id == skippedCandidate.id }
        val highAffinityScore = ranked.first { it.track.id == highAffinityCandidate.id }

        assertTrue(skippedScore.skipProbability > highAffinityScore.skipProbability)
        assertTrue(highAffinityScore.finalScore > skippedScore.finalScore)
    }

    @Test
    fun ranking_activeSeedArtistAndCollaborators_rankedAboveOutOfVibeHistoricalArtists() = runBlocking {
        val seedTrack = TrackEntity(id = "seed_ed", title = "Perfect", artist = "Ed Sheeran")
        sessionManager.onTrackStarted(seedTrack)
        val sessionProfile = sessionManager.getSessionProfile()

        // User's old history was Punjabi/Phonk
        val longTermProfile = LongTermProfile(
            artistScores = mapOf("sidhu moose wala" to 20.0f, "kordhell" to 15.0f),
            genreScores = mapOf("punjabi" to 20.0f, "phonk" to 15.0f),
            topArtists = listOf("Sidhu Moose Wala", "KORDHELL"),
            topGenres = listOf("punjabi", "phonk"),
            vaultTracks = emptyList(),
            heavyRotationTracks = emptyList(),
            primaryArtist = "Sidhu Moose Wala",
            profileGeneratedTimestamp = System.currentTimeMillis()
        )

        val edCandidate = TrackEntity(id = "c_ed", title = "Photograph", artist = "Ed Sheeran")
        val lewisCandidate = TrackEntity(id = "c_lewis", title = "Someone You Loved", artist = "Lewis Capaldi")
        val outOfVibeCandidate = TrackEntity(id = "c_out", title = "295", artist = "Sidhu Moose Wala")

        val ranked = rankingScoringEngine.scoreAndRankCandidates(
            candidates = listOf(outOfVibeCandidate, lewisCandidate, edCandidate),
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = emptyList()
        )

        val edScore = ranked.first { it.track.id == edCandidate.id }
        val lewisScore = ranked.first { it.track.id == lewisCandidate.id }
        val outScore = ranked.first { it.track.id == outOfVibeCandidate.id }

        assertTrue("Ed Sheeran score (${edScore.finalScore}) must exceed out-of-vibe score (${outScore.finalScore})", edScore.finalScore > outScore.finalScore)
        assertTrue("Lewis Capaldi score (${lewisScore.finalScore}) must exceed out-of-vibe score (${outScore.finalScore})", lewisScore.finalScore > outScore.finalScore)
    }

    // =========================================================================
    // 5. HumanCuratedQueueSequencer Pacing & Diversity Tests
    // =========================================================================

    @Test
    fun sequencer_excludesSeedTrackFromQueue() {
        val seed = TrackEntity(id = "seed1", title = "Kesariya", artist = "Arijit Singh")
        val seedVariant = TrackEntity(id = "seed1_var", title = "Kesariya (Official Audio)", artist = "Arijit Singh")
        val candidate2 = TrackEntity(id = "c2", title = "Tum Hi Ho", artist = "Arijit Singh")
        val candidate3 = TrackEntity(id = "c3", title = "Raataan Lambiyan", artist = "Jubin Nautiyal")

        val scored = listOf(
            CandidateScore(seed, 0.95f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(seedVariant, 0.94f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(candidate2, 0.85f, 0.8f, 0.8f, 0.8f, 0.8f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(candidate3, 0.80f, 0.8f, 0.8f, 0.8f, 0.8f, 0.1f, 0.9f, 0.8f, 0.1f, false)
        )

        val queue = queueSequencer.sequenceQueue(scored, seedTrack = seed, limit = 10)

        assertFalse("Seed track must never be in queue", queue.any { it.id == seed.id })
        assertFalse("Seed variant must never be in queue", queue.any { it.id == seedVariant.id })
    }

    @Test
    fun sequencer_enforcesMaxTwoConsecutiveTracksBySameArtist() {
        val t1 = TrackEntity(id = "a1", title = "Song One", artist = "Drake")
        val t2 = TrackEntity(id = "a2", title = "Song Two", artist = "Drake")
        val t3 = TrackEntity(id = "a3", title = "Song Three", artist = "Drake")
        val t4 = TrackEntity(id = "b1", title = "Song Four", artist = "Travis Scott")
        val t5 = TrackEntity(id = "a4", title = "Song Five", artist = "Drake")

        val scored = listOf(
            CandidateScore(t1, 0.95f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(t2, 0.94f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(t3, 0.93f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(t4, 0.90f, 0.8f, 0.8f, 0.8f, 0.8f, 0.1f, 0.9f, 0.8f, 0.1f, false),
            CandidateScore(t5, 0.85f, 0.8f, 0.8f, 0.8f, 0.8f, 0.1f, 0.9f, 0.8f, 0.1f, false)
        )

        val queue = queueSequencer.sequenceQueue(scored, seedTrack = null, limit = 5)

        for (i in 2 until queue.size) {
            val a0 = queue[i - 2].cleanArtistName().lowercase(Locale.ROOT)
            val a1 = queue[i - 1].cleanArtistName().lowercase(Locale.ROOT)
            val a2 = queue[i].cleanArtistName().lowercase(Locale.ROOT)
            assertFalse(
                "Cannot have 3 consecutive tracks by same artist: $a0 at index $i",
                a0 == a1 && a1 == a2
            )
        }
    }

    @Test
    fun sequencer_enforcesMaxThreeConsecutiveTracksFromSameGenre() {
        val tracks = (1..6).map { i ->
            TrackEntity(id = "g$i", title = "Sidhu Track $i", artist = "Sidhu Moose Wala $i")
        }
        val alternate = TrackEntity(id = "alt1", title = "Animals Dance Party EDM", artist = "Martin Garrix")

        val scored = tracks.map {
            CandidateScore(it, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, false)
        } + CandidateScore(alternate, 0.85f, 0.8f, 0.8f, 0.8f, 0.8f, 0.1f, 0.9f, 0.8f, 0.1f, false)

        val queue = queueSequencer.sequenceQueue(scored, seedTrack = null, limit = 5)

        for (i in 3 until queue.size) {
            val g0 = vibeClassifier.detectGenres("${queue[i - 3].title} ${queue[i - 3].artist}").firstOrNull()
            val g1 = vibeClassifier.detectGenres("${queue[i - 2].title} ${queue[i - 2].artist}").firstOrNull()
            val g2 = vibeClassifier.detectGenres("${queue[i - 1].title} ${queue[i - 1].artist}").firstOrNull()
            val g3 = vibeClassifier.detectGenres("${queue[i].title} ${queue[i].artist}").firstOrNull()
            if (g0 != null) {
                assertFalse(
                    "Cannot have 4 consecutive tracks from same genre: $g0 at index $i",
                    g0 == g1 && g1 == g2 && g2 == g3
                )
            }
        }
    }

    @Test
    fun sequencer_injectsDiscoveryCandidateEveryFiveTracks() {
        val familiarTitles = listOf(
            "Kesariya", "Tum Hi Ho", "Raataan Lambiyan", "Apna Bana Le", "Channa Mereya",
            "Tera Hone Laga", "Zara Sa", "Deva Deva", "Kal Ho Naa Ho", "Agar Tum Saath",
            "Pehla Nasha", "Tujh Mein Rab", "Jeena Jeena", "Sun Saathiya", "Samjhawan"
        )
        val familiarArtists = listOf(
            "Arijit Singh", "Mithoon", "Jubin Nautiyal", "Sachin-Jigar", "Pritam",
            "Atif Aslam", "KK", "Amit Trivedi", "Sonu Nigam", "Alka Yagnik",
            "Udit Narayan", "Roop Kumar", "Mohit Chauhan", "Divya Kumar", "Shreya Ghoshal"
        )
        val familiar = familiarTitles.indices.map { i ->
            CandidateScore(
                track = TrackEntity(id = "fam_$i", title = familiarTitles[i], artist = familiarArtists[i]),
                finalScore = 0.90f - (i * 0.01f),
                sessionMatch = 0.9f,
                tasteMatch = 0.9f,
                completionProbability = 0.8f,
                replayProbability = 0.7f,
                discoveryPotential = 0.1f,
                freshness = 0.9f,
                trendMomentum = 0.8f,
                skipProbability = 0.1f,
                isDiscovery = false
            )
        }
        val discoveryTitles = listOf("Blinding Lights", "Starboy", "Sunset", "Nightcall", "Resonance")
        val discoveryArtists = listOf("The Weeknd", "Daft Punk", "The Midnight", "Kavinsky", "HOME")
        val discovery = discoveryTitles.indices.map { i ->
            CandidateScore(
                track = TrackEntity(id = "disc_$i", title = discoveryTitles[i], artist = discoveryArtists[i]),
                finalScore = 0.80f - (i * 0.01f),
                sessionMatch = 0.7f,
                tasteMatch = 0.5f,
                completionProbability = 0.7f,
                replayProbability = 0.4f,
                discoveryPotential = 0.95f,
                freshness = 0.95f,
                trendMomentum = 0.8f,
                skipProbability = 0.15f,
                isDiscovery = true
            )
        }

        val queue = queueSequencer.sequenceQueue(familiar + discovery, seedTrack = null, limit = 15)

        assertEquals(15, queue.size)
        // 0-indexed: index 4, 9, 14 must be discovery tracks
        assertTrue("Track at index 4 must be a discovery track", queue[4].id.startsWith("disc_"))
        assertTrue("Track at index 9 must be a discovery track", queue[9].id.startsWith("disc_"))
        assertTrue("Track at index 14 must be a discovery track", queue[14].id.startsWith("disc_"))
    }

    // =========================================================================
    // 6. AdaptiveSessionManager Real-Time Feedback Loops Tests
    // =========================================================================

    @Test
    fun adaptiveSession_threeSkips_triggersThreeSkipsAdaptation() {
        val t1 = TrackEntity(id = "s1", title = "Skip 1", artist = "Artist X")
        val t2 = TrackEntity(id = "s2", title = "Skip 2", artist = "Artist Y")
        val t3 = TrackEntity(id = "s3", title = "Skip 3", artist = "Artist Z")

        val trig1 = sessionManager.onTrackSkipped(t1, 5000L)
        val trig2 = sessionManager.onTrackSkipped(t2, 4000L)
        val trig3 = sessionManager.onTrackSkipped(t3, 3000L)

        assertEquals(null, trig1)
        assertEquals(null, trig2)
        assertEquals(SessionAdaptationTrigger.THREE_SKIPS, trig3)

        val profile = sessionManager.getSessionProfile()
        assertTrue("Exploration temperature should increase on skips", profile.explorationTemperature > 0.20f)
        assertTrue("Artist weight should be penalized", (profile.sessionArtistWeights["artist x"] ?: 0f) < 0f)
    }

    @Test
    fun adaptiveSession_twoReplays_triggersTwoReplaysAdaptation() {
        val t1 = TrackEntity(id = "r1", title = "Replay Song", artist = "Replay Artist")

        sessionManager.onTrackStarted(t1)
        val trig1 = sessionManager.onTrackReplayed(t1)
        val trig2 = sessionManager.onTrackReplayed(t1)

        assertEquals(null, trig1)
        assertEquals(SessionAdaptationTrigger.TWO_REPLAYS, trig2)

        val profile = sessionManager.getSessionProfile()
        assertTrue("Artist affinity should be strongly boosted", (profile.sessionArtistWeights["replay artist"] ?: 0f) > 5.0f)
    }

    @Test
    fun adaptiveSession_majorGenreSwitch_triggersMajorGenreSwitchAdaptation() {
        val punjabiTrack = TrackEntity(id = "pb1", title = "295", artist = "Sidhu Moose Wala")
        val gymTrack = TrackEntity(id = "gym1", title = "Murder In My Mind Phonk Gym", artist = "KORDHELL")

        val trig1 = sessionManager.onTrackStarted(punjabiTrack)
        val trig2 = sessionManager.onTrackStarted(gymTrack)

        // First transition sets the baseline vibe, second transition switches from Punjabi to Gym
        assertEquals(null, trig1)
        assertEquals(SessionAdaptationTrigger.MAJOR_GENRE_SWITCH, trig2)
    }

    @Test
    fun adaptiveSession_likeEvent_triggersLikeEventAndBoostsArtist() {
        val track = TrackEntity(id = "l1", title = "Loved Song", artist = "Loved Artist")

        val trigger = sessionManager.onTrackLiked(track)

        assertEquals(SessionAdaptationTrigger.LIKE_EVENT, trigger)
        val profile = sessionManager.getSessionProfile()
        val artistWeight = profile.sessionArtistWeights["loved artist"] ?: 0f
        assertEquals(6.0f, artistWeight, 0.001f)
    }

    @Test
    fun adaptiveSession_completion_coolsDownTemperature() {
        val track = TrackEntity(id = "c1", title = "Complete Song", artist = "Complete Artist")
        sessionManager.onTrackSkipped(track, 1000L) // elevate temperature
        val tempBefore = sessionManager.getSessionProfile().explorationTemperature

        sessionManager.onTrackCompleted(track)
        val tempAfter = sessionManager.getSessionProfile().explorationTemperature

        assertTrue(tempAfter < tempBefore)
    }

    // =========================================================================
    // 7. Advanced Modernization: Circadian, Cold-Start & Elastic Flow Tests
    // =========================================================================

    @Test
    fun circadianEnergy_targetMatchesTimeOfDayCurve() {
        assertEquals(0.35f, RankingScoringEngine.getCircadianTargetEnergy(2), 0.001f) // 2 AM late night
        assertEquals(0.35f, RankingScoringEngine.getCircadianTargetEnergy(23), 0.001f) // 11 PM late night
        assertEquals(0.50f, RankingScoringEngine.getCircadianTargetEnergy(7), 0.001f) // 7 AM morning
        assertEquals(0.65f, RankingScoringEngine.getCircadianTargetEnergy(14), 0.001f) // 2 PM midday
        assertEquals(0.80f, RankingScoringEngine.getCircadianTargetEnergy(19), 0.001f) // 7 PM workout/evening
        assertEquals(0.52f, RankingScoringEngine.getCircadianTargetEnergy(22), 0.001f) // 10 PM wind down
    }

    @Test
    fun rankingScoring_newUserColdStart_anchorsTasteToSeedAndProvidesPositiveScores() = runBlocking {
        val seed = TrackEntity(id = "seed_arijit", title = "Kesariya", artist = "Arijit Singh")
        sessionManager.onTrackStarted(seed)
        val sessionProfile = sessionManager.getSessionProfile()

        // Brand new user with zero listening history or saved vault tracks
        val emptyLongTermProfile = LongTermProfile()
        assertTrue("Profile must identify as new user", emptyLongTermProfile.isNewUser)

        val candidateSameGenre = TrackEntity(id = "c_same", title = "Apna Bana Le", artist = "Arijit Singh", thumbnailUrl = "https://example.com/art.jpg")
        val candidateDifferentGenre = TrackEntity(id = "c_diff", title = "Animals Dance EDM Club", artist = "Martin Garrix")

        val ranked = rankingScoringEngine.scoreAndRankCandidates(
            candidates = listOf(candidateDifferentGenre, candidateSameGenre),
            sessionProfile = sessionProfile,
            longTermProfile = emptyLongTermProfile,
            recentHistory = emptyList()
        )

        val sameScore = ranked.first { it.track.id == candidateSameGenre.id }
        assertTrue("New user cold start should have positive taste match (> 0.5)", sameScore.tasteMatch > 0.50f)
        assertFalse("Trending same-genre candidate for new user should be placed in familiar core, not discovery", sameScore.isDiscovery)
        assertTrue("Seed-aligned candidate must outrank disparate candidate", ranked.first().track.id == candidateSameGenre.id)
    }

    @Test
    fun sequencer_enforcesMaxThreeTotalTracksBySameArtistInQueue() {
        val drakeTitles = listOf("God's Plan", "Hotline Bling", "In My Feelings", "One Dance", "Nonstop", "Passionfruit")
        val drakeTracks = drakeTitles.indices.map { i ->
            TrackEntity(id = "drake_$i", title = drakeTitles[i], artist = "Drake", durationSeconds = 200L + i * 10L)
        }
        val otherTitles = listOf(
            "Starboy", "Believer", "Lucid Dreams", "Circles", "Sunflower",
            "Industry Baby", "Blinding Lights", "Without Me", "Stay", "Peaches",
            "Levitating", "Save Your Tears", "Counting Stars", "Happier", "Closer"
        )
        val otherArtists = listOf(
            "The Weeknd", "Imagine Dragons", "Juice WRLD", "Post Malone", "Swae Lee",
            "Lil Nas X", "Kavinsky", "Halsey", "The Kid LAROI", "Justin Bieber",
            "Dua Lipa", "Metro Boomin", "OneRepublic", "Marshmello", "The Chainsmokers"
        )
        val otherTracks = otherTitles.indices.map { i ->
            TrackEntity(id = "other_$i", title = otherTitles[i], artist = otherArtists[i], durationSeconds = 180L + i * 10L)
        }

        val allCandidates = (drakeTracks + otherTracks).map { track ->
            CandidateScore(
                track = track,
                sessionMatch = if (track.artist == "Drake") 0.95f else 0.85f,
                tasteMatch = 0.85f,
                completionProbability = 0.80f,
                replayProbability = 0.70f,
                discoveryPotential = 0.10f,
                freshness = 0.90f,
                trendMomentum = 0.80f,
                skipProbability = 0.10f,
                finalScore = if (track.artist == "Drake") 0.92f else 0.80f,
                isDiscovery = false
            )
        }

        val queue = queueSequencer.sequenceQueue(allCandidates, seedTrack = null, limit = 15)

        assertEquals(15, queue.size)
        val drakeCount = queue.count { it.cleanArtistName().equals("Drake", ignoreCase = true) }
        assertTrue("Queue must not contain more than 3 tracks by the same artist across 15 tracks to prevent fatigue, but had $drakeCount", drakeCount <= 3)
    }

    @Test
    fun sequencer_elasticDiscovery_adaptsCadenceBasedOnSkipsAndReplays() {
        val familiarTitles = listOf(
            "Kesariya", "Tum Hi Ho", "Raataan Lambiyan", "Apna Bana Le", "Channa Mereya",
            "Tera Hone Laga", "Zara Sa", "Deva Deva", "Kal Ho Naa Ho", "Agar Tum Saath",
            "Pehla Nasha", "Tujh Mein Rab", "Jeena Jeena", "Sun Saathiya", "Samjhawan",
            "Gerua", "Zaalima", "Hawayein", "Shayad", "Mast Magan"
        )
        val familiarArtists = listOf(
            "Arijit Singh", "Mithoon", "Jubin Nautiyal", "Sachin-Jigar", "Pritam",
            "Atif Aslam", "KK", "Amit Trivedi", "Sonu Nigam", "Alka Yagnik",
            "Udit Narayan", "Roop Kumar", "Mohit Chauhan", "Divya Kumar", "Shreya Ghoshal",
            "Badshah", "Diljit Dosanjh", "Karan Aujla", "AP Dhillon", "Sidhu Moose Wala"
        )
        val familiar = familiarTitles.indices.map { i ->
            CandidateScore(
                track = TrackEntity(id = "fam_$i", title = familiarTitles[i], artist = familiarArtists[i], durationSeconds = 210L + i * 5L),
                sessionMatch = 0.9f, tasteMatch = 0.9f, completionProbability = 0.8f,
                replayProbability = 0.7f, discoveryPotential = 0.1f, freshness = 0.9f,
                trendMomentum = 0.8f, skipProbability = 0.1f, finalScore = 0.9f - (i * 0.01f),
                isDiscovery = false
            )
        }
        val discoveryTitles = listOf(
            "Blinding Lights", "Starboy", "Sunset", "Nightcall", "Resonance",
            "Midnight City", "Days of Thunder", "After Hours", "Save Your Tears", "Stargazing"
        )
        val discoveryArtists = listOf(
            "The Weeknd", "Daft Punk", "The Midnight", "Kavinsky", "HOME",
            "M83", "Gunship", "Carpenter Brut", "Trevor Something", "Travis Scott"
        )
        val discovery = discoveryTitles.indices.map { i ->
            CandidateScore(
                track = TrackEntity(id = "disc_$i", title = discoveryTitles[i], artist = discoveryArtists[i], durationSeconds = 220L + i * 5L),
                sessionMatch = 0.7f, tasteMatch = 0.5f, completionProbability = 0.7f,
                replayProbability = 0.4f, discoveryPotential = 0.95f, freshness = 0.95f,
                trendMomentum = 0.8f, skipProbability = 0.15f, finalScore = 0.8f - (i * 0.01f),
                isDiscovery = true
            )
        }

        // 1. Skip streak (consecutiveSkips >= 2) -> discovery throttle to every 9th track (index 8)
        val skipSession = ShortTermSessionProfile(consecutiveSkips = 2)
        val skipQueue = queueSequencer.sequenceQueue(familiar + discovery, seedTrack = null, limit = 15, sessionProfile = skipSession)
        assertEquals(15, skipQueue.size)
        assertTrue("Index 8 should be discovery under skip throttle", skipQueue[8].id.startsWith("disc_"))
        assertFalse("Index 4 should NOT be discovery under skip throttle", skipQueue[4].id.startsWith("disc_"))

        // 2. Replay flow (consecutiveReplays >= 1) -> discovery accelerated to every 3rd track (index 2)
        val flowSession = ShortTermSessionProfile(consecutiveReplays = 1)
        val flowQueue = queueSequencer.sequenceQueue(familiar + discovery, seedTrack = null, limit = 15, sessionProfile = flowSession)
        assertEquals(15, flowQueue.size)
        assertTrue("Index 2 should be discovery in flow mode", flowQueue[2].id.startsWith("disc_"))

        // 3. New user mode -> conservative discovery every 7th track (index 6) to build initial trust
        val newUserQueue = queueSequencer.sequenceQueue(familiar + discovery, seedTrack = null, limit = 15, isNewUser = true)
        assertEquals(15, newUserQueue.size)
        assertTrue("Index 6 should be discovery for new user", newUserQueue[6].id.startsWith("disc_"))
        assertFalse("Index 4 should NOT be discovery for new user", newUserQueue[4].id.startsWith("disc_"))
    }

    @Test
    fun sequencer_smoothEnergyContinuity_prefersGradualEnergyTransitions() {
        val seed = TrackEntity(id = "seed", title = "Calm Acoustic Morning", artist = "Soft Singer") // ~0.35 energy
        val jarringTrack = TrackEntity(id = "jarring", title = "Hard Phonk Gym Rave Bass", artist = "Bass Artist") // ~0.90+ energy
        val bridgeTrack = TrackEntity(id = "bridge", title = "Melodic Slow Pop Rhythm", artist = "Pop Singer") // ~0.50 energy

        val candidates = listOf(
            CandidateScore(
                track = jarringTrack,
                sessionMatch = 0.95f, tasteMatch = 0.9f, completionProbability = 0.8f,
                replayProbability = 0.7f, discoveryPotential = 0.1f, freshness = 0.9f,
                trendMomentum = 0.8f, skipProbability = 0.1f, finalScore = 0.95f,
                isDiscovery = false
            ),
            CandidateScore(
                track = bridgeTrack,
                sessionMatch = 0.85f, tasteMatch = 0.85f, completionProbability = 0.8f,
                replayProbability = 0.7f, discoveryPotential = 0.1f, freshness = 0.9f,
                trendMomentum = 0.8f, skipProbability = 0.1f, finalScore = 0.88f,
                isDiscovery = false
            )
        )

        val queue = queueSequencer.sequenceQueue(candidates, seedTrack = seed, limit = 2)

        assertEquals("Bridge track with gradual energy delta must be sequenced before jarring jump", "bridge", queue.first().id)
    }

    // =========================================================================
    // 7. Native Graph First & Genre Coherence Tests (Pyaar Hua Ikraar Hua & Tum Utho Siya Sringar Karo)
    // =========================================================================

    @Test
    fun isSongOnly_rejectsTvAndFilmClips() {
        val sceneTrack = TrackEntity(
            id = "netflix_clip",
            title = "The Full Dustin and Suzie NeverEnding Story Scene | Stranger Things | Netflix",
            artist = "Still Watching Netflix",
            durationSeconds = 180L
        )
        assertFalse("TV show clips, movie scenes, and Netflix promos must be rejected by isSongOnly", sceneTrack.isSongOnly())
    }

    @Test
    fun vibeClassifier_classifiesTumUthoSiyaSringarKaro_asDevotional() {
        val track = TrackEntity(
            id = "siya_1",
            title = "Tum Utho Siya Sringar Karo",
            artist = "Maithili Thakur",
            durationSeconds = 240L
        )
        val vibe = vibeClassifier.classifyTrackVibe(track)
        assertEquals(SessionVibeMode.DEVOTIONAL, vibe)
    }

    @Test
    fun vibeClassifier_classifiesPyaarHuaIkraarHua_asRomantic() {
        val track = TrackEntity(
            id = "pyaar_1",
            title = "Pyaar Hua Ikraar Hua",
            artist = "Manna Dey, Lata Mangeshkar",
            durationSeconds = 260L
        )
        val vibe = vibeClassifier.classifyTrackVibe(track)
        assertEquals(SessionVibeMode.ROMANTIC, vibe)
    }

    @Test
    fun nativeGraph_pyaarHuaIkraarHua_sequencesGoldenEraClassicsAndRejectsPollution() = runBlocking {
        val seedTrack = TrackEntity(
            id = "pyaar_seed",
            title = "Pyaar Hua Ikraar Hua",
            artist = "Manna Dey, Lata Mangeshkar",
            durationSeconds = 260L
        )

        // Native watch-next tracks returned by YouTube / JioSaavn
        val nativeTracks = listOf(
            TrackEntity("nat_1", "Yeh Raat Bheegi Bheegi", "Manna Dey, Lata Mangeshkar", 270L, "http://thumb"),
            TrackEntity("nat_2", "Mera Joota Hai Japani", "Mukesh", 260L, "http://thumb"),
            TrackEntity("nat_3", "Ajeeb Dastan Hai Yeh", "Lata Mangeshkar", 310L, "http://thumb"),
            TrackEntity("nat_4", "Awaara Hoon", "Mukesh", 195L, "http://thumb"),
            TrackEntity("nat_5", "Ramaiya Vastavaiya", "Lata Mangeshkar, Mohammed Rafi, Mukesh", 350L, "http://thumb"),
            TrackEntity("nat_6", "Mud Mud Ke Na Dekh", "Asha Bhosle, Manna Dey", 345L, "http://thumb"),
            TrackEntity("nat_7", "Pyar Deewana Hota Hai", "Kishore Kumar", 280L, "http://thumb"),
            TrackEntity("nat_8", "Pal Pal Dil Ke Paas", "Kishore Kumar", 310L, "http://thumb"),
            TrackEntity("nat_9", "Lag Ja Gale", "Lata Mangeshkar", 255L, "http://thumb"),
            TrackEntity("nat_10", "Chaudhvin Ka Chand Ho", "Mohammed Rafi", 220L, "http://thumb"),
            TrackEntity("nat_11", "Dum Bhar Jo Udhar Munh Phere", "Mukesh, Lata Mangeshkar", 260L, "http://thumb"),
            TrackEntity("nat_12", "Eena Meena Deeka", "Kishore Kumar", 210L, "http://thumb")
        )
        val nativeIds = nativeTracks.map { it.id }.toSet()

        // Cross-genre polluters (soundtracks, EDM, trap from unrelated history)
        val polluters = listOf(
            TrackEntity("pollute_1", "Alan Silvestri - Portals (Avengers Endgame)", "MarvelMusicVEVO", 220L),
            TrackEntity("pollute_2", "Animals", "Martin Garrix", 190L),
            TrackEntity("pollute_3", "SICKO MODE", "Travis Scott", 312L)
        )

        val allCandidates = nativeTracks + polluters

        val sessionProfile = ShortTermSessionProfile(
            currentVibe = SessionVibeMode.ROMANTIC,
            currentEnergyLevel = 0.50f,
            sessionTracks = listOf(seedTrack)
        )
        val longTermProfile = LongTermProfile(
            topArtists = listOf("Martin Garrix", "Travis Scott", "Alan Silvestri"),
            artistScores = mapOf("martin garrix" to 10f, "travis scott" to 8f, "alan silvestri" to 7f),
            topGenres = listOf("edm", "hip hop", "soundtrack")
        )

        val scored = rankingScoringEngine.scoreAndRankCandidates(
            candidates = allCandidates,
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = emptyList(),
            forceFresh = false,
            nativeRelatedIds = nativeIds
        )

        val queue = queueSequencer.sequenceQueue(
            scoredCandidates = scored,
            seedTrack = seedTrack,
            limit = 10,
            sessionProfile = sessionProfile
        )

        assertEquals("Queue should return requested 10 items", 10, queue.size)

        // Verify all 10 tracks are from the native golden era cluster
        val returnedIds = queue.map { it.id }.toSet()
        for (polluter in polluters) {
            assertFalse("Unrelated polluter ${polluter.title} must NOT be in queue", returnedIds.contains(polluter.id))
        }

        // Verify top tracks are native watch-next tracks
        assertTrue("Track 1 must be from native watch-next graph", nativeIds.contains(queue[0].id))
        assertTrue("Track 2 must be from native watch-next graph", nativeIds.contains(queue[1].id))
        assertTrue("Track 3 must be from native watch-next graph", nativeIds.contains(queue[2].id))
    }
}

// =============================================================================
// Lightweight Test Doubles & Mocks
// =============================================================================

class FakePlaybackTelemetryDao : PlaybackTelemetryDao {
    private val records = mutableMapOf<String, PlaybackTelemetryEntity>()
    private val skippedIds = mutableSetOf<String>()

    override suspend fun insertOrUpdate(telemetry: PlaybackTelemetryEntity) {
        records[telemetry.trackId] = telemetry
    }

    override suspend fun getTelemetry(trackId: String): PlaybackTelemetryEntity? = records[trackId]

    override suspend fun getRecentTelemetry(limit: Int): List<PlaybackTelemetryEntity> =
        records.values.take(limit)

    override suspend fun getTopEngagedTracks(limit: Int): List<PlaybackTelemetryEntity> =
        records.values.take(limit)

    override suspend fun getFrequentlySkippedTrackIds(): List<String> = skippedIds.toList()

    override suspend fun recordPlayStart(trackId: String, title: String, artist: String, timestamp: Long) {
        val existing = records[trackId]
        records[trackId] = existing?.copy(
            playCount = existing.playCount + 1,
            lastPlayedTimestamp = timestamp
        ) ?: PlaybackTelemetryEntity(
            trackId = trackId,
            title = title,
            artist = artist,
            playCount = 1,
            lastPlayedTimestamp = timestamp
        )
    }

    override suspend fun updateTrackEngagement(
        trackId: String,
        listenTimeMs: Long,
        completionRate: Float,
        completed: Int,
        skipped: Int,
        timestamp: Long
    ) {
        val existing = records[trackId] ?: PlaybackTelemetryEntity(
            trackId = trackId,
            title = "",
            artist = ""
        )
        records[trackId] = existing.copy(
            completionCount = existing.completionCount + completed,
            skipCount = existing.skipCount + skipped,
            totalListenTimeMs = existing.totalListenTimeMs + listenTimeMs,
            lastCompletionRate = completionRate,
            lastPlayedTimestamp = timestamp
        )
        if (existing.skipCount + skipped >= 2 && existing.completionCount + completed == 0) {
            skippedIds.add(trackId)
        }
    }

    override suspend fun updateLikedStatus(trackId: String, isLiked: Boolean) {
        val existing = records[trackId]
        if (existing != null) {
            records[trackId] = existing.copy(isLiked = isLiked)
        }
    }
}

class FakeStreamRepository : StreamRepository {
    override fun searchTracks(query: String): Flow<Result<List<TrackEntity>>> =
        flowOf(Result.success(emptyList()))

    override fun resolveAudioStream(
        trackIdOrUrl: String,
        quality: String?,
        forceFresh: Boolean
    ): Flow<Result<AudioStreamResult>> =
        flowOf(Result.failure(UnsupportedOperationException("Fake")))

    override fun extractVideoId(input: String): String = input
}

class FakeSaavnMediaEngine : SaavnMediaEngine(OkHttpClient()) {
    override fun searchTracks(query: String): List<TrackEntity> = emptyList()
    override fun getSongRecommendations(trackId: String): List<TrackEntity> = emptyList()
}

class FakeNewPipeYouTubeEngine : NewPipeYouTubeEngine(OkHttpClient()) {
    override fun getRelatedTracks(videoId: String): List<TrackEntity> = emptyList()
}

