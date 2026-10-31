package com.example.alarmbus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun AdminPanelDialog(
    onDismiss: () -> Unit,
    onSettingsSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val purpleColor = Color(0xFF6750A4)
    val scrollState = rememberScrollState()

    val currentPrice = LicenseManager.getPromoPrice(context)
    val currentDays = LicenseManager.getPromoDays(context)

    var priceText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentPrice)) }
    var daysText by remember { mutableStateOf(currentDays.toString()) }

    var isLoadingStats by remember { mutableStateOf(false) }
    var statsResult by remember { mutableStateOf<LicenseManager.AdminCustomerStats?>(null) }

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
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = purpleColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Painel do Desenvolvedor",
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

                Text(
                    text = "👑 Olá, Andre3097! Configure o preço e o período de validade do acesso cobrado no app.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Valor Promocional (Ex: 1.99, 4.90, 9.90)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Período em Dias (Ex: 30, 60, 90, 180, 365)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val parsedPrice = priceText.replace(",", ".").toDoubleOrNull() ?: 9.90
                        val parsedDays = daysText.toIntOrNull() ?: 30

                        LicenseManager.saveAdminPromoSettings(context, parsedPrice, parsedDays)
                        Toast.makeText(context, "✅ Promoção atualizada: R$ $parsedPrice por $parsedDays dias!", Toast.LENGTH_LONG).show()
                        onSettingsSaved()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVAR CONFIGURAÇÃO PROMO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BOTÃO COMPARTILHAR LINK E BANNER DE DOWNLOAD DO ALARMBUS
                Button(
                    onClick = {
                        try {
                            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.promo_banner)
                            val cachePath = File(context.cacheDir, "images")
                            cachePath.mkdirs()
                            val file = File(cachePath, "AlarmBus_Banner.png")
                            val stream = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            stream.close()

                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )

                            if (contentUri != null) {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "🚌 *AlarmBus GPS* - Baixe o aplicativo e durma tranquilo nas suas viagens de ônibus:\nhttps://github.com/andrecunha-dev/AlarmBus/raw/main/AlarmBus.apk"
                                    )
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartilhar Banner e Link do AlarmBus"))
                            }
                        } catch (e: Exception) {
                            Log.e("AdminPanel", "Erro ao compartilhar banner", e)
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "🚌 *AlarmBus GPS* - Baixe o aplicativo e durma tranquilo nas suas viagens de ônibus:\nhttps://github.com/andrecunha-dev/AlarmBus/raw/main/AlarmBus.apk"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartilhar AlarmBus no WhatsApp"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📲 COMPARTILHAR LINK E BANNER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BOTÃO VER RELATÓRIO DE CADASTROS (ATIVOS E INATIVOS)
                Button(
                    onClick = {
                        isLoadingStats = true
                        coroutineScope.launch {
                            statsResult = LicenseManager.fetchAdminCustomerStats()
                            isLoadingStats = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = !isLoadingStats
                ) {
                    if (isLoadingStats) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📊 RELATÓRIO DE CADASTROS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // EXIBIÇÃO DO RELATÓRIO DE CADASTROS
                statsResult?.let { stats ->
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0F8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "📈 Resumo das Instalações & Clientes:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = purpleColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "📱 Total de Instalações Realizadas: ${stats.totalInstallations}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                Text(text = "🟢 Clientes Pagantes (Ativos): ${stats.activeCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                Text(text = "⚪ Testes Gratuitos / Cortesias: ${stats.freeTrialCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (stats.customerList.isEmpty()) {
                                Text(
                                    text = "Nenhum cliente pagante real registrado ainda. Todos os testes anteriores foram limpos.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Text(text = "Lista de Clientes Cadastrados:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                stats.customerList.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(text = "CPF: ${item.cpfCnpj} | Data: ${item.dateCreated}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Text(
                                                text = item.statusText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
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
}
