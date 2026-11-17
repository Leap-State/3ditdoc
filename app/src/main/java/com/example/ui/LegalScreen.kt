package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LegalTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    initialTab: LegalTab,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.legal_screen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("legal_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        LegalTab.TERMS to R.string.legal_tab_terms,
                        LegalTab.PRIVACY to R.string.legal_tab_privacy,
                        LegalTab.ABOUT to R.string.legal_tab_about
                    )
                    tabs.forEachIndexed { idx, (tab, labelRes) ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                            modifier = Modifier.testTag("legal_tab_${tab.name.lowercase()}")
                        ) {
                            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        LegalTab.TERMS -> TermsOfUseContent()
                        LegalTab.PRIVACY -> PrivacyPolicyContent()
                        LegalTab.ABOUT -> AboutAppContent()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TermsOfUseContent() {
    LegalSectionCard(
        title = stringResource(R.string.terms_s1_title),
        body = stringResource(R.string.terms_s1_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.terms_s2_title),
        body = stringResource(R.string.terms_s2_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.terms_s3_title),
        body = stringResource(R.string.terms_s3_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.terms_s4_title),
        body = stringResource(R.string.terms_s4_body)
    )
}

@Composable
private fun PrivacyPolicyContent() {
    LegalSectionCard(
        title = stringResource(R.string.privacy_s1_title),
        body = stringResource(R.string.privacy_s1_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.privacy_s2_title),
        body = stringResource(R.string.privacy_s2_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.privacy_s3_title),
        body = stringResource(R.string.privacy_s3_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.privacy_s4_title),
        body = stringResource(R.string.privacy_s4_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.tutorial_step_4_title),
        body = stringResource(R.string.share_offline_notice)
    )
}

@Composable
private fun AboutAppContent() {
    DeveloperSupportCard(
        compact = false,
        tagPrefix = "about"
    )
    LegalSectionCard(
        title = stringResource(R.string.about_s1_title),
        body = stringResource(R.string.about_s1_body)
    )
    LegalSectionCard(
        title = stringResource(R.string.about_s2_title),
        body = stringResource(R.string.about_s2_body)
    )
}

@Composable
private fun LegalSectionCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
