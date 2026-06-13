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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.data.EquationLoader
import com.sonari.app.data.MoleculeLoader
import com.sonari.app.model.Renderable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF1A1B1E)
private val ACCENT = Color(0xFF4FC3F7)
private val FIELD_BG = Color(0xFF2C2D31)
private val TAB_ACTIVE = Color(0xFF4FC3F7)
private val TAB_INACTIVE = Color(0xFF2C2D31)
private val ERROR = Color(0xFFEF5350)

private enum class InputMode { EQUATION, MOLECULE }

private val EQUATION_PRESETS = listOf(
    "x^2" to "Parabola",
    "sin(x)" to "Sine",
    "x^3-3*x" to "Cubic",
    "exp(x)" to "Exponential",
    "log(x)" to "Logarithm"
)

private val MOLECULE_FALLBACKS = listOf("water", "caffeine", "aspirin")

@Composable
fun HomeScreen(
    onLoad: (Renderable) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputMode by rememberSaveable { mutableStateOf(InputMode.EQUATION) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Sonari", color = Color.White, fontSize = 28.sp)
        Text("Accessible STEM Explorer", color = Color(0xFF888888), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(20.dp))

        // Input mode selector.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            InputMode.entries.forEach { m ->
                val active = m == inputMode
                Button(
                    onClick = { inputMode = m },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) TAB_ACTIVE else TAB_INACTIVE
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${m.name.lowercase()} mode tab" }
                ) {
                    Text(
                        text = m.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                        color = if (active) Color.Black else Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (inputMode) {
            InputMode.EQUATION -> EquationPanel(onLoad)
            InputMode.MOLECULE -> MoleculePanel(onLoad)
        }
    }
}

// ─── Equation panel ───────────────────────────────────────────────────────────

@Composable
private fun EquationPanel(onLoad: (Renderable) -> Unit) {
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
            errorMsg = "Domain: enter two numbers with xMin < xMax"
            return
        }
        EquationLoader.load(equation, xMin, xMax)
            .onSuccess { chart -> errorMsg = ""; onLoad(chart) }
            .onFailure { e -> errorMsg = e.message ?: "Parse error" }
    }

    Text("Equation  (use x as the variable)", color = Color(0xFFCCCCCC), fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))

    OutlinedTextField(
        value = equation,
        onValueChange = { equation = it; errorMsg = "" },
        singleLine = true,
        placeholder = { Text("e.g.  x^2  or  sin(x)", color = Color(0xFF555555)) },
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { tryLoad() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Equation input" }
    )

    if (errorMsg.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(errorMsg, color = ERROR, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text("Domain", color = Color(0xFFCCCCCC), fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = xMinText,
            onValueChange = { xMinText = it; errorMsg = "" },
            singleLine = true,
            label = { Text("x min", color = Color(0xFF888888), fontSize = 12.sp) },
            colors = fieldColors(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "x minimum input" }
        )
        OutlinedTextField(
            value = xMaxText,
            onValueChange = { xMaxText = it; errorMsg = "" },
            singleLine = true,
            label = { Text("x max", color = Color(0xFF888888), fontSize = 12.sp) },
            colors = fieldColors(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "x maximum input" }
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = ::tryLoad,
        colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Explore equation" }
    ) { Text("Explore", color = Color.Black, fontSize = 17.sp) }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Quick examples", color = Color(0xFF888888), fontSize = 12.sp)
    Spacer(modifier = Modifier.height(8.dp))

    EQUATION_PRESETS.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { (expr, label) ->
                Button(
                    onClick = { equation = expr; errorMsg = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D31)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "$label: $expr" }
                ) {
                    Column {
                        Text(expr, color = ACCENT, fontSize = 13.sp)
                        Text(label, color = Color(0xFF888888), fontSize = 10.sp)
                    }
                }
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Molecule panel ───────────────────────────────────────────────────────────

@Composable
private fun MoleculePanel(onLoad: (Renderable) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun search(query: String) {
        if (query.isBlank()) { errorMsg = "Enter a molecule name"; return }
        keyboard?.hide()
        loading = true; errorMsg = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { MoleculeLoader.load(query) }
            loading = false
            result
                .onSuccess { mol -> onLoad(mol) }
                .onFailure { e -> errorMsg = e.message ?: "Not found" }
        }
    }

    Text("Molecule name", color = Color(0xFFCCCCCC), fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it; errorMsg = "" },
        singleLine = true,
        placeholder = { Text("e.g.  caffeine  or  aspirin", color = Color(0xFF555555)) },
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search(name) }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Molecule name input" }
    )

    if (errorMsg.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(errorMsg, color = ERROR, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (loading) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = ACCENT)
        }
    } else {
        Button(
            onClick = { search(name) },
            colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "Search molecule on PubChem" }
        ) { Text("Search PubChem", color = Color.Black, fontSize = 17.sp) }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Offline examples (always available)", color = Color(0xFF888888), fontSize = 12.sp)
    Spacer(modifier = Modifier.height(8.dp))

    MOLECULE_FALLBACKS.forEach { mol ->
        Button(
            onClick = { search(mol) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D31)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .semantics { contentDescription = "$mol molecule preset" }
        ) {
            Text(mol.replaceFirstChar { it.uppercaseChar() }, color = ACCENT, fontSize = 14.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = FIELD_BG,
    unfocusedContainerColor = FIELD_BG,
    focusedBorderColor = ACCENT,
    unfocusedBorderColor = Color(0xFF4A4B50)
)
