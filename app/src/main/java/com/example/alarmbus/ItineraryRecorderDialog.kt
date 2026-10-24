package com.example.alarmbus

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ItineraryRecorderDialog(
    currentLat: Double,
    currentLng: Double,
    onDismiss: () -> Unit,
    onItinerarySaved: () -> Unit
) {
    val context = LocalContext.current
    val purpleColor = Color(0xFF6750A4)

    var itineraryName by remember { mutableStateOf("Fretado - Ida ao Trabalho") }
    var isRecording by remember { mutableStateOf(false) }
    var waypoints by remember { mutableStateOf(mutableListOf<LatLngData>()) }

    // Grava novos pontos do GPS periodicamente durante o deslocamento do ônibus
    LaunchedEffect(isRecording, currentLat, currentLng) {
        if (isRecording && currentLat != 0.0 && currentLng != 0.0) {
            val lastWp = waypoints.lastOrNull()
            if (lastWp == null || distanceBetween(lastWp.latitude, lastWp.longitude, currentLat, currentLng) > 10) {
                waypoints.add(LatLngData(currentLat, currentLng))
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = purpleColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gravar Itinerário do Fretado",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = itineraryName,
                    onValueChange = { itineraryName = it },
                    label = { Text("Nome do Itinerário (Ex: Fretado - Ida)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isRecording) Color(0xFFFFEBEE) else Color(0xFFF3F0F8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = if (isRecording) Color.Red else purpleColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) "REC GRAVANDO PERCURSO EM TEMPO REAL..." else "PRONTO PARA GRAVAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isRecording) Color.Red else purpleColor
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Pontos de GPS capturados: ${waypoints.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isRecording = !isRecording
                            if (isRecording && currentLat != 0.0 && currentLng != 0.0) {
                                waypoints.add(LatLngData(currentLat, currentLng))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else purpleColor)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecording) "PAUSAR" else "INICIAR REC", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (waypoints.isEmpty()) {
                                Toast.makeText(context, "Inicie a gravação para registrar os pontos do percurso", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val newItinerary = Itinerary(
                                id = System.currentTimeMillis().toString(),
                                name = itineraryName.ifBlank { "Itinerário Fretado" },
                                startAddress = "Ponto Inicial",
                                endAddress = "Destino Final",
                                waypoints = waypoints,
                                dateCreated = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                            )

                            ItineraryManager.saveItinerary(context, newItinerary)
                            ItineraryManager.setActiveItineraryId(context, newItinerary.id)

                            Toast.makeText(context, "✅ Itinerário \"${newItinerary.name}\" salvo com sucesso!", Toast.LENGTH_LONG).show()
                            onItinerarySaved()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = waypoints.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SALVAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results)
    return results[0]
}
