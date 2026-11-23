package id.branchworld.game

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

/**
 * Runs a suspending simulation test on every target, including Kotlin/JS where blocking is impossible.
 * Long replay and soak fixtures are CPU-bound, so the wall-clock limit is generous.
 */
internal fun runSimulationTest(block: suspend CoroutineScope.() -> Unit): TestResult =
    runTest(timeout = 30.minutes) { block() }
