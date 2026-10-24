package com.example.alarmbus

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider

@Composable
fun MeusTrajetosDialog(
    onDismiss: () -> Unit,
    onSelectItinerary: (Itinerary, Boolean) -> Unit
) {
    val context = LocalContext.current
    val purpleColor = Color(0xFF6750A4)
    val scrollState = rememberScrollState()

    var itineraries by remember { mutableStateOf(ItineraryManager.getItineraries(context)) }
    var editingItineraryId by remember { mutableStateOf<String?>(null) }
    var newNameText by remember { mutableStateOf("") }

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
                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = purpleColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Meus Trajetos Salvos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (itineraries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nenhum trajeto salvo ainda.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ao ativar um alerta, escolha 'Sim' para gravar o percurso do seu fretado.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    itineraries.forEach { itin ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F6FA)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (editingItineraryId == itin.id) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newNameText,
                                            onValueChange = { newNameText = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                if (newNameText.isNotBlank()) {
                                                    ItineraryManager.renameItinerary(context, itin.id, newNameText)
                                                    itineraries = ItineraryManager.getItineraries(context)
                                                }
                                                editingItineraryId = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = itin.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = purpleColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Distância: ${ItineraryManager.formatDistance(itin.totalDistanceMeters)} | ${itin.waypoints.size} pontos",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = "Gravado em: ${itin.dateCreated}",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingItineraryId = itin.id
                                                    newNameText = itin.name
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = purpleColor, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val gpxFile = ItineraryManager.exportToGpx(context, itin)
                                                        val contentUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            gpxFile
                                                        )
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/gpx+xml"
                                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                                            putExtra(Intent.EXTRA_TEXT, "Trajeto Fretado: ${itin.name}")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar GPX"))
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erro ao exportar GPX", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Compartilhar GPX", tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    ItineraryManager.deleteItinerary(context, itin.id)
                                                    itineraries = ItineraryManager.getItineraries(context)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.Red, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onSelectItinerary(itin, true)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SEGUIR ROTA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            onSelectItinerary(itin, false)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("USAR NO ALERTA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
