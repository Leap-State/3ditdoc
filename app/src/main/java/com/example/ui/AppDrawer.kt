package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.LegalTab
import com.example.ui.theme.MonoOverlayFontFamily
import kotlinx.coroutines.delay

@Composable
fun AppNavigationDrawerSheet(
    currentRoute: String?,
    onOpenPdf: () -> Unit,
    onOpenSamplePdf: () -> Unit,
    onMergePdfs: () -> Unit,
    onPdfToImage: () -> Unit,
    onImageToPdf: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenLegal: (LegalTab) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                ThreeDitDocWordmark()
                                Text(
                                    text = stringResource(R.string.drawer_tagline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.drawer_offline_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.drawer_section_actions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_open_pdf)) },
                icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                selected = false,
                onClick = onOpenPdf,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_open_pdf")
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )

            Text(
                text = stringResource(R.string.drawer_section_tools),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_sample_pdf)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.Assignment, contentDescription = null) },
                selected = false,
                onClick = onOpenSamplePdf,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_sample_pdf")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_merge_pdfs)) },
                icon = { Icon(Icons.Rounded.LibraryAdd, contentDescription = null) },
                selected = false,
                onClick = onMergePdfs,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_merge_pdfs")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_pdf_to_image)) },
                icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                selected = false,
                onClick = onPdfToImage,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_pdf_to_image")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_image_to_pdf)) },
                icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                selected = false,
                onClick = onImageToPdf,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_image_to_pdf")
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )

            Text(
                text = stringResource(R.string.drawer_section_guide),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_tutorial)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
                selected = currentRoute == "tutorial",
                onClick = onOpenTutorial,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_tutorial")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_terms)) },
                icon = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
                selected = false,
                onClick = { onOpenLegal(LegalTab.TERMS) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_terms")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_privacy)) },
                icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
                selected = false,
                onClick = { onOpenLegal(LegalTab.PRIVACY) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_privacy")
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_about)) },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                selected = false,
                onClick = { onOpenLegal(LegalTab.ABOUT) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_about")
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )

            Text(
                text = stringResource(R.string.drawer_section_developer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            DeveloperSupportCard(
                compact = true,
                tagPrefix = "drawer"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "3ditdoc • v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DeveloperSupportCard(
    compact: Boolean,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val email = stringResource(R.string.dev_email_value)
    val emailSubject = stringResource(R.string.dev_email_subject)
    val pixKey = stringResource(R.string.dev_pix_key)

    var emailCopied by remember { mutableStateOf(false) }
    var pixCopied by remember { mutableStateOf(false) }

    LaunchedEffect(emailCopied) {
        if (emailCopied) {
            delay(2200)
            emailCopied = false
        }
    }

    LaunchedEffect(pixCopied) {
        if (pixCopied) {
            delay(2200)
            pixCopied = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_developer_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.dev_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = stringResource(R.string.dev_card_promise),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Contact Email Section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.dev_contact_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoOverlayFontFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val mailUri = Uri.parse("mailto:$email?subject=${Uri.encode(emailSubject)}")
                            val intent = Intent(Intent.ACTION_SENDTO, mailUri)
                            val opened = runCatching { context.startActivity(intent) }.isSuccess
                            if (!opened) {
                                copyTextToClipboard(context, "3ditdoc Email", email)
                                emailCopied = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${tagPrefix}_send_email_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dev_send_email_btn),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            copyTextToClipboard(context, "3ditdoc Email", email)
                            emailCopied = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${tagPrefix}_copy_email_btn")
                    ) {
                        Icon(
                            imageVector = if (emailCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (emailCopied) R.string.dev_copied_feedback else R.string.dev_copy_email_btn
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Voluntary Pix Support Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.dev_support_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.dev_support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.dev_pix_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = pixKey,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoOverlayFontFamily,
                            fontSize = if (compact) 11.sp else 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        copyTextToClipboard(context, "3ditdoc Pix", pixKey)
                        pixCopied = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_copy_pix_btn")
                ) {
                    Icon(
                        imageVector = if (pixCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (pixCopied) R.string.dev_pix_copied_feedback else R.string.dev_copy_pix_btn
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AnimatedVisibility(visible = pixCopied) {
                    Text(
                        text = stringResource(R.string.dev_pix_copied_feedback),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = stringResource(R.string.dev_other_donations_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
fun ThreeDitDocWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "3dit",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(3.dp))
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "doc",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}
