package com.technoblaze.drishtistem.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.data.ConceptRepository
import com.technoblaze.drishtistem.model.Subject

private val SubjectColors = mapOf(
    Subject.MATHS to Color(0xFF1E3A5F),
    Subject.PHYSICS to Color(0xFF4A2E1A),
    Subject.CHEMISTRY to Color(0xFF1E4A2E)
)

@Composable
fun HomeScreen(engines: Engines, onSubject: (Subject) -> Unit, onScan: () -> Unit) {
    LaunchedEffect(Unit) {
        engines.speech.announce(
            "Welcome to Drishti STEM. Turning visual STEM into touch and sound. " +
                "Choose a subject: Mathematics, Physics, or Chemistry. " +
                "Or use upload a molecule or graph to read a photo of a printed structure.",
            interrupt = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B1E))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "DrishtiSTEM",
            color = Color(0xFFE8C49A),
            fontSize = 34.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            "Turning visual STEM into touch and sound",
            color = Color(0xFF9E9E9E),
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Subject.entries.forEach { subject ->
            Card(
                onClick = {
                    engines.haptics.tick()
                    onSubject(subject)
                },
                colors = CardDefaults.cardColors(containerColor = SubjectColors.getValue(subject)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics {
                        contentDescription =
                            "${subject.displayName}. ${ConceptRepository.bySubject(subject).size} concepts. Double tap to open."
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(subject.displayName, color = Color.White, fontSize = 24.sp)
                    Text(
                        "${ConceptRepository.bySubject(subject).size} concepts",
                        color = Color(0xFFBDBDBD),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Upload a photo of a printed structure and explore it by touch.
        Card(
            onClick = {
                engines.haptics.tick()
                onScan()
            },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2E12)),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics {
                    contentDescription =
                        "Upload a structure. Choose a photo of a molecule or graph and explore it by touch. Double tap to open."
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("🖼  Upload a molecule or graph", color = Color(0xFFE8C49A), fontSize = 22.sp)
                Text(
                    "Read a photo of a printed structure",
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SubjectScreen(
    subject: Subject,
    engines: Engines,
    onConcept: (String) -> Unit,
    onBack: () -> Unit
) {
    val concepts = ConceptRepository.bySubject(subject)

    LaunchedEffect(subject) {
        val names = concepts.joinToString(". ") { it.title }
        engines.speech.announce(
            "${subject.spokenIntro} ${concepts.size} concepts: $names.",
            interrupt = true
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1B1E))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { engines.quietAll(); onBack() },
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = "Back to subjects" }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFE8C49A))
            }
            Text(subject.displayName, color = Color(0xFFE8C49A), fontSize = 22.sp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(concepts, key = { it.id }) { concept ->
                Card(
                    onClick = {
                        engines.haptics.tick()
                        onConcept(concept.id)
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3136)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics {
                            contentDescription = "${concept.title}. Double tap to explore."
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(concept.title, color = Color.White, fontSize = 19.sp)
                    }
                }
            }
        }
    }
}
