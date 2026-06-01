package com.clothcall.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.clothcall.data.db.CaregiverProfile
import com.clothcall.ui.viewmodels.CaregiverViewModel

// ── Local-only types (ViewModel just receives the final Int threshold) ────────

private enum class FadeRating { STILL_FINE, BORDERLINE, RETIRE }

private val FADE_LEVELS = listOf(0, 5, 10, 20, 30)

private val SHIRT_COLORS = listOf(
    Color(0xFF0D1B4B),  // 0%  — deep navy
    Color(0xFF1A3270),  // 5%
    Color(0xFF2E559A),  // 10%
    Color(0xFF6680BB),  // 20%
    Color(0xFFA8B8D8),  // 30% — washed out
)

private val JEANS_COLORS = listOf(
    Color(0xFF101F30),  // 0%  — raw dark denim
    Color(0xFF1A3A5C),  // 5%
    Color(0xFF1F5F9E),  // 10%
    Color(0xFF4A84B8),  // 20%
    Color(0xFF8FBBD8),  // 30% — very faded denim
)

private val FADE_LABELS = listOf("Fresh (0%)", "Barely faded (5%)", "Light fade (10%)", "Clear fade (20%)", "Heavily faded (30%)")

// ── Step navigation ──────────────────────────────────────────────────────────

private enum class CaregiverSetupStep { LIST, CREATE }

@Composable
fun CaregiverSetupScreen(navController: NavController, viewModel: CaregiverViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    var step by remember { mutableStateOf(CaregiverSetupStep.LIST) }

    when (step) {
        CaregiverSetupStep.LIST -> ProfileListScreen(
            profiles = profiles,
            onAdd = { step = CaregiverSetupStep.CREATE },
            onDelete = { viewModel.deleteProfile(it) },
            onBack = { navController.popBackStack() }
        )
        CaregiverSetupStep.CREATE -> CreateProfileScreen(
            onSave = { name, threshold ->
                viewModel.saveProfile(name, threshold)
                step = CaregiverSetupStep.LIST
            },
            onBack = { step = CaregiverSetupStep.LIST }
        )
    }
}

