package com.glowplan.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

private val Background = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF1C2440)
private val MutedText = Color(0xFF68738C)
private val Purple = Color(0xFF7554D9)
private val PalePurple = Color(0xFFF0ECFF)

private const val STORAGE = "glowplan_settings"
private const val ROOMS_KEY = "room_presets_v1"

private data class RoomSpec(
    val id: String,
    val name: String,
    val lumensPerSqFt: Double,
    val emoji: String,
    val argb: Long,
    val custom: Boolean = false
)

private enum class LengthUnit(val label: String, val feetPerUnit: Double) {
    FEET("ft", 1.0), METRES("m", 3.280839895013123),
    CENTIMETRES("cm", 0.03280839895013123), INCHES("in", 1.0 / 12.0)
}

private enum class Page { ROOMS, CALCULATOR, RESULTS, SETTINGS }

private data class LightingResult(
    val areaSqFt: Double,
    val totalLumens: Double,
    val requiredLights: Int,
    val lampLumens: Double
)

private fun defaultRooms(): List<RoomSpec> = listOf(
    RoomSpec("living", "Living Room", 15.0, "🛋️", 0xFF8562E6),
    RoomSpec("kitchen", "Kitchen - General", 75.0, "🍳", 0xFFEF963F),
    RoomSpec("dining", "Dining Room", 35.0, "🍽️", 0xFFE26591),
    RoomSpec("bedroom", "Bedroom", 15.0, "🛏️", 0xFF25A7A3),
    RoomSpec("hallway", "Hallway", 10.0, "🚪", 0xFF5184E4),
    RoomSpec("bathroom", "Bathroom", 75.0, "🛁", 0xFF30AA7B)
)

private fun readRooms(context: Context): List<RoomSpec> {
    val json = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        .getString(ROOMS_KEY, null) ?: return defaultRooms()
    return runCatching {
        val arr = JSONArray(json)
        val parsed = (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            RoomSpec(
                id = item.getString("id"),
                name = item.getString("name"),
                lumensPerSqFt = item.getDouble("lumensPerSqFt"),
                emoji = item.optString("emoji", "🏠"),
                argb = item.getLong("argb"),
                custom = item.optBoolean("custom", false)
            )
        }.filter { it.name.isNotBlank() && it.lumensPerSqFt.isFinite() && it.lumensPerSqFt > 0.0 }
        parsed.ifEmpty { defaultRooms() }
    }.getOrElse { defaultRooms() }
}

private fun saveRooms(context: Context, rooms: List<RoomSpec>) {
    val arr = JSONArray()
    rooms.forEach { room ->
        arr.put(JSONObject().apply {
            put("id", room.id)
            put("name", room.name)
            put("lumensPerSqFt", room.lumensPerSqFt)
            put("emoji", room.emoji)
            put("argb", room.argb)
            put("custom", room.custom)
        })
    }
    context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        .edit().putString(ROOMS_KEY, arr.toString()).apply()
}

private fun number(value: Double, decimals: Int = 1): String {
    if (!value.isFinite()) return "—"
    val formatted = String.format(Locale.US, "%,.${decimals}f", value)
    return if (decimals > 0) formatted.trimEnd('0').trimEnd('.') else formatted
}

private fun compact(value: Double): String =
    String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private fun calculate(room: RoomSpec, length: String, width: String,
                      unit: LengthUnit, lightLumens: String): LightingResult? {
    val l = length.trim().toDoubleOrNull() ?: return null
    val w = width.trim().toDoubleOrNull() ?: return null
    val lamp = lightLumens.trim().toDoubleOrNull() ?: return null
    if (!l.isFinite() || !w.isFinite() || !lamp.isFinite() ||
        l <= 0 || w <= 0 || lamp <= 0 || !room.lumensPerSqFt.isFinite()) return null
    val area = (l * unit.feetPerUnit) * (w * unit.feetPerUnit)
    val total = area * room.lumensPerSqFt
    val lights = ceil(total / lamp)
    if (!lights.isFinite() || lights < 1.0 || lights > 1_000_000) return null
    return LightingResult(area, total, lights.toInt(), lamp)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlowPlanApp() }
    }
}

