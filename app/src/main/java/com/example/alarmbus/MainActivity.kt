package com.example.alarmbus

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

suspend fun fetchRoadRoutePoints(startLat: Double, startLng: Double, endLat: Double, endLng: Double): List<LatLng> {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
            val url = URL(urlString)
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "AlarmBusGPS/1.0")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val routes = jsonObj.getJSONArray("routes")
                if (routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")
                    val points = mutableListOf<LatLng>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lng = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        points.add(LatLng(lat, lng))
                    }
                    if (points.isNotEmpty()) return@withContext points
                }
            } else {
                Log.e("RouteHelper", "OSRM HTTP Error: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("RouteHelper", "Error fetching road route", e)
        }
        val midLat = (startLat + endLat) / 2.0
        val midLng = (startLng + endLng) / 2.0
        listOf(
            LatLng(startLat, startLng),
            LatLng(startLat, midLng),
            LatLng(midLat, midLng),
            LatLng(midLat, endLng),
            LatLng(endLat, endLng)
        )
    }
}

data class EnderecoHistorico(val endereco: String)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var toqueUri by mutableStateOf<Uri?>(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    private var alertaAtivo by mutableStateOf(false)
    private var destinoLatitude by mutableStateOf(0.0)
    private var destinoLongitude by mutableStateOf(0.0)
    private var mensagem by mutableStateOf("")
    private var enderecoPesquisado by mutableStateOf("")
    private var distanciaAlerta by mutableStateOf(500)
    private var vibracaoAtiva by mutableStateOf(true)
    private var volumeNivel by mutableStateOf(100f)

    private var historicoEnderecos by mutableStateOf(listOf<EnderecoHistorico>())
    private var distanciaRestante by mutableStateOf<Float?>(null)

    private var localAtualLat by mutableStateOf(-23.550520)
    private var localAtualLng by mutableStateOf(-46.633308)

    private var showMapPicker by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            iniciarMonitoramento()
        } else {
            mensagem = "Permissão de localização negada"
            alertaAtivo = false
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        iniciarMonitoramento()
    }

    private val pickRingtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                toqueUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mensagem = "Toque configurado com sucesso"
                salvarConfiguracoes()
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "LOCATION_UPDATE" -> {
                    val lat = intent.getDoubleExtra("latitude", 0.0)
                    val lng = intent.getDoubleExtra("longitude", 0.0)
                    if (lat != 0.0 && lng != 0.0) {
                        localAtualLat = lat
                        localAtualLng = lng
                    }

                    val dist = intent.getFloatExtra("distancia", -1f)
                    if (dist >= 0) {
                        distanciaRestante = dist
                    }
                }
                "com.example.alarmbus.ACTION_ALARM_STOPPED" -> {
                    alertaAtivo = false
                    distanciaRestante = null
                    mensagem = "Alerta desativado"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "AlarmBus_GPSNavigation_v1.0"

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        carregarConfiguracoes()
        obterUltimaLocalizacao()

        val filter = IntentFilter().apply {
            addAction("LOCATION_UPDATE")
            addAction("com.example.alarmbus.ACTION_ALARM_STOPPED")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    systemReceiver,
                    filter,
                    null,
                    Handler(Looper.getMainLooper()),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    systemReceiver,
                    filter,
                    null,
                    Handler(Looper.getMainLooper())
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao registrar receiver: ${e.message}")
        }

        setContent {
            AlarmBusAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }

    private fun obterUltimaLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    localAtualLat = loc.latitude
                    localAtualLng = loc.longitude
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                request,
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val loc = result.lastLocation ?: return
                        localAtualLat = loc.latitude
                        localAtualLng = loc.longitude
                    }
                },
                Looper.getMainLooper()
            )
        }
    }

    private fun carregarConfiguracoes() {
        destinoLatitude = AlarmPreferences.getDestLat(this)
        destinoLongitude = AlarmPreferences.getDestLng(this)
        enderecoPesquisado = AlarmPreferences.getDestAddress(this)
        distanciaAlerta = AlarmPreferences.getAlertDist(this)
        vibracaoAtiva = AlarmPreferences.isVibrationActive(this)
        volumeNivel = AlarmPreferences.getVolumeLevel(this)
        toqueUri = AlarmPreferences.getToneUri(this) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        alertaAtivo = AlarmPreferences.isAlarmActive(this)

        if (destinoLatitude != 0.0 && enderecoPesquisado.isNotBlank()) {
            mensagem = "Local selecionado: $enderecoPesquisado"
        }

        val hist = AlarmPreferences.getHistorico(this)
        historicoEnderecos = hist.map { EnderecoHistorico(it) }
    }

    private fun salvarConfiguracoes() {
        AlarmPreferences.saveAlarmConfig(
            context = this,
            lat = destinoLatitude,
            lng = destinoLongitude,
            address = enderecoPesquisado,
            dist = distanciaAlerta,
            vibration = vibracaoAtiva,
            volLevel = volumeNivel,
            toneUri = toqueUri,
            active = alertaAtivo
        )
    }

    @Composable
    private fun AlarmBusAppTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF6750A4),
                onPrimary = Color.White,
                surface = Color.White,
                onSurface = Color.Black,
                background = Color(0xFFF8F6FA)
            ),
            content = content
        )
    }

    @Composable
    private fun AppContent() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        val purpleColor = Color(0xFF6750A4)
        val lightPurple = Color(0xFFF3F0F8)

        var isMapInteracting by remember { mutableStateOf(false) }
        var isSatelliteMap by remember { mutableStateOf(false) }

        if (showMapPicker) {
            MapPickerDialog(
                initialLat = if (destinoLatitude != 0.0) destinoLatitude else localAtualLat,
                initialLng = if (destinoLongitude != 0.0) destinoLongitude else localAtualLng,
                onDismiss = { showMapPicker = false },
                onLocationSelected = { selectedLat, selectedLng, selectedAddress ->
                    destinoLatitude = selectedLat
                    destinoLongitude = selectedLng
                    enderecoPesquisado = selectedAddress
                    mensagem = "Local selecionado: $selectedAddress"
                    salvarNoHistorico(selectedAddress)
                    salvarConfiguracoes()
                    showMapPicker = false
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F6FA))
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = !isMapInteracting)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2. Card "Durma Tranquilo" com Imagem de Fundo
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.banner_viagem),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 60.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Durma Tranquilo",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A237E)
                            )
                            Text(
                                text = "Eu aviso quando estiver chegando.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A237E).copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Busca de Destino
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = purpleColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (enderecoPesquisado.isEmpty()) {
                                Text(
                                    text = "Digite ou escolha o destino",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = enderecoPesquisado,
                                onValueChange = {
                                    enderecoPesquisado = it
                                    salvarConfiguracoes()
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = {
                                        if (enderecoPesquisado.isNotBlank()) buscarEndereco(enderecoPesquisado)
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (enderecoPesquisado.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    enderecoPesquisado = ""
                                    destinoLatitude = 0.0
                                    destinoLongitude = 0.0
                                    mensagem = ""
                                    distanciaRestante = null
                                    salvarConfiguracoes()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar endereço",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        // Botão ENTER / CONFIRMAR BUSCA no campo de texto
                        IconButton(
                            onClick = {
                                if (enderecoPesquisado.isNotBlank()) buscarEndereco(enderecoPesquisado)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Buscar e Confirmar Endereço",
                                tint = purpleColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botão de Ação Rápida: "Escolher no Google Maps"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = lightPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMapPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = purpleColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Escolher no Google Maps", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = purpleColor)
                    }
                }

                // 5. Últimas localizações (Card unificado)
                if (historicoEnderecos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Últimas localizações",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        color = purpleColor,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            historicoEnderecos.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            enderecoPesquisado = item.endereco
                                            buscarEndereco(item.endereco)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.endereco,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (index < historicoEnderecos.size - 1) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7. Cálculo de Distância e Tempo Reais por Trajeto Viário (~1.35x distância em linha reta)
                val (distanciaTexto, tempoTexto) = remember(localAtualLat, localAtualLng, destinoLatitude, destinoLongitude) {
                    if (destinoLatitude != 0.0 && destinoLongitude != 0.0) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            localAtualLat, localAtualLng,
                            destinoLatitude, destinoLongitude,
                            results
                        )
                        val straightMeters = results[0]
                        val roadMeters = straightMeters * 1.35f

                        val distStr = if (roadMeters >= 1000) {
                            String.format(java.util.Locale.getDefault(), "%.1f km", roadMeters / 1000f)
                        } else {
                            "${roadMeters.toInt()} m"
                        }
                        val timeMin = (roadMeters / 1000f / 35f * 60f).roundToInt().coerceAtLeast(1)
                        val timeStr = "≈ $timeMin min"
                        Pair(distStr, timeStr)
                    } else {
                        Pair("18,0 km", "≈ 25 min")
                    }
                }

                // Google Map Nativo Oficial mostrando a Localização Atual do Usuário e Destino
                val targetLat = if (destinoLatitude != 0.0) destinoLatitude else localAtualLat + 0.012
                val targetLng = if (destinoLongitude != 0.0) destinoLongitude else localAtualLng + 0.012

                val currentLatLng = remember(localAtualLat, localAtualLng) {
                    LatLng(localAtualLat, localAtualLng)
                }
                val destLatLng = remember(targetLat, targetLng) {
                    LatLng(targetLat, targetLng)
                }

                // Pontos do trajeto viário real das ruas obtidos via OSRM Directions Routing API
                var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

                LaunchedEffect(currentLatLng, destLatLng, destinoLatitude, destinoLongitude) {
                    if (destinoLatitude != 0.0 && destinoLongitude != 0.0) {
                        val fetched = fetchRoadRoutePoints(
                            currentLatLng.latitude, currentLatLng.longitude,
                            destLatLng.latitude, destLatLng.longitude
                        )
                        routePoints = fetched
                    } else {
                        routePoints = listOf(currentLatLng, destLatLng)
                    }
                }

                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
                }

                LaunchedEffect(destinoLatitude, destinoLongitude) {
                    val target = if (destinoLatitude != 0.0) destLatLng else currentLatLng
                    val initialZoom = if (destinoLatitude != 0.0) 14f else 15f
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(target, initialZoom)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    isMapInteracting = event.changes.any { it.pressed }
                                }
                            }
                        }
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = false,
                            mapType = if (isSatelliteMap) MapType.HYBRID else MapType.NORMAL
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false,
                            mapToolbarEnabled = false
                        )
                    ) {
                        // Marcador da Origem (Sua Localização)
                        Marker(
                            state = MarkerState(position = currentLatLng),
                            title = "Sua Localização"
                        )

                        // Marcador do Destino
                        Marker(
                            state = MarkerState(position = destLatLng),
                            title = if (enderecoPesquisado.isNotBlank()) enderecoPesquisado else "Destino"
                        )

                        // Trajeto Viário pelas Ruas (Linha Azul)
                        Polyline(
                            points = routePoints,
                            color = Color(0xFF1976D2),
                            width = 10f
                        )

                        // Círculo com o Raio do Alerta Real em Metros (300m, 500m, 750m, 1000m)
                        Circle(
                            center = destLatLng,
                            radius = distanciaAlerta.toDouble(),
                            fillColor = Color(0x336750A4),
                            strokeColor = Color(0xFF6750A4),
                            strokeWidth = 4f
                        )
                    }

                    // Parte Superior sobre o Mapa: Card "Destino Selecionado" + Chips Reais
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (destinoLatitude != 0.0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Destino Selecionado", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                        Text(
                                            text = if (enderecoPesquisado.isNotBlank()) enderecoPesquisado else mensagem,
                                            fontSize = 11.sp,
                                            color = Color.DarkGray,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Chips de Valores Reais sobre o Mapa (Topo Esquerdo)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = purpleColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = distanciaTexto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = purpleColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = tempoTexto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }

                    // Botão para Alternar entre Mapa Padrão e Satélite (Canto inferior esquerdo)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSatelliteMap) purpleColor else Color.White.copy(alpha = 0.95f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clickable { isSatelliteMap = !isSatelliteMap }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Tipo de mapa",
                                tint = if (isSatelliteMap) Color.White else purpleColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSatelliteMap) "Satélite" else "Padrão",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSatelliteMap) Color.White else Color.Black
                            )
                        }
                    }

                    // Etiqueta do Raio do Alarme ("Alarme em 500 m") no Mapa (Canto inferior direito)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = purpleColor,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Alarme em ${if (distanciaAlerta >= 1000) "1 km" else "$distanciaAlerta m"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8. Seleção de Distância: "Quando devo avisar?"
                Text(
                    text = "Quando devo avisar?",
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    color = purpleColor,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(300, 500, 750, 1000).forEach { dist ->
                        val isSelected = distanciaAlerta == dist
                        Button(
                            onClick = {
                                distanciaAlerta = dist
                                salvarConfiguracoes()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) purpleColor else Color.White,
                                contentColor = if (isSelected) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                        ) {
                            Text(
                                text = if (dist >= 1000) "1 km" else "$dist m",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 9. Configurações de Som, Vibração, Volume Máximo e Slider
                Text(
                    text = "Como deseja ser avisado?",
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    color = purpleColor,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        // Som
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { escolherToque() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Som do Alarme", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "Escolher toque de alarme", fontSize = 11.sp, color = Color.Gray)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))

                        // Vibração
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Vibração", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = vibracaoAtiva,
                                onCheckedChange = {
                                    vibracaoAtiva = it
                                    salvarConfiguracoes()
                                }
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))

                        // Slider do Volume
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Nível do Volume do Alarme", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "${volumeNivel.roundToInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = purpleColor,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = volumeNivel,
                                onValueChange = { newVol ->
                                    volumeNivel = newVol
                                    salvarConfiguracoes()
                                },
                                valueRange = 0f..100f,
                                steps = 9
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 10. Botão "Ativar alerta" / "Desativar alerta"
                Button(
                    onClick = {
                        if (destinoLatitude == 0.0 || destinoLongitude == 0.0) {
                            Toast.makeText(context, "Busque e confirme um destino primeiro", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            return@Button
                        }

                        alertaAtivo = !alertaAtivo
                        salvarConfiguracoes()

                        if (alertaAtivo) {
                            iniciarMonitoramento()
                        } else {
                            pararMonitoramento()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alertaAtivo) Color(0xFFB3261E) else purpleColor
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (alertaAtivo) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (alertaAtivo) "Desativar alerta" else "Ativar alerta",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (alertaAtivo && distanciaRestante != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Distância aproximada: ${distanciaRestante?.toInt()}m",
                        color = purpleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 12. Aviso inferior do GPS
                Text(
                    text = "Mantenha o GPS ligado e permita o funcionamento em segundo plano.",
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    private fun preencherComLocalizacaoAtual() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    destinoLatitude = location.latitude
                    destinoLongitude = location.longitude
                    localAtualLat = location.latitude
                    localAtualLng = location.longitude
                    val geocoder = Geocoder(this)
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            enderecoPesquisado = addresses[0].getAddressLine(0)
                            mensagem = "Localização Atual: $enderecoPesquisado"
                        } else {
                            enderecoPesquisado = "Minha Localização Atual"
                            mensagem = "Coordenadas: ${location.latitude}, ${location.longitude}"
                        }
                        salvarNoHistorico(enderecoPesquisado)
                        salvarConfiguracoes()
                    } catch (e: Exception) {
                        enderecoPesquisado = "Minha Localização Atual"
                        mensagem = "Coordenadas obtidas com sucesso"
                        salvarConfiguracoes()
                    }
                } else {
                    Toast.makeText(this, "Não foi possível obter localização atual", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun buscarEndereco(endereco: String) {
        val geocoder = Geocoder(this)
        try {
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocationName(endereco, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                destinoLatitude = address.latitude
                destinoLongitude = address.longitude
                mensagem = "Local definido: ${address.getAddressLine(0)}"
                salvarNoHistorico(endereco)
                salvarConfiguracoes()
            } else {
                mensagem = "Endereço não encontrado"
                destinoLatitude = 0.0
                destinoLongitude = 0.0
            }
        } catch (e: IOException) {
            mensagem = "Erro ao buscar endereço"
            Log.e("MainActivity", "Geocoder error", e)
        }
    }

    private fun iniciarMonitoramento() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                try {
                    salvarConfiguracoes()

                    val serviceIntent = Intent(this, LocationForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    alertaAtivo = true
                    mensagem = "Monitoramento ativado"
                } catch (e: Exception) {
                    mensagem = "Erro ao iniciar serviço de monitoramento"
                    Log.e("MainActivity", "Service start error", e)
                }
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                mensagem = "Permissões de localização necessárias"
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun pararMonitoramento() {
        try {
            AlarmPlayer.stopAlarm(this)

            val serviceIntent = Intent(this, LocationForegroundService::class.java)
            stopService(serviceIntent)

            alertaAtivo = false
            mensagem = "Monitoramento desativado"
            distanciaRestante = null
            salvarConfiguracoes()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping location updates", e)
        }
    }

    private fun escolherToque() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Selecione o toque do alarme")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, toqueUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            pickRingtoneLauncher.launch(intent)
        } catch (e: Exception) {
            mensagem = "Não foi possível abrir seletor de toques"
            Log.e("MainActivity", "Ringtone picker error", e)
        }
    }

    private fun salvarNoHistorico(endereco: String) {
        val novoLista = historicoEnderecos.map { it.endereco }.toMutableList()
        novoLista.removeAll { it.equals(endereco, ignoreCase = true) }
        novoLista.add(0, endereco)
        if (novoLista.size > 3) novoLista.removeAt(3)

        historicoEnderecos = novoLista.map { EnderecoHistorico(it) }
        AlarmPreferences.saveHistorico(this, novoLista)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(systemReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Receiver já não estava registrado")
        }
    }
}

/**
 * Seletor de Destino em Mapa Interativo (Dialog em Tela Cheia).
 * Permite que o usuário toque em qualquer ponto do mapa para marcar e confirmar seu destino exato.
 */
@Composable
fun MapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onLocationSelected: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    val purpleColor = Color(0xFF6750A4)

    var pickedLat by remember { mutableStateOf(initialLat) }
    var pickedLng by remember { mutableStateOf(initialLng) }
    var pickedAddress by remember { mutableStateOf("Carregando endereço...") }
    var isPickerSatellite by remember { mutableStateOf(false) }

    LaunchedEffect(pickedLat, pickedLng) {
        val geocoder = Geocoder(context)
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(pickedLat, pickedLng, 1)
            if (!addresses.isNullOrEmpty()) {
                pickedAddress = addresses[0].getAddressLine(0) ?: "Local sem nome"
            } else {
                pickedAddress = String.format(java.util.Locale.getDefault(), "Lat: %.5f, Lng: %.5f", pickedLat, pickedLng)
            }
        } catch (e: Exception) {
            pickedAddress = String.format(java.util.Locale.getDefault(), "Coordenadas: %.5f, %.5f", pickedLat, pickedLng)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 16f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TopBar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(purpleColor)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Escolher Destino no Google Maps",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Banner de Instrução
                Surface(
                    color = purpleColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "👉 Toque em qualquer ponto do mapa para marcar o destino",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = purpleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }

                // Google Map
                Box(modifier = Modifier.weight(1f)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapType = if (isPickerSatellite) MapType.HYBRID else MapType.NORMAL
                        ),
                        onMapClick = { latLng ->
                            pickedLat = latLng.latitude
                            pickedLng = latLng.longitude
                        },
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            myLocationButtonEnabled = true
                        )
                    ) {
                        Marker(
                            state = MarkerState(position = LatLng(pickedLat, pickedLng)),
                            title = "Ponto Selecionado",
                            snippet = pickedAddress
                        )
                    }

                    // Botão Alternar Satélite no Seletor de Mapa
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPickerSatellite) purpleColor else Color.White.copy(alpha = 0.95f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable { isPickerSatellite = !isPickerSatellite }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Tipo de mapa",
                                tint = if (isPickerSatellite) Color.White else purpleColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPickerSatellite) "Satélite" else "Padrão",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPickerSatellite) Color.White else Color.Black
                            )
                        }
                    }
                }

                // Card Inferior de Confirmação
                Card(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Endereço do Ponto:",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pickedAddress,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                onLocationSelected(pickedLat, pickedLng, pickedAddress)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text(
                                text = "CONFIRMAR ESTE DESTINO",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
