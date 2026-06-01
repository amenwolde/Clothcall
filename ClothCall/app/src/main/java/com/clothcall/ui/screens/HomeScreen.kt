package com.clothcall.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.clothcall.data.db.CaregiverProfile
import com.clothcall.data.db.Garment
import com.clothcall.ui.navigation.Route
import com.clothcall.ui.viewmodels.HomeViewModel
import com.clothcall.utils.ScanResultHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val profiles by viewModel.profiles.collectAsState()
    val garments by viewModel.garments.collectAsState()
    var outMode by remember { mutableStateOf(viewModel.isOutMode) }
    var profileDropdownExpanded by remember { mutableStateOf(false) }
    var garmentDropdownExpanded by remember { mutableStateOf(false) }
    val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
    var selectedGarmentId by remember { mutableIntStateOf(viewModel.selectedGarmentId) }
    val selectedGarment = garments.firstOrNull { it.id == selectedGarmentId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClothCall") },
                actions = {
                    IconButton(onClick = { navController.navigate(Route.WARDROBE) }) {
                        Icon(Icons.Filled.Checkroom, contentDescription = "Wardrobe")
                    }
                    IconButton(onClick = { navController.navigate(Route.CAREGIVER_SETUP) }) {
                        Icon(Icons.Filled.Person, contentDescription = "Caregiver profiles")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Profile switcher
            ProfileDropdown(
                profiles = profiles,
                active = activeProfile,
                expanded = profileDropdownExpanded,
                onToggle = { profileDropdownExpanded = !profileDropdownExpanded },
                onSelect = { profile ->
                    viewModel.setActiveProfile(profile.id)
                    profileDropdownExpanded = false
                }
            )

            // Garment selector — drives baseline comparison
            GarmentDropdown(
                garments = garments,
                selected = selectedGarment,
                expanded = garmentDropdownExpanded,
                onToggle = { garmentDropdownExpanded = !garmentDropdownExpanded },
                onSelect = { garment ->
                    val newId = garment?.id ?: -1
                    selectedGarmentId = newId
                    viewModel.selectedGarmentId = newId
                    garmentDropdownExpanded = false
                }
            )

            // Home / Out toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (outMode) "Out" else "Home",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Switch(
                    checked = outMode,
                    onCheckedChange = { outMode = it; viewModel.isOutMode = it }
                )
                Text(
                    text = if (outMode) "Call" else "Speaker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(16.dp))

            // Morning Spin
            TextButton(
                onClick = { navController.navigate(Route.MORNING_SPIN) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Morning Spin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Preview call
            TextButton(
                onClick = {
                    ScanResultHolder.response = "A navy blue shirt is being examined. " +
                        "The fabric overall appears in good condition. " +
                        "A faint darker mark has been noticed near the right cuff, " +
                        "roughly the size of a fingertip — it could be a water stain or a light contact mark. " +
                        "The collar and shoulders show no signs of visible fading, though this shirt " +
                        "has drifted very slightly from how a fresh shirt would appear — " +
                        "the tolerance level of ${activeProfile?.fadeThreshold ?: 15}% has not been exceeded. " +
                        "Do you still want to wear it?"
                    ScanResultHolder.caregiverName = activeProfile?.name
                    ScanResultHolder.fadeThreshold = activeProfile?.fadeThreshold ?: 15
                    navController.navigate(Route.CALL_UI)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Preview call",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Main button
            Button(
                onClick = { navController.navigate(Route.QUICK_SCAN) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Check My Clothes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

        }
    }
}

@Composable
private fun ProfileDropdown(
    profiles: List<CaregiverProfile>,
    active: CaregiverProfile?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (CaregiverProfile) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (active != null) "Trusted: ${active.name}" else "No profile selected",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle
        ) {
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No profiles — add one") },
                    onClick = onToggle
                )
            }
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text("${profile.name}  (${profile.fadeThreshold}% tolerance)") },
                    onClick = { onSelect(profile) }
                )
            }
        }
    }
}

@Composable
private fun GarmentDropdown(
    garments: List<Garment>,
    selected: Garment?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Garment?) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selected != null) "Comparing: ${selected.name}" else "Garment: None (stain check only)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle
        ) {
            // None option — clears baseline, stain-only mode
            DropdownMenuItem(
                text = {
                    Text(
                        "None — stain check only",
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                onClick = { onSelect(null) }
            )
            if (garments.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No garments saved — add one in Wardrobe") },
                    onClick = onToggle
                )
            }
            garments.forEach { garment ->
                DropdownMenuItem(
                    text = { Text(garment.name) },
                    onClick = { onSelect(garment) }
                )
            }
        }
    }
}
