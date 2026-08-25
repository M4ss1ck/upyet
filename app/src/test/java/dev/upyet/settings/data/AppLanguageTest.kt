package dev.upyet.settings.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {
    @Test fun systemIsTheAbsenceOfAnOverride() {
        assertThat(AppLanguage.SYSTEM.tag).isNull()
        assertThat(AppLanguage.fromTag(null)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTag("")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test fun regionalTagsResolveToTheirLanguage() {
        assertThat(AppLanguage.fromTag("en")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.fromTag("en-US")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.fromTag("es")).isEqualTo(AppLanguage.SPANISH)
        assertThat(AppLanguage.fromTag("es-419")).isEqualTo(AppLanguage.SPANISH)
    }

    @Test fun anUntranslatedLanguageFallsBackToSystem() {
        assertThat(AppLanguage.fromTag("fr-FR")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test fun everyOptionRoundTripsThroughItsTag() {
        AppLanguage.entries.forEach { language ->
            assertThat(AppLanguage.fromTag(language.tag)).isEqualTo(language)
        }
    }
}
