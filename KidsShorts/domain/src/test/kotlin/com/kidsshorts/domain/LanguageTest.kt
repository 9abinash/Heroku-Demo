package com.kidsshorts.domain

import com.kidsshorts.domain.Fx.approve
import com.kidsshorts.domain.Fx.block
import com.kidsshorts.domain.Fx.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {
    @Test fun normalize_handlesCaseSeparatorsAndSubtags() {
        assertEquals("hi", LanguageNormalizer.normalize("hi"))
        assertEquals("hi", LanguageNormalizer.normalize(" HI-in "))
        assertEquals("en", LanguageNormalizer.normalize("en_US"))
        assertEquals("zh", LanguageNormalizer.normalize("zh-Hans-CN"))
        assertEquals("hi", LanguageNormalizer.normalize("hi-Latn"))
    }

    @Test fun normalize_mapsOnlyExactSynonyms() {
        assertEquals("or", LanguageNormalizer.normalize("ori"))
        assertEquals("or", LanguageNormalizer.normalize("ory"))
        assertEquals("hi", LanguageNormalizer.normalize("hin"))
        assertEquals("he", LanguageNormalizer.normalize("iw"))
        // Distinct languages stay distinct:
        assertEquals("ur", LanguageNormalizer.normalize("ur"))
        assertEquals("bn", LanguageNormalizer.normalize("bn"))
    }

    @Test fun normalize_rejectsInvalidAndUndetermined() {
        listOf(null, "", " ", "und", "zxx", "mul", "mis", "h", "hindi-language-x", "hi--in", "12", "hi_!!").forEach {
            assertNull("input=$it", LanguageNormalizer.normalize(it))
        }
    }

    @Test fun resolver_audioOnly_isVerified() {
        val r = LanguageResolver.resolve(video(audio = "ta", meta = null), null)
        assertEquals(LanguageResolution(LanguageStatus.VERIFIED_BY_METADATA, "ta"), r)
    }

    @Test fun resolver_audioAndMetaAgree_isVerified() {
        val r = LanguageResolver.resolve(video(audio = "te-IN", meta = "te"), null)
        assertEquals(LanguageResolution(LanguageStatus.VERIFIED_BY_METADATA, "te"), r)
    }

    @Test fun resolver_audioAndMetaDisagree_isConflicting() {
        val r = LanguageResolver.resolve(video(audio = "hi", meta = "en"), null)
        assertEquals(LanguageStatus.CONFLICTING, r.status)
        assertNull(r.languageCode)
    }

    @Test fun resolver_metaOnly_isUnknown() {
        assertEquals(LanguageStatus.UNKNOWN, LanguageResolver.resolve(video(audio = null, meta = "hi"), null).status)
    }

    @Test fun resolver_invalidAudioWithValidMeta_isUnknown_notVerified() {
        assertEquals(LanguageStatus.UNKNOWN, LanguageResolver.resolve(video(audio = "und", meta = "hi"), null).status)
    }

    @Test fun resolver_parentConfirmation_outranksMetadata() {
        val r = LanguageResolver.resolve(video(audio = "en"), approve(lang = "hi"))
        assertEquals(LanguageResolution(LanguageStatus.PARENT_CONFIRMED, "hi"), r)
    }

    @Test fun resolver_blockedDecisionCarriesNoLanguageConfirmation() {
        val d = VideoDecision("v1", ParentDecision.BLOCKED, "hi", 1L)
        assertEquals(LanguageStatus.UNKNOWN, LanguageResolver.resolve(video(audio = null), d).status)
        assertEquals(LanguageStatus.UNKNOWN, LanguageResolver.resolve(video(audio = null), block()).status)
    }

    @Test fun catalog_containsTheRequiredIndianLanguages() {
        val required = listOf("en", "hi", "bn", "or", "ta", "te", "kn", "ml", "mr", "gu", "pa", "ur", "as", "sa")
        assertEquals(required, LanguageCatalog.india.map { it.code })
        required.forEach { assertNotNull(LanguageCatalog.find(it)) }
    }

    @Test fun catalog_codesAreUniqueAndNormalised() {
        val codes = LanguageCatalog.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertEquals(it, LanguageNormalizer.normalize(it)) }
    }

    @Test fun catalog_labelNeverGuesses() {
        assertEquals("Odia (or)", LanguageCatalog.label("ory"))
        assertEquals("Unknown", LanguageCatalog.label(null))
        assertEquals("xx", LanguageCatalog.label("xx"))
    }
}
