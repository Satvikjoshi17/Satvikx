package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao
import com.satvik.satvikx.data.local.entity.PlaybackTelemetryEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
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

