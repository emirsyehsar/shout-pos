package com.emirst.shoutpos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emirst.shoutpos.ui.theme.ShoutposTheme

class MainActivity : ComponentActivity() {

    private val speechViewModel: SpeechViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoutposTheme {
                val speechText by speechViewModel.speechText.observeAsState("")
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PressButtonScreen(
                        speechText = speechText,
                        onPressStart = speechViewModel::onPressStart,
                        onPressEnd = speechViewModel::onPressEnd,
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
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toastText = stringResource(R.string.press_button_toast)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) onPressStart() else onPressEnd()
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
            text = speechText,
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
        PressButtonScreen(speechText = "", onPressStart = {}, onPressEnd = {})
    }
}
