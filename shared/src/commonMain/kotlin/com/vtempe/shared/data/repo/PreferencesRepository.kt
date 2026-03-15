package com.vtempe.shared.data.repo

import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.russhwolf.settings.Settings

class SettingsPreferencesRepository(
    private val settings: Settings
) : PreferencesRepository {
    private val KEY_LANG = "prefs.languageTag"
    private val KEY_THEME = "prefs.theme" // light | dark | system
    private val KEY_UNITS = "prefs.units" // metric | imperial
    private val KEY_AI_MODEL = "prefs.aiModelMode" // paid | free

    override fun getLanguageTag(): String? = settings.getStringOrNull(KEY_LANG)
    override fun setLanguageTag(tag: String?) {
        if (tag == null) settings.remove(KEY_LANG) else settings.putString(KEY_LANG, tag)
    }

    override fun getTheme(): String? = settings.getStringOrNull(KEY_THEME)
    override fun setTheme(theme: String?) {
        if (theme == null) settings.remove(KEY_THEME) else settings.putString(KEY_THEME, theme)
    }

    override fun getUnits(): String? = settings.getStringOrNull(KEY_UNITS)
    override fun setUnits(units: String?) {
        if (units == null) settings.remove(KEY_UNITS) else settings.putString(KEY_UNITS, units)
    }

    override fun getAiModelMode(): AiModelMode =
        AiModelMode.fromWire(settings.getStringOrNull(KEY_AI_MODEL))

    override fun setAiModelMode(mode: AiModelMode) {
        settings.putString(KEY_AI_MODEL, mode.wireValue)
    }
}