// ── Profile list ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileListScreen(
    profiles: List<CaregiverProfile>,
    onAdd: () -> Unit,
    onDelete: (CaregiverProfile) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trusted people") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = { TextButton(onClick = onAdd) { Text("Add new") } }
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No trusted people added yet.\nTap 'Add new' to create a profile.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Fade tolerance: ${profile.fadeThreshold}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { onDelete(profile) }) {
                                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Create profile ────────────────────────────────────────────────────────────

@Composable
private fun CreateProfileScreen(onSave: (String, Int) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val shirtRatings = remember { mutableStateMapOf<Int, FadeRating>() }
    val jeansRatings = remember { mutableStateMapOf<Int, FadeRating>() }

    val shirtsAllRated = FADE_LEVELS.all { shirtRatings.containsKey(it) }
    val jeansAllRated  = FADE_LEVELS.all { jeansRatings.containsKey(it) }
    val canSave = name.isNotBlank() && shirtsAllRated && jeansAllRated

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }

        Text("Add a trusted person", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name  (e.g. Wife, Colleague)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(4.dp))

        // ── Shirts section ────────────────────────────────────────────────
        SectionHeader("Shirts")
        Text(
            "For each shirt below, tap how you feel about it:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        FADE_LEVELS.forEachIndexed { i, level ->
            ClothingFadeRow(
                label = FADE_LABELS[i],
                color = SHIRT_COLORS[i],
                drawClothing = DrawScope::drawShirt,
                selected = shirtRatings[level],
                onSelect = { rating -> shirtRatings[level] = rating }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Jeans / trousers section ──────────────────────────────────────
        SectionHeader("Jeans / Trousers")
        Text(
            "Now do the same for jeans or trousers:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        FADE_LEVELS.forEachIndexed { i, level ->
            ClothingFadeRow(
                label = FADE_LABELS[i],
                color = JEANS_COLORS[i],
                drawClothing = DrawScope::drawJeans,
                selected = jeansRatings[level],
                onSelect = { rating -> jeansRatings[level] = rating }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (!canSave) {
            Text(
                text = when {
                    name.isBlank() -> "Enter a name to continue."
                    !shirtsAllRated -> "Rate all 5 shirt stages."
                    else -> "Rate all 5 jeans stages."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                val threshold = computeOverallThreshold(shirtRatings, jeansRatings)
                onSave(name.trim(), threshold)
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Save profile")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

// ── Single clothing fade row ──────────────────────────────────────────────────

@Composable
private fun ClothingFadeRow(
    label: String,
    color: Color,
    drawClothing: DrawScope.(Color) -> Unit,
    selected: FadeRating?,
    onSelect: (FadeRating) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clothing silhouette drawn on canvas
        Canvas(
            modifier = Modifier
                .width(52.dp)
                .height(68.dp)
        ) {
            drawClothing(color)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RatingChip("Still fine",  FadeRating.STILL_FINE, selected, onSelect, Color(0xFF2E7D32))
                RatingChip("Borderline",  FadeRating.BORDERLINE, selected, onSelect, Color(0xFFF57C00))
                RatingChip("Retire",      FadeRating.RETIRE,     selected, onSelect, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RatingChip(
    label: String,
    rating: FadeRating,
    selected: FadeRating?,
    onSelect: (FadeRating) -> Unit,
    activeColor: Color
) {
    val isSelected = selected == rating
    OutlinedButton(
        onClick = { onSelect(rating) },
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ── Canvas clothing silhouettes ───────────────────────────────────────────────

private fun DrawScope.drawShirt(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path()

    // Start at collar left
    path.moveTo(w * 0.34f, 0f)
    // Left shoulder
    path.lineTo(w * 0.18f, 0f)
    // Left sleeve outer edge
    path.lineTo(0f,        h * 0.14f)
    path.lineTo(0f,        h * 0.40f)
    // Left sleeve inner (back toward body)
    path.lineTo(w * 0.20f, h * 0.34f)
    // Left body down to hem
    path.lineTo(w * 0.16f, h)
    // Hem bottom
    path.lineTo(w * 0.84f, h)
    // Right body up from hem
    path.lineTo(w * 0.80f, h * 0.34f)
    // Right sleeve inner
    path.lineTo(w,         h * 0.40f)
    // Right sleeve outer edge
    path.lineTo(w,         h * 0.14f)
    // Right shoulder
    path.lineTo(w * 0.82f, 0f)
    path.lineTo(w * 0.66f, 0f)
    // V-neck
    path.lineTo(w * 0.50f, h * 0.17f)
    path.lineTo(w * 0.34f, 0f)
    path.close()

    drawPath(path, color)
}

private fun DrawScope.drawJeans(color: Color) {
    val w = size.width
    val h = size.height

    // Waist + crotch panel
    val upper = Path()
    upper.moveTo(w * 0.04f, 0f)
    upper.lineTo(w * 0.96f, 0f)
    upper.lineTo(w * 0.90f, h * 0.38f)
    upper.lineTo(w * 0.56f, h * 0.38f)
    upper.lineTo(w * 0.50f, h * 0.46f)   // crotch point
    upper.lineTo(w * 0.44f, h * 0.38f)
    upper.lineTo(w * 0.10f, h * 0.38f)
    upper.close()
    drawPath(upper, color)

    // Left leg (slightly tapered)
    val leftLeg = Path()
    leftLeg.moveTo(w * 0.07f, h * 0.36f)
    leftLeg.lineTo(w * 0.46f, h * 0.36f)
    leftLeg.lineTo(w * 0.40f, h)
    leftLeg.lineTo(0f,        h)
    leftLeg.close()
    drawPath(leftLeg, color)

    // Right leg (slightly tapered)
    val rightLeg = Path()
    rightLeg.moveTo(w * 0.54f, h * 0.36f)
    rightLeg.lineTo(w * 0.93f, h * 0.36f)
    rightLeg.lineTo(w,         h)
    rightLeg.lineTo(w * 0.60f, h)
    rightLeg.close()
    drawPath(rightLeg, color)
}

// ── Threshold helpers ─────────────────────────────────────────────────────────

private fun computeThreshold(ratings: Map<Int, FadeRating>): Int {
    val lastFine   = FADE_LEVELS.lastOrNull  { ratings[it] == FadeRating.STILL_FINE }
    val firstRetire = FADE_LEVELS.firstOrNull { ratings[it] == FadeRating.RETIRE }
    return when {
        lastFine == null && firstRetire == null -> 15
        lastFine == null  -> (firstRetire ?: 5) / 2
        firstRetire == null -> (lastFine + 30) / 2
        else -> (lastFine + firstRetire) / 2
    }
}

/** Average of shirt and jeans calibrations — stored as the profile's single threshold. */
private fun computeOverallThreshold(
    shirtRatings: Map<Int, FadeRating>,
    jeansRatings:  Map<Int, FadeRating>
): Int = (computeThreshold(shirtRatings) + computeThreshold(jeansRatings)) / 2
