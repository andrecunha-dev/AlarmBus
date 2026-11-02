package com.example.alarmbus

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ScheduledRoutinesDialog(
    currentLat: Double,
    currentLng: Double,
    currentAddress: String,
    currentDist: Int,
    onDismiss: () -> Unit,
    onOpenMapPicker: () -> Unit
) {
    val context = LocalContext.current
    val purpleColor = Color(0xFF6750A4)
    val scrollState = rememberScrollState()

    var routines by remember { mutableStateOf(RoutineManager.getRoutines(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<ScheduledRoutine?>(null) }

    // Campos do formulário com estado do tipo String para permitir digitação livre
    var nameInput by remember { mutableStateOf("Fretado - Ida ao Trabalho") }
    var hourText by remember { mutableStateOf("06") }
    var minuteText by remember { mutableStateOf("30") }
    var selectedDays by remember { mutableStateOf(listOf(2, 3, 4, 5, 6)) }
    var destLatInput by remember { mutableStateOf(if (currentLat != 0.0) currentLat else -23.550520) }
    var destLngInput by remember { mutableStateOf(if (currentLng != 0.0) currentLng else -46.633308) }
    var destAddressInput by remember { mutableStateOf(if (currentAddress.isNotBlank()) currentAddress else "Avenida Paulista, 1000") }
    var alertDistInput by remember { mutableStateOf(if (currentDist > 0) currentDist else 300) }

    if (showCreateDialog) {
        Dialog(
            onDismissRequest = { showCreateDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = purpleColor, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editingRoutine != null) "Editar Rotina" else "Nova Rotina Programada",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showCreateDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nome da Rotina (Ex: Fretado Ida)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horário com teclado numérico e edição livre
                    Text("Horário de Partida do Ônibus:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    hourText = input
                                }
                            },
                            label = { Text("Hora (00-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    minuteText = input
                                }
                            },
                            label = { Text("Minuto (00-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Seleção dos Dias da Semana
                    Text("Dias da Semana:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dayLabels = listOf(
                            2 to "Seg", 3 to "Ter", 4 to "Qua",
                            5 to "Qui", 6 to "Sex", 7 to "Sáb", 1 to "Dom"
                        )
                        dayLabels.forEach { (dayInt, label) ->
                            val isSelected = selectedDays.contains(dayInt)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) purpleColor else Color(0xFFF3F0F8),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable {
                                        val mutable = selectedDays.toMutableList()
                                        if (isSelected) mutable.remove(dayInt) else mutable.add(dayInt)
                                        selectedDays = mutable
                                    }
                            ) {
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Destino Editable Text Field + Google Maps Picker Button
                    Text("Endereço de Destino:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = destAddressInput,
                        onValueChange = { destAddressInput = it },
                        label = { Text("Digite o endereço de destino") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            onOpenMapPicker()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ESCOLHER NO GOOGLE MAPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Raio de Aviso
                    Text("Raio de Alerta:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(200, 300, 500, 750, 1000).forEach { dist ->
                            val isSel = alertDistInput == dist
                            Button(
                                onClick = { alertDistInput = dist },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) purpleColor else Color(0xFFF3F0F8),
                                    contentColor = if (isSel) Color.White else Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (dist >= 1000) "1km" else "${dist}m",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (selectedDays.isEmpty()) {
                                Toast.makeText(context, "Selecione pelo menos um dia da semana", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val parsedHour = (hourText.toIntOrNull() ?: 6).coerceIn(0, 23)
                            val parsedMinute = (minuteText.toIntOrNull() ?: 30).coerceIn(0, 59)

                            val newRoutine = ScheduledRoutine(
                                id = editingRoutine?.id ?: System.currentTimeMillis().toString(),
                                name = nameInput.ifBlank { "Rotina Fretado" },
                                enabled = true,
                                hour = parsedHour,
                                minute = parsedMinute,
                                daysOfWeek = selectedDays,
                                destLat = destLatInput,
                                destLng = destLngInput,
                                destAddress = destAddressInput,
                                alertDist = alertDistInput
                            )

                            RoutineManager.saveRoutine(context, newRoutine)
                            routines = RoutineManager.getRoutines(context)
                            showCreateDialog = false
                            editingRoutine = null

                            Toast.makeText(context, "⏰ Rotina '${newRoutine.name}' programada para as ${String.format(java.util.Locale.US, "%02d:%02d", parsedHour, parsedMinute)}!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SALVAR ROTINA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = purpleColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rotinas Programadas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "⏰ Configure os horários do seu fretado para o aplicativo ativar o GPS e o alarme automaticamente sem você precisar abrir o app!",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        editingRoutine = null
                        nameInput = "Fretado - Ida ao Trabalho"
                        hourText = "06"
                        minuteText = "30"
                        selectedDays = listOf(2, 3, 4, 5, 6)
                        if (currentLat != 0.0) {
                            destLatInput = currentLat
                            destLngInput = currentLng
                            destAddressInput = currentAddress
                            alertDistInput = currentDist
                        }
                        showCreateDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NOVA ROTINA PROGRAMADA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (routines.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F6FA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Nenhuma rotina cadastrada.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cadastre os dias e horários do seu ônibus para o AlarmBus cuidar de tudo por você!", fontSize = 11.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    routines.forEach { r ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (r.enabled) Color(0xFFF3F0F8) else Color(0xFFF8F6FA)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (r.enabled) purpleColor.copy(alpha = 0.4f) else Color(0xFFE0E0E0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = r.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (r.enabled) purpleColor else Color.DarkGray
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%02d:%02d", r.hour, r.minute)} • ${RoutineManager.formatDaysOfWeek(r.daysOfWeek)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Destino: ${r.destAddress} (${if (r.alertDist >= 1000) "1km" else "${r.alertDist}m"})",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }

                                    Switch(
                                        checked = r.enabled,
                                        onCheckedChange = { enabled ->
                                            RoutineManager.toggleRoutine(context, r.id, enabled)
                                            routines = RoutineManager.getRoutines(context)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingRoutine = r
                                            nameInput = r.name
                                            hourText = String.format(java.util.Locale.US, "%02d", r.hour)
                                            minuteText = String.format(java.util.Locale.US, "%02d", r.minute)
                                            selectedDays = r.daysOfWeek
                                            destLatInput = r.destLat
                                            destLngInput = r.destLng
                                            destAddressInput = r.destAddress
                                            alertDistInput = r.alertDist
                                            showCreateDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = purpleColor, modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            RoutineManager.deleteRoutine(context, r.id)
                                            routines = RoutineManager.getRoutines(context)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
