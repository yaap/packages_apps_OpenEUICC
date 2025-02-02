package im.angry.openeuicc.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class SettingsFragment: PreferenceFragmentCompat() {
    private lateinit var developerPref: PreferenceCategory

    // Hidden developer options switch
    private var numClicks = 0
    private var lastClickTimestamp = -1L
    private var lastToast: Toast? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_settings, rootKey)

        developerPref = findPreference("pref_developer")!!

        // Show / hide developer preference based on whether it is enabled
        lifecycleScope.launch {
            preferenceRepository.developerOptionsEnabledFlow
                .onEach { developerPref.isVisible = it }
                .collect()
        }

        findPreference<Preference>("pref_info_app_version")?.apply {
            summary = requireContext().selfAppVersion

            // Enable developer options when this is clicked for 7 times
            setOnPreferenceClickListener(::onAppVersionClicked)
        }

        findPreference<Preference>("pref_language")?.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@apply
            isVisible = true
            intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
        }

        findPreference<Preference>("pref_advanced_logs")?.apply {
            intent = Intent(requireContext(), LogsActivity::class.java)
        }

        findPreference<CheckBoxPreference>("pref_notifications_download")
            ?.bindBooleanFlow(preferenceRepository.notificationDownloadFlow, PreferenceKeys.NOTIFICATION_DOWNLOAD)

        findPreference<CheckBoxPreference>("pref_notifications_delete")
            ?.bindBooleanFlow(preferenceRepository.notificationDeleteFlow, PreferenceKeys.NOTIFICATION_DELETE)

        findPreference<CheckBoxPreference>("pref_notifications_switch")
            ?.bindBooleanFlow(preferenceRepository.notificationSwitchFlow, PreferenceKeys.NOTIFICATION_SWITCH)

        findPreference<CheckBoxPreference>("pref_advanced_disable_safeguard_removable_esim")
            ?.bindBooleanFlow(preferenceRepository.disableSafeguardFlow, PreferenceKeys.DISABLE_SAFEGUARD_REMOVABLE_ESIM)

        findPreference<CheckBoxPreference>("pref_advanced_verbose_logging")
            ?.bindBooleanFlow(preferenceRepository.verboseLoggingFlow, PreferenceKeys.VERBOSE_LOGGING)

        findPreference<CheckBoxPreference>("pref_developer_unfiltered_profile_list")
            ?.bindBooleanFlow(preferenceRepository.unfilteredProfileListFlow, PreferenceKeys.UNFILTERED_PROFILE_LIST)

        findPreference<CheckBoxPreference>("pref_ignore_tls_certificate")
            ?.bindBooleanFlow(preferenceRepository.ignoreTLSCertificateFlow, PreferenceKeys.IGNORE_TLS_CERTIFICATE)
    }

    override fun onStart() {
        super.onStart()
        setupRootViewInsets(requireView().requireViewById(R.id.recycler_view))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAppVersionClicked(pref: Preference): Boolean {
        if (developerPref.isVisible) return false
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp >= 1000) {
            numClicks = 1
        } else {
            numClicks++
        }
        lastClickTimestamp = now

        if (numClicks == 7) {
            lifecycleScope.launch {
                preferenceRepository.updatePreference(
                    PreferenceKeys.DEVELOPER_OPTIONS_ENABLED,
                    true
                )

                lastToast?.cancel()
                Toast.makeText(
                    requireContext(),
                    R.string.developer_options_enabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (numClicks > 1) {
            lastToast?.cancel()
            lastToast = Toast.makeText(
                requireContext(),
                getString(R.string.developer_options_steps, 7 - numClicks),
                Toast.LENGTH_SHORT
            )
            lastToast!!.show()
        }

        return true
    }

    private fun CheckBoxPreference.bindBooleanFlow(flow: Flow<Boolean>, key: Preferences.Key<Boolean>) {
        lifecycleScope.launch {
            flow.collect { isChecked = it }
        }

        setOnPreferenceChangeListener { _, newValue ->
            runBlocking {
                preferenceRepository.updatePreference(key, newValue as Boolean)
            }
            true
        }
    }

    protected fun mergePreferenceOverlay(overlayKey: String, targetKey: String) {
        val overlayCat = findPreference<PreferenceCategory>(overlayKey)!!
        val targetCat = findPreference<PreferenceCategory>(targetKey)!!

        val prefs = buildList {
            for (i in 0..<overlayCat.preferenceCount) {
                add(overlayCat.getPreference(i))
            }
        }

        prefs.forEach {
            overlayCat.removePreference(it)
            targetCat.addPreference(it)
        }

        overlayCat.parent?.removePreference(overlayCat)
    }
}