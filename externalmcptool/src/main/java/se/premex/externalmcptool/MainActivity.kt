package se.premex.externalmcptool

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import se.premex.externalmcptool.ui.theme.PhonemcpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        val intent = Intent("se.premex.mcp.MCP_PROVIDER")

        val resolveInfoList = packageManager.queryIntentContentProviders(
            intent,
            PackageManager.MATCH_ALL
        )

        resolveInfoList.toString()

        enableEdgeToEdge()
        setContent {
            PhonemcpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalculatorScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {
    var firstNumber by remember { mutableStateOf("") }
    var secondNumber by remember { mutableStateOf("") }
    var selectedOperation by remember { mutableStateOf("add") }
    var result by remember { mutableStateOf<String?>(null) }

    val operations = listOf("add", "subtract", "multiply", "divide", "power")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "External MCP Tool Sample",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Calculator Tool",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "This app demonstrates how to implement an external MCP tool via a ContentProvider.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Tool Name: calculator",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Authority: se.premex.externalmcptool.calculator",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = firstNumber,
            onValueChange = { firstNumber = it },
            label = { Text("First Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = secondNumber,
            onValueChange = { secondNumber = it },
            label = { Text("Second Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text(text = "Operation:", style = MaterialTheme.typography.bodyLarge)

        operations.forEach { operation ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                RadioButton(
                    selected = operation == selectedOperation,
                    onClick = { selectedOperation = operation }
                )
                Text(
                    text = operation.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Button(
            onClick = {
                val a = firstNumber.toDoubleOrNull()
                val b = secondNumber.toDoubleOrNull()

                if (a != null && b != null) {
                    try {
                        val calculatedResult = when (selectedOperation) {
                            "add" -> a + b
                            "subtract" -> a - b
                            "multiply" -> a * b
                            "divide" -> {
                                if (b == 0.0) throw ArithmeticException("Division by zero")
                                a / b
                            }
                            "power" -> a.pow(b)
                            else -> 0.0
                        }
                        result = calculatedResult.toString()
                    } catch (e: Exception) {
                        result = "Error: ${e.message}"
                    }
                } else {
                    result = "Please enter valid numbers"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Calculate")
        }

        if (result != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = result ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "How it works:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "1. This app exposes a ContentProvider with the MCP tool protocol\n" +
                           "2. The MCP app discovers this tool through its MIME type\n" +
                           "3. When an AI wants to use this calculator, MCP forwards the request to this app\n" +
                           "4. The content provider executes the calculation and returns the result",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CalculatorScreenPreview() {
    PhonemcpTheme {
        CalculatorScreen()
    }
}