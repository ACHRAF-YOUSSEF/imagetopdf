package tech.youssefachraf.image_to_pdf.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import tech.youssefachraf.image_to_pdf.BuildConfig
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "About",
                style = MaterialTheme.typography.labelLarge,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Verified,
                        title = "Build Version",
                        subtitle = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        onClick = null,
                    )

                    HorizontalDivider(
                        color = DarkBackground,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "A free, offline image to PDF converter built with Jetpack Compose",
                        onClick = null,
                    )

                    HorizontalDivider(
                        color = DarkBackground,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "Source Code",
                        subtitle = "View on GitHub",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/ACHRAF-YOUSSEF/kotlin-projects/image-to-pdf".toUri()
                            )
                            context.startActivity(intent)
                        },
                    )

                    HorizontalDivider(
                        color = DarkBackground,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    SettingsItem(
                        icon = Icons.Default.NewReleases,
                        title = "Latest Release",
                        subtitle = "Check for updates",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/ACHRAF-YOUSSEF/kotlin-projects/image-to-pdf/releases/latest".toUri()
                            )
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(22.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PrimaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
            Text(
                text = subtitle,
                color = SecondaryText,
                fontSize = 13.sp,
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

