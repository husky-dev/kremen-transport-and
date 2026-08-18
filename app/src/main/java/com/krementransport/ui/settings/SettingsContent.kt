package com.krementransport.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.krementransport.R
import com.krementransport.data.prefs.AppearancePreference
import com.krementransport.data.prefs.LanguagePreference

/**
 * The config modal: appearance and language, the only two things this app has to configure.
 *
 * Language is a real in-app picker rather than a deep link into system settings — Android lets
 * an app set its own locale (natively from API 33, backported by AppCompat below), which is the
 * one place this app deliberately does more than its iOS sibling.
 */
@Composable
fun SettingsContent(
    appearance: AppearancePreference,
    language: LanguagePreference,
    onAppearanceChange: (AppearancePreference) -> Unit,
    onLanguageChange: (LanguagePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_title),
            modifier = Modifier.padding(start = 24.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        SectionHeader(stringResource(R.string.settings_appearance_section))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            val options = AppearancePreference.entries
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = appearance == option,
                    onClick = { onAppearanceChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringResource(option.labelRes()))
                }
            }
        }

        SectionHeader(stringResource(R.string.settings_language_section))

        Column(Modifier.selectableGroup()) {
            for (option in LanguagePreference.entries) {
                ListItem(
                    headlineContent = { Text(stringResource(option.labelRes())) },
                    leadingContent = {
                        RadioButton(selected = language == option, onClick = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .selectable(
                            selected = language == option,
                            role = Role.RadioButton,
                            onClick = { onLanguageChange(option) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun AppearancePreference.labelRes(): Int = when (this) {
    AppearancePreference.System -> R.string.settings_appearance_system
    AppearancePreference.Light -> R.string.settings_appearance_light
    AppearancePreference.Dark -> R.string.settings_appearance_dark
}

private fun LanguagePreference.labelRes(): Int = when (this) {
    LanguagePreference.System -> R.string.settings_language_system
    LanguagePreference.Ukrainian -> R.string.settings_language_uk
    LanguagePreference.English -> R.string.settings_language_en
}
