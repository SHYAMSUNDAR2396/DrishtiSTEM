package com.sonari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.data.EquationLoader
import com.sonari.app.model.LineChart

private val BG = Color(0xFF1A1B1E)
private val ACCENT = Color(0xFF4FC3F7)
private val FIELD_BG = Color(0xFF2C2D31)
private val ERROR = Color(0xFFEF5350)

private val PRESETS = listOf(
    "x^2" to "Parabola",
    "sin(x)" to "Sine wave",
    "x^3-3*x" to "Cubic",
    "exp(x)" to "Exponential",
    "log(x)" to "Logarithm"
)

@Composable
fun HomeScreen(
    onLoad: (LineChart) -> Unit,
    modifier: Modifier = Modifier
) {
    var equation by rememberSaveable { mutableStateOf("x^2") }
    var xMinText by rememberSaveable { mutableStateOf("-10") }
    var xMaxText by rememberSaveable { mutableStateOf("10") }
    var errorMsg by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun tryLoad() {
        keyboard?.hide()
        val xMin = xMinText.toDoubleOrNull()
        val xMax = xMaxText.toDoubleOrNull()
        if (xMin == null || xMax == null || xMin >= xMax) {
            errorMsg = "Domain must be two numbers with xMin < xMax"
            return
        }
        EquationLoader.load(equation, xMin, xMax)
            .onSuccess { chart -> errorMsg = ""; onLoad(chart) }
            .onFailure { e -> errorMsg = e.message ?: "Unknown error" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text("Sonari", color = Color.White, fontSize = 28.sp)
        Text("Accessible STEM Explorer", color = Color(0xFF888888), fontSize = 14.sp)

        Spacer(modifier = Modifier.height(28.dp))

        Text("Equation  (use x as the variable)", color = Color(0xFFCCCCCC), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = equation,
            onValueChange = { equation = it; errorMsg = "" },
            singleLine = true,
            placeholder = { Text("e.g.  x^2  or  sin(x)", color = Color(0xFF555555)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = FIELD_BG,
                unfocusedContainerColor = FIELD_BG,
                focusedBorderColor = ACCENT,
                unfocusedBorderColor = Color(0xFF4A4B50)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { tryLoad() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Equation input. Type a math expression using x." }
        )

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(errorMsg, color = ERROR, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Domain row.
        Text("Domain", color = Color(0xFFCCCCCC), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DomainField(
                label = "x min",
                value = xMinText,
                onChange = { xMinText = it; errorMsg = "" },
                modifier = Modifier.weight(1f)
            )
            DomainField(
                label = "x max",
                value = xMaxText,
                onChange = { xMaxText = it; errorMsg = "" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Explore button.
        Button(
            onClick = ::tryLoad,
            colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "Explore equation button" }
        ) {
            Text("Explore", color = Color.Black, fontSize = 17.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text("Quick examples", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // 2×3 preset grid.
        PRESETS.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pair.forEach { (expr, label) ->
                    Button(
                        onClick = { equation = expr; errorMsg = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D31)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "$label preset: $expr" }
                    ) {
                        Column {
                            Text(expr, color = ACCENT, fontSize = 13.sp)
                            Text(label, color = Color(0xFF888888), fontSize = 10.sp)
                        }
                    }
                }
                // Pad last row if odd number of presets.
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DomainField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, color = Color(0xFF888888), fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = FIELD_BG,
            unfocusedContainerColor = FIELD_BG,
            focusedBorderColor = ACCENT,
            unfocusedBorderColor = Color(0xFF4A4B50)
        ),
        modifier = modifier.semantics { contentDescription = "$label domain input" }
    )
}
