package dev.nynp.nothanks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.philipp_bobek.oss_licenses_parser.OssLicensesParser
import de.philipp_bobek.oss_licenses_parser.ThirdPartyLicense
import dev.nynp.nothanks.ui.theme.NoThanksTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(applicationContext)
        setContent {
            NoThanksTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(onBackPressedDispatcher)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onBackPressedDispatcher: OnBackPressedDispatcher) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen == Screen.Info) {
                    currentScreen = Screen.Main
                }
            }
        }
    }

    DisposableEffect(onBackPressedDispatcher) {
        onBackPressedDispatcher.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("No, Thanks!") }, actions = {
            IconButton(onClick = { currentScreen = Screen.Info }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.info_button_description)
                )
            }
        }, navigationIcon = {
            if (currentScreen == Screen.Info) {
                IconButton(onClick = { currentScreen = Screen.Main }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(
                            R.string.back_arrow_description
                        )
                    )
                }
            }
        })
    }) { paddingValues ->
        when (currentScreen) {
            Screen.Main -> MainContent(paddingValues)
            Screen.Info -> InfoScreen(paddingValues)
        }
    }
}

@Composable
fun MainContent(paddingValues: PaddingValues) {
    val context = LocalContext.current
    var isAccessibilityServiceEnabled by remember {
        mutableStateOf(
            isAccessibilityServiceEnabled(
                context
            )
        )
    }
    val isBlockingEnabled by SettingsManager.isBlockingEnabled.collectAsState()
    val blockedViews by SettingsManager.blockedViews.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(context)
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        item {
            SettingsItem(icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.accessibility_service_icon_description)
                )
            },
                title = stringResource(R.string.accessibility_service_title),
                subtitle = if (isAccessibilityServiceEnabled) stringResource(R.string.enabled) else stringResource(
                    R.string.disabled
                ),
                onClick = { openAccessibilitySettings(context) })
        }

        item {
            SettingsSwitch(icon = {
                Icon(
                    if (isBlockingEnabled) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = stringResource(R.string.enable_blocking_icon_description)
                )
            },
                title = stringResource(R.string.enable_blocking_title),
                isChecked = isBlockingEnabled,
                onCheckedChange = {
                    if (isAccessibilityServiceEnabled) SettingsManager.setBlockingEnabled(it)
                },
                enabled = isAccessibilityServiceEnabled
            )
        }

        item {
            Text(
                stringResource(R.string.blocked_views_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(if (isAccessibilityServiceEnabled) 1f else 0.5f)
            )
        }

        items(blockedViews) { view ->
            SettingsSwitch(icon = {
                when (view.name.lowercase()) {
                    "instagram reels" -> Icon(
                        ImageVector.vectorResource(R.drawable.instagram_brands_solid),
                        contentDescription = stringResource(R.string.instagram_icon_description),
                        modifier = Modifier.size(24.dp)
                    )

                    "youtube shorts" -> Icon(
                        ImageVector.vectorResource(R.drawable.youtube_brands_solid),
                        contentDescription = stringResource(R.string.youtube_icon_description),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
                title = view.name,
                isChecked = view.isBlocked,
                onCheckedChange = {
                    if (isAccessibilityServiceEnabled) SettingsManager.toggleBlockedView(view.id)
                },
                enabled = isAccessibilityServiceEnabled
            )
        }
    }
}

@Composable
fun InfoScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val thirdPartyLicenseMetadataFile =
        context.resources.openRawResource(R.raw.third_party_license_metadata)
    val thirdPartyLicensesFile = context.resources.openRawResource(R.raw.third_party_licenses)
    val licenses =
        OssLicensesParser.parseAllLicenses(thirdPartyLicenseMetadataFile, thirdPartyLicensesFile)
            .toMutableList()

    // not a dependency, so not covered by the OssLicensesParser thing
    licenses.add(
        ThirdPartyLicense(
            libraryName = "FontAwesome", licenseContent = "https://fontawesome.com/license/free"
        )
    )

    licenses.sortBy { it.libraryName }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        item {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.about_paragraph_1))
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.about_paragraph_2))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.developed_by))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(R.string.source_code),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/NewYearNewPhil/nothanks")
                })
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(stringResource(R.string.license_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.license_text))
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(stringResource(R.string.third_party_licenses_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(licenses) { license ->
            Text(text = license.libraryName,
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(license.licenseContent)
                    }
                    .padding(vertical = 4.dp))
        }
    }
}

@Composable
fun SettingsItem(
    icon: @Composable () -> Unit, title: String, subtitle: String? = null, onClick: () -> Unit
) {
    ListItem(leadingContent = icon,
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitch(
    icon: @Composable () -> Unit,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(leadingContent = icon, headlineContent = { Text(title) }, trailingContent = {
        Switch(
            checked = isChecked, onCheckedChange = onCheckedChange, enabled = enabled
        )
    }, modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
    )
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    val serviceName = "${context.packageName}/${NoThanksAccessibilityService::class.java.name}"
    return enabledServices?.contains(serviceName) == true
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    context.startActivity(intent)
}

sealed class Screen {
    object Main : Screen()
    object Info : Screen()
}