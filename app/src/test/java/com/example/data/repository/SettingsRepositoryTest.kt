package com.example.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.local.preferences.SettingsRepositoryImpl
import com.example.domain.model.InspectionPolicyConfig
import com.example.ui.theme.AppThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `theme mode and screening policy config persist and reset cleanly`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempFolder.root, "test_settings.preferences_pb") }
        )
        val settings = SettingsRepositoryImpl(dataStore)

        assertEquals(AppThemeMode.SYSTEM, settings.themeMode.first())
        assertEquals(InspectionPolicyConfig(), settings.policyConfig.first())

        settings.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, settings.themeMode.first())

        val updatedPolicy = InspectionPolicyConfig(
            profileName = "Custom Bench Profile",
            rejectOnCritical = false,
            reviewOnMedium = false,
            minImageWidth = 512,
            minImageHeight = 512
        )
        settings.updatePolicyConfig(updatedPolicy)
        val readBack = settings.policyConfig.first()
        assertEquals("Custom Bench Profile", readBack.profileName)
        assertFalse(readBack.rejectOnCritical)
        assertEquals(512, readBack.minImageWidth)

        settings.resetPolicyDefaults()
        assertEquals(InspectionPolicyConfig(), settings.policyConfig.first())
    }
}
