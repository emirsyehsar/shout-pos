package com.emirst.shoutpos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.emirst.shoutpos.ui.theme.ShoutposTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoutposTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PressButtonScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PressButtonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toastText = stringResource(R.string.press_button_toast)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }) {
            Text(text = stringResource(R.string.press_button))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PressButtonScreenPreview() {
    ShoutposTheme {
        PressButtonScreen()
    }
}