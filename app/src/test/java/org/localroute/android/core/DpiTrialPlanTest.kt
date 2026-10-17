package org.localroute.android.core

import org.junit.Assert.*
import org.junit.Test

class DpiTrialPlanTest {
    @Test fun customCommandDoesNotDuplicateABuiltInCandidate() {
        val candidates=AndroidStrategies.candidates(null,AndroidStrategies.BYEDPI_ANDROID_TCP_UDP_COMMAND)
        assertEquals(AndroidStrategies.all.size,candidates.size)
        assertEquals(candidates.size,candidates.map { it.nativeArguments }.toSet().size)
        assertEquals("custom-command",candidates.first().id)
    }
    @Test fun rejectionAdvancesToADifferentCandidateAndNeverWrapsSilently() {
        val ids=AndroidStrategies.all.map { it.id }
        assertTrue(ids.size>1)
        var plan=DpiTrialPlan();val seen=mutableSetOf<String>()
        repeat(ids.size) { plan=plan.next(ids);assertTrue(seen.add(requireNotNull(plan.candidate))) }
        val exhausted=plan.next(ids)
        assertNull(exhausted.candidate);assertEquals(ids.toSet(),exhausted.tried)
        assertNull(exhausted.next(ids).candidate)
    }
    @Test fun trialSurvivesRecreationAndConfirmationIsExplicit() {
        val ids=AndroidStrategies.all.map { it.id }
        val first=DpiTrialPlan().next(ids)
        val restored=DpiTrialPlan(first.tried.toSet(),first.candidate)
        assertNotEquals(restored.candidate,restored.next(ids).candidate)
        assertNotNull(restored.candidate)
        assertNull(restored.confirmed().candidate)
    }
    @Test fun rememberedStrategyIsFirstButDoesNotRepeatAfterRejection() {
        val saved=AndroidStrategies.all.last().id
        val ids=AndroidStrategies.ordered(saved).map { it.id }
        val trial=DpiTrialPlan().next(ids)
        assertEquals(saved,trial.candidate)
        assertNotEquals(saved,trial.next(ids).candidate)
    }
}
