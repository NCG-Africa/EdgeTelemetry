package com.nathanclair.edgetelemetry

import android.os.Bundle
import android.util.Log
import android.widget.Space
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nathanclair.edgetelemetry.ui.theme.EdgeTelemetryTheme
import com.nathanclair.edgetelemetrysdk.EdgeTelemetry
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdgeTelemetryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                    Column(modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {

                        Button(onClick = {
                            EdgeTelemetry.getInstance().simulateCrash()
                        }) {
                            Text(text = "Crash Button")
                        }

                        Spacer(modifier = Modifier.padding(16.dp))

                        Button(onClick = {
                            EdgeTelemetry.getInstance().simulateNetworkRequest(
                                url = "https://api.example.com/users",
                                method = "GET",
                                apiName = "users-api"
                            )
                        }) {
                            Text(text = "Network Button")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EdgeTelemetryTheme {
        Greeting("Android")
    }
}