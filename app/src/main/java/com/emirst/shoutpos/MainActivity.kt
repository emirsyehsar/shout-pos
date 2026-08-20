package com.emirst.shoutpos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emirst.shoutpos.ui.theme.ShoutposTheme

class MainActivity : ComponentActivity() {

    private val speechViewModel: SpeechViewModel by viewModels {
        viewModelFactory {
            initializer {
                SpeechViewModel(
                    SpeechAPI(
                        appContext = applicationContext,
                        recognitionListener = SpeechRecognitionListener(),
                        languageChecker = SpeechLanguageChecker(applicationContext)
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoutposTheme {
                val speechText by speechViewModel.speechText.observeAsState("")
                val statusRes by speechViewModel.statusRes.observeAsState(null)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PressButtonScreen(
                        speechText = speechText,
                        statusRes = statusRes,
                        onPressStart = speechViewModel::onPressStart,
                        onPressEnd = speechViewModel::onPressEnd,
                        onPermissionDenied = speechViewModel::onPermissionDenied,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun PressButtonScreen(
    speechText: String,
    statusRes: Int?,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toastText = stringResource(R.string.press_button_toast)

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        when {
            isPressed && hasMicPermission -> onPressStart()
            isPressed -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> onPressEnd()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            },
            interactionSource = interactionSource
        ) {
            Text(text = stringResource(R.string.press_button))
        }
        Text(
            text = statusRes?.let { stringResource(it) } ?: speechText,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(TAG_TXT_SPEECH)
        )
    }
}

const val TAG_TXT_SPEECH = "txt_speech"

@Preview(showBackground = true)
@Composable
fun PressButtonScreenPreview() {
    ShoutposTheme {
        PressButtonScreen(
            speechText = "",
            statusRes = null,
            onPressStart = {},
            onPressEnd = {},
            onPermissionDenied = {}
        )
    }
}