@Composable
private fun GlowPlanApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var rooms by remember { mutableStateOf(readRooms(context)) }
    var selectedRoomId by remember { mutableStateOf("living") }
    var page by remember { mutableStateOf(Page.ROOMS) }
    var length by remember { mutableStateOf("12") }
    var width by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf(LengthUnit.FEET) }
    var lightLumens by remember { mutableStateOf("1000") }
    var showEditor by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<RoomSpec?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val selected = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.first()
    val result = calculate(selected, length, width, unit, lightLumens)

    fun updateRooms(changed: List<RoomSpec>) {
        rooms = changed
        saveRooms(context, changed)
    }
    fun changeUnit(newUnit: LengthUnit) {
        if (newUnit == unit) return
        length.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
            length = compact(it * unit.feetPerUnit / newUnit.feetPerUnit)
        }
        width.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
            width = compact(it * unit.feetPerUnit / newUnit.feetPerUnit)
        }
        unit = newUnit
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple, onPrimary = Color.White,
            background = Background, surface = Color.White,
            onBackground = TextDark, onSurface = TextDark
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf(
                        Triple(Page.ROOMS, "Rooms", "▦"),
                        Triple(Page.CALCULATOR, "Calculator", "✦"),
                        Triple(Page.SETTINGS, "Settings", "⚙")
                    ).forEach { (destination, title, symbol) ->
                        NavigationBarItem(
                            selected = page == destination || (destination == Page.CALCULATOR && page == Page.RESULTS),
                            onClick = { page = destination },
                            icon = { Text(symbol, fontSize = 23.sp) },
                            label = { Text(title) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                AppHeader()
                Box(Modifier.fillMaxSize()) {
                    when (page) {
                        Page.ROOMS -> RoomSelection(rooms, onRoomClick = {
                            selectedRoomId = it.id
                            page = Page.CALCULATOR
                        })
                        Page.CALCULATOR -> CalculatorScreen(
                            room = selected, length = length, width = width,
                            unit = unit, lightLumens = lightLumens, result = result,
                            onLength = { length = it }, onWidth = { width = it },
                            onUnit = ::changeUnit, onLightLumens = { lightLumens = it },
                            onCalculate = { page = Page.RESULTS },
                            onChangePreset = { page = Page.SETTINGS }
                        )
                        Page.RESULTS -> ResultsScreen(
                            room = selected, result = result,
                            onBack = { page = Page.CALCULATOR },
                            onChangeRoom = { page = Page.ROOMS }
                        )
                        Page.SETTINGS -> SettingsScreen(
                            rooms = rooms,
                            onEdit = { editingRoom = it; showEditor = true },
                            onAdd = { editingRoom = null; showEditor = true },
                            onReset = { showResetConfirm = true }
                        )
                    }
                }
            }
        }
        if (showEditor) {
            PresetEditor(
                room = editingRoom,
                onDismiss = { showEditor = false },
                onSave = { newName, newLevel ->
                    val existing = editingRoom
                    if (existing != null) {
                        updateRooms(rooms.map {
                            if (it.id == existing.id) it.copy(
                                name = if (existing.custom) newName else it.name,
                                lumensPerSqFt = newLevel
                            ) else it
                        })
                    } else {
                        val colors = listOf(0xFF8562E6, 0xFF25A7A3, 0xFFE26591, 0xFF5184E4)
                        val newRoom = RoomSpec(
                            id = UUID.randomUUID().toString(), name = newName,
                            lumensPerSqFt = newLevel, emoji = "🏠",
                            argb = colors[rooms.count { it.custom } % colors.size], custom = true
                        )
                        updateRooms(rooms + newRoom)
                    }
                    showEditor = false
                },
                onDelete = if (editingRoom?.custom == true) ({
                    val deletedId = editingRoom?.id
                    updateRooms(rooms.filterNot { it.id == deletedId })
                    if (selectedRoomId == deletedId) selectedRoomId = "living"
                    showEditor = false
                }) else null
            )
        }
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Restore room defaults?") },
                text = { Text("This restores all six original lighting values and removes custom rooms.") },
                confirmButton = {
                    TextButton(onClick = {
                        updateRooms(defaultRooms())
                        selectedRoomId = "living"
                        showResetConfirm = false
                    }) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(PalePurple),
            contentAlignment = Alignment.Center
        ) { Text("✦", color = Purple, fontSize = 27.sp) }
        Column {
            Text("GlowPlan", color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text("Room lighting calculator", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RoomSelection(rooms: List<RoomSpec>, onRoomClick: (RoomSpec) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Choose your room", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
        Text("Tap a room card to calculate the lights required.", fontSize = 14.sp, color = MutedText)
        Spacer(Modifier.height(18.dp))
        rooms.chunked(2).forEach { rowRooms ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowRooms.forEach { room ->
                    RoomTile(room = room, modifier = Modifier.weight(1f), onClick = { onRoomClick(room) })
                }
                if (rowRooms.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = PalePurple, shape = RoundedCornerShape(16.dp)) {
            Text(
                "Lighting levels can be changed anytime in Settings. Values are in lumens per square foot.",
                modifier = Modifier.padding(16.dp), color = Purple, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RoomTile(room: RoomSpec, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = Color(room.argb.toInt())
    Box(
        modifier = modifier.height(162.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.77f))))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(room.emoji, fontSize = 35.sp)
            Column {
                Text(room.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("${number(room.lumensPerSqFt)} lm/ft²", color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SelectedRoomBanner(room: RoomSpec) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Color(room.argb.toInt())).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(room.emoji, fontSize = 34.sp)
        Column {
            Text(room.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("${number(room.lumensPerSqFt)} lumens / ft²", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
}

@Composable
private fun CalculatorScreen(
    room: RoomSpec, length: String, width: String,
    unit: LengthUnit, lightLumens: String, result: LightingResult?,
    onLength: (String) -> Unit, onWidth: (String) -> Unit,
    onUnit: (LengthUnit) -> Unit, onLightLumens: (String) -> Unit,
    onCalculate: () -> Unit, onChangePreset: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Lighting calculator", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Enter your room size and the lumen rating on the light box.", fontSize = 13.sp, color = MutedText)
        Spacer(Modifier.height(16.dp))
        SelectedRoomBanner(room)
        Spacer(Modifier.height(20.dp))
        SectionHeading("1. Room measurements")
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LengthUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit, onClick = { onUnit(option) },
                    label = { Text(when (option) {
                        LengthUnit.FEET -> "Feet (ft)"
                        LengthUnit.METRES -> "Metres (m)"
                        LengthUnit.CENTIMETRES -> "cm"
                        LengthUnit.INCHES -> "Inches"
                    }) }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput("Length (${unit.label})", length, onLength, Modifier.weight(1f))
            NumberInput("Width (${unit.label})", width, onWidth, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        SectionHeading("2. Light details")
        Spacer(Modifier.height(9.dp))
        NumberInput("Lumens per light (lm)", lightLumens, onLightLumens, Modifier.fillMaxWidth())
        Text("Use the lumen value printed on the LED light packaging.", color = MutedText, fontSize = 12.sp)
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Required lighting", fontSize = 13.sp, color = MutedText)
                Text("${number(room.lumensPerSqFt)} lm/ft²", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onChangePreset) { Text("Edit preset") }
        }
        Spacer(Modifier.height(8.dp))
        if (result != null) {
            Surface(color = PalePurple, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Room area", color = MutedText, fontSize = 12.sp)
                        Text("${number(result.areaSqFt)} ft²", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total lumens needed", color = MutedText, fontSize = 12.sp)
                        Text(number(result.totalLumens, 0), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    }
                }
            }
        } else {
            Text("Enter positive measurements and light lumens to calculate.", color = Color(0xFFB5514B), fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onCalculate, enabled = result != null, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Calculate lights  →", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        Text("Basic estimate: assumes all listed lamp lumens are available to light the room. Fixture losses, beam angle and placement are not included.",
            color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun NumberInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (new.length <= 16 && new.all { it.isDigit() || it == '.' }) onValueChange(new)
        },
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun ResultsScreen(
    room: RoomSpec, result: LightingResult?, onBack: () -> Unit, onChangeRoom: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Your lighting plan", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
        Spacer(Modifier.height(14.dp))
        SelectedRoomBanner(room)
        Spacer(Modifier.height(15.dp))
        if (result == null) {
            Text("Please enter valid calculator values.", color = MutedText)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Return to calculator") }
            return@Column
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7554D9), Color(0xFFAA73E8))))
                .padding(25.dp), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", fontSize = 39.sp, color = Color.White)
                Text("${result.requiredLights}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 75.sp)
                Text("LIGHTS REQUIRED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(7.dp))
                Text("Rounded up to a whole light", color = Color.White.copy(alpha = 0.87f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoRow("Room area", "${number(result.areaSqFt)} ft²")
                HorizontalDivider(color = Background)
                InfoRow("Lighting level", "${number(room.lumensPerSqFt)} lm/ft²")
                HorizontalDivider(color = Background)
                InfoRow("Total light required", "${number(result.totalLumens, 0)} lm")
                HorizontalDivider(color = Background)
                InfoRow("Lumens per light", "${number(result.lampLumens, 0)} lm")
                HorizontalDivider(color = Background)
                InfoRow("Minimum light count", "${result.requiredLights}")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Formula: ceiling((area in ft² × room lm/ft²) ÷ lamp lumens)", color = MutedText, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Change measurements")
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick = onChangeRoom, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Choose another room")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, fontSize = 13.sp)
        Text(value, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(
    rooms: List<RoomSpec>, onEdit: (RoomSpec) -> Unit, onAdd: () -> Unit, onReset: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Room presets", fontSize = 26.sp, color = TextDark, fontWeight = FontWeight.ExtraBold)
        Text("Tap any card to edit the required lumens per square foot.", color = MutedText, fontSize = 13.sp)
        Spacer(Modifier.height(17.dp))
        rooms.forEach { room ->
            Surface(
                color = Color.White, shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onEdit(room) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(49.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(room.argb.toInt()).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text(room.emoji, fontSize = 25.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(room.name, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${number(room.lumensPerSqFt)} lm/ft²", color = MutedText, fontSize = 13.sp)
                    }
                    Text("Edit", color = Purple, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Button(
            onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("+ Add custom room") }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            onClick = onReset, modifier = Modifier.fillMaxWidth().height(49.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Restore original presets") }
        Spacer(Modifier.height(14.dp))
        Text("Your edits are stored on this Android device and remain available after the app restarts.",
            color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun PresetEditor(
    room: RoomSpec?, onDismiss: () -> Unit, onSave: (String, Double) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(room?.id) { mutableStateOf(room?.name ?: "") }
    var level by remember(room?.id) { mutableStateOf(room?.lumensPerSqFt?.let { compact(it) } ?: "20") }
    val validLevel = level.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100_000 }
    val valid = name.isNotBlank() && validLevel != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (room == null) "Add room" else "Edit lighting preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (room == null || room.custom) {
                    OutlinedTextField(
                        value = name, onValueChange = { if (it.length <= 45) name = it },
                        label = { Text("Room name") }, singleLine = true
                    )
                } else {
                    Text(room.name, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = level,
                    onValueChange = { if (it.length <= 12 && it.all { char -> char.isDigit() || char == '.' }) level = it },
                    label = { Text("Lumens per sq. ft") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("This is the brightness target for this room, not the lamp's lumen rating.",
                    color = MutedText, fontSize = 12.sp)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete custom room", color = Color(0xFFC23A51)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                validLevel?.let { onSave(name.trim(), it) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
