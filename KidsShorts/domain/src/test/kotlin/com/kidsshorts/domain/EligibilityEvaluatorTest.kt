package com.kidsshorts.domain

import com.kidsshorts.domain.Fx.approve
import com.kidsshorts.domain.Fx.block
import com.kidsshorts.domain.Fx.channel
import com.kidsshorts.domain.Fx.channels
import com.kidsshorts.domain.Fx.decisions
import com.kidsshorts.domain.Fx.eval
import com.kidsshorts.domain.Fx.policy
import com.kidsshorts.domain.Fx.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EligibilityEvaluatorTest {

    // ---- the twelve scenarios required by the build prompt ----

    @Test fun approvedChannel_allowedLanguage_isEligible() {
        assertTrue(eval().isEligible())
    }

    @Test fun unapprovedChannel_allowedLanguage_isBlocked() {
        val r = eval(video = video(channelId = "UC_other"))
        assertFalse(r.isEligible())
        assertTrue(ExclusionReason.CHANNEL_NOT_APPROVED in r.reasons())
    }

    @Test fun approvedChannel_disallowedLanguage_isBlocked() {
        val r = eval(video = video(audio = "en"), policy = policy("hi"))
        assertEquals(listOf(ExclusionReason.LANGUAGE_NOT_ALLOWED), r.reasons())
    }

    @Test fun approvedChannel_unknownLanguage_isBlockedByDefault() {
        val r = eval(video = video(audio = null, meta = null))
        assertEquals(listOf(ExclusionReason.LANGUAGE_UNKNOWN), r.reasons())
    }

    @Test fun conflictingSignals_areBlockedPendingReview() {
        val r = eval(video = video(audio = "hi", meta = "en"))
        assertEquals(listOf(ExclusionReason.LANGUAGE_CONFLICT), r.reasons())
    }

    @Test fun parentBlockedVideo_isBlockedEvenWhenEverythingElsePasses() {
        val r = eval(decisions = decisions(block()))
        assertTrue(ExclusionReason.VIDEO_BLOCKED_BY_PARENT in r.reasons())
        assertFalse(r.isEligible())
    }

    @Test fun parentConfirmedAllowedLanguage_isEligible() {
        val v = video(audio = null)
        val r = eval(video = v, decisions = decisions(approve(lang = "hi")))
        assertTrue(r.isEligible())
        assertEquals(LanguageStatus.PARENT_CONFIRMED, (r as EligibilityResult.Eligible).language.status)
    }

    @Test fun parentConfirmedDisallowedLanguage_isBlocked() {
        val r = eval(video = video(audio = null), decisions = decisions(approve(lang = "en")), policy = policy("hi"))
        assertEquals(listOf(ExclusionReason.LANGUAGE_NOT_ALLOWED), r.reasons())
    }

    @Test fun channelDisabledAfterVideoWasCached_isBlockedImmediately() {
        val v = video()
        assertTrue(eval(video = v).isEligible())
        val r = eval(video = v, channels = channels(channel(enabled = false)))
        assertEquals(listOf(ExclusionReason.CHANNEL_DISABLED), r.reasons())
    }

    @Test fun languageRemovedFromAllowlist_removesAffectedVideosImmediately() {
        val videos = listOf(video("a", audio = "hi"), video("b", audio = "en"), video("c", audio = "hi"))
        fun eligibleIds(p: LanguagePolicy) = EligibilityEvaluator
            .evaluateAll(videos, channels(), p, emptyMap()).filterIsInstance<EligibilityResult.Eligible>().map { it.video.videoId }
        assertEquals(listOf("a", "b", "c"), eligibleIds(policy("hi", "en")))
        assertEquals(listOf("b"), eligibleIds(policy("en")))
    }

    @Test fun emptyAllowedLanguageList_meansNothingIsPlayable() {
        val videos = listOf(video("a", audio = "hi"), video("b", audio = null))
        val results = EligibilityEvaluator.evaluateAll(videos, channels(), LanguagePolicy(emptySet(), null, true), emptyMap())
        assertTrue(results.none { it.isEligible() })
    }

    @Test fun emptyAllowedLanguageList_evenWithAllowUnknownSwitchedOn_meansNothingIsPlayable() {
        val r = eval(video = video(audio = null), policy = LanguagePolicy(emptySet(), null, blockUnknownLanguage = false))
        assertFalse(r.isEligible())
        assertTrue(ExclusionReason.LANGUAGE_NOT_ALLOWED in r.reasons())
    }

    @Test fun emptyFeed_neverFallsBackToOtherChannelsOrLanguages() {
        // The only cached videos are in a disallowed language / unapproved channel.
        val videos = listOf(video("a", audio = "en"), video("b", channelId = "UC_other", audio = "hi"))
        val eligible = EligibilityEvaluator.evaluateAll(videos, channels(), policy("hi"), emptyMap())
            .filterIsInstance<EligibilityResult.Eligible>()
        assertTrue(eligible.isEmpty())
        assertTrue(FeedOrdering.order(eligible, preferredLanguageCode = "hi").isEmpty())
    }

    // ---- additional fail-closed behaviour ----

    @Test fun parentApprovalWithoutLanguageLabel_doesNotBypassLanguageRules() {
        val r = eval(video = video(audio = null), decisions = decisions(approve(lang = null)))
        assertEquals(listOf(ExclusionReason.LANGUAGE_UNKNOWN), r.reasons())
    }

    @Test fun parentApprovalWithInvalidLanguageLabel_isIgnored() {
        val r = eval(video = video(audio = null), decisions = decisions(approve(lang = "???")))
        assertEquals(listOf(ExclusionReason.LANGUAGE_UNKNOWN), r.reasons())
    }

    @Test fun parentApproval_resolvesConflict_butOnlyWithExplicitLanguage() {
        val v = video(audio = "hi", meta = "en")
        assertFalse(eval(video = v, decisions = decisions(approve(lang = null))).isEligible())
        assertTrue(eval(video = v, decisions = decisions(approve(lang = "hi"))).isEligible())
    }

    @Test fun manualApproval_isTiedToTheExactVideoId() {
        val approved = video("a", audio = null)
        val sibling = video("b", audio = null) // same channel, no decision
        val d = decisions(approve("a", "hi"))
        assertTrue(eval(video = approved, decisions = d).isEligible())
        assertFalse(eval(video = sibling, decisions = d).isEligible())
    }

    @Test fun allowUnknownSwitchedOn_admitsUnknown_butNeverConflict() {
        val p = policy("hi", blockUnknown = false)
        assertTrue(eval(video = video(audio = null), policy = p).isEligible())
        assertFalse(eval(video = video(audio = "hi", meta = "en"), policy = p).isEligible())
    }

    @Test fun bareDefaultLanguage_isNotEvidenceOfSpokenLanguage() {
        // Title/description language alone must never verify a language.
        val r = eval(video = video(audio = null, meta = "hi"))
        assertEquals(listOf(ExclusionReason.LANGUAGE_UNKNOWN), r.reasons())
    }

    @Test fun regionVariants_normaliseToSameLanguage() {
        assertTrue(eval(video = video(audio = "hi-IN")).isEligible())
        assertTrue(eval(video = video(audio = "HI_in", meta = "hi")).isEligible())
    }

    @Test fun similarLanguages_areNeverSubstituted() {
        // Urdu and Hindi are distinct entries; Odia must not match Bengali, etc.
        assertFalse(eval(video = video(audio = "ur"), policy = policy("hi")).isEligible())
        assertFalse(eval(video = video(audio = "bn"), policy = policy("or")).isEligible())
        assertFalse(eval(video = video(audio = "pa"), policy = policy("hi")).isEligible())
    }

    @Test fun undeterminedLanguageCodes_areTreatedAsUnknown() {
        listOf("und", "zxx", "mul", "", "  ", "x", "12", "hi!").forEach {
            val r = eval(video = video(audio = it))
            assertEquals("audio=$it", listOf(ExclusionReason.LANGUAGE_UNKNOWN), r.reasons())
        }
    }

    @Test fun unavailableOrNonEmbeddableOrUnverifiedStatus_areBlocked() {
        assertTrue(ExclusionReason.VIDEO_NOT_EMBEDDABLE in eval(video = video(embeddable = false)).reasons())
        assertTrue(ExclusionReason.VIDEO_UNAVAILABLE in eval(video = video(available = false)).reasons())
        assertTrue(ExclusionReason.VIDEO_UNAVAILABLE in eval(video = video(playerUnavailable = true)).reasons())
        assertTrue(ExclusionReason.STATUS_NOT_VERIFIED in eval(video = video(madeForKids = null)).reasons())
    }

    @Test fun madeForKidsTrueOrFalse_bothAllowedOnceStatusIsKnown() {
        assertTrue(eval(video = video(madeForKids = true)).isEligible())
        assertTrue(eval(video = video(madeForKids = false)).isEligible())
    }

    @Test fun shortsFormat_boundaries() {
        assertTrue(eval(video = video(duration = 1)).isEligible())
        assertTrue(eval(video = video(duration = 180)).isEligible())
        assertEquals(listOf(ExclusionReason.NOT_SHORTS_FORMAT), eval(video = video(duration = 181)).reasons())
        assertEquals(listOf(ExclusionReason.NOT_SHORTS_FORMAT), eval(video = video(duration = 0)).reasons())
    }

    @Test fun multipleFailures_areAllReported_inStablePriorityOrder() {
        val r = eval(video = video(channelId = "UC_x", audio = "en"), decisions = decisions(block()))
        assertEquals(
            listOf(ExclusionReason.CHANNEL_NOT_APPROVED, ExclusionReason.VIDEO_BLOCKED_BY_PARENT, ExclusionReason.LANGUAGE_NOT_ALLOWED),
            r.reasons(),
        )
    }

    @Test fun evaluation_isDeterministic() {
        val v = video(audio = "hi", meta = "en")
        val a = eval(video = v); val b = eval(video = v)
        assertEquals(a.reasons(), b.reasons())
    }

    @Test fun parentUndoOfBlock_makesVideoEligibleAgain() {
        val v = video()
        assertFalse(eval(video = v, decisions = decisions(block())).isEligible())
        assertTrue(eval(video = v, decisions = emptyMap()).isEligible())
    }

    // ---- feed ordering must never widen the feed ----

    @Test fun preferredLanguage_onlyReorders_neverAdds() {
        val videos = listOf(
            video("old_hi", audio = "hi", published = 1), video("new_en", audio = "en", published = 9),
            video("new_hi", audio = "hi", published = 5), video("x_bn", audio = "bn", published = 99),
        )
        val p = policy("hi", "en", preferred = "hi")
        val eligible = EligibilityEvaluator.evaluateAll(videos, channels(), p, emptyMap()).filterIsInstance<EligibilityResult.Eligible>()
        val ordered = FeedOrdering.order(eligible, p.preferredLanguageCode).map { it.video.videoId }
        assertEquals(listOf("new_hi", "old_hi", "new_en"), ordered)
        assertFalse("x_bn" in ordered)
    }

    @Test fun preferredLanguageNotInAllowlist_cannotBypassFilter() {
        val p = policy("hi", preferred = "en")
        val r = eval(video = video(audio = "en"), policy = p)
        assertFalse(r.isEligible())
    }
}
