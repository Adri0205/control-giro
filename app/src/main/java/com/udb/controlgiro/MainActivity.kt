package com.udb.controlgiro

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.udb.controlgiro.ui.theme.AzulCielo
import com.udb.controlgiro.ui.theme.ControlGiroTheme
import com.udb.controlgiro.ui.theme.Durazno
import com.udb.controlgiro.ui.theme.FondoCrema
import com.udb.controlgiro.ui.theme.Lavanda
import com.udb.controlgiro.ui.theme.Menta
import com.udb.controlgiro.ui.theme.RojoError
import com.udb.controlgiro.ui.theme.RosaSuave
import com.udb.controlgiro.ui.theme.TextoGris
import com.udb.controlgiro.ui.theme.TextoOscuro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlGiroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Pantalla()
                }
            }
        }
    }
}

private fun zonaDe(d: Double): String = when {
    d in 100.0..200.0 -> "Zona 100–200 cm → derecha"
    d > 200.0 && d <= 300.0 -> "Zona 200–300 cm → izquierda"
    else -> "Fuera de rango → detenido"
}

private fun motorTexto(m: String): String = when (m) {
    "right" -> "Girando a la derecha"
    "left" -> "Girando a la izquierda"
    else -> "Detenido"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla() {
    val ctx = LocalContext.current
    var modoConexion by remember { mutableIntStateOf(0) } // 0: WiFi, 1: Bluetooth
    var estado by remember { mutableStateOf<Estado?>(null) }
    var conectadoWifi by remember { mutableStateOf(false) }
    var conectadoBt by remember { mutableStateOf(false) }
    var dispositivoNombre by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var mostrarDialogoBt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val estaConectado = if (modoConexion == 0) conectadoWifi else conectadoBt

    // Solicitar permiso BLUETOOTH_CONNECT si es necesario (Android 12+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            mostrarDialogoBt = true
        } else {
            mensaje = "Se requiere el permiso de Bluetooth para conectar."
        }
    }

    fun abrirDialogoBt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }
        mostrarDialogoBt = true
    }

    suspend fun conectarBtDevice(device: BluetoothDevice) {
        @SuppressLint("MissingPermission")
        val nombre = device.name ?: device.address
        dispositivoNombre = nombre
        mensaje = "Conectando a $nombre..."
        try {
            Bt.conectar(device)
            conectadoBt = true
            mensaje = ""
            scope.launch {
                Bt.leerLineas { linea ->
                    Bt.parsear(linea)?.let { nuevoEstado ->
                        estado = nuevoEstado
                    }
                }
                conectadoBt = false
            }
        } catch (e: Exception) {
            conectadoBt = false
            mensaje = "Error al conectar por Bluetooth: ${e.message ?: "fallo de conexión"}"
        }
    }

    // Lectura continua del sensor vía WiFi (consulta /api/status cada 500 ms)
    LaunchedEffect(modoConexion) {
        if (modoConexion == 0) {
            while (true) {
                try {
                    estado = Api.estado()
                    conectadoWifi = true
                    mensaje = ""
                } catch (e: Exception) {
                    conectadoWifi = false
                    mensaje = "Sin conexión con la API WiFi"
                }
                delay(500)
            }
        } else {
            conectadoWifi = false
        }
    }

    fun accionWifi(bloque: suspend () -> Estado) {
        scope.launch {
            try {
                estado = bloque()
                mensaje = ""
            } catch (e: Exception) {
                mensaje = e.message ?: "Error"
            }
        }
    }

    val auto = estado?.mode == "auto"
    val resId = remember {
        val idLogo = ctx.resources.getIdentifier("logo", "drawable", ctx.packageName)
        if (idLogo != 0) idLogo else ctx.resources.getIdentifier("escudo_barcelona", "drawable", ctx.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = "Logo de la aplicación",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Text(
            text = "Control de giro",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextoOscuro
        )

        // Selector WiFi / Bluetooth
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val opciones = listOf("WiFi", "Bluetooth")
            opciones.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = modoConexion == index,
                    onClick = {
                        if (modoConexion != index) {
                            if (modoConexion == 1) {
                                Bt.desconectar()
                                conectadoBt = false
                            }
                            modoConexion = index
                            mensaje = ""
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = opciones.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Lavanda,
                        activeContentColor = TextoOscuro,
                        inactiveContainerColor = FondoCrema,
                        inactiveContentColor = TextoOscuro
                    )
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Indicador de conexión y botón de selección para Bluetooth
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (estaConectado) Menta.copy(alpha = 0.5f) else RosaSuave.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val textoEstado = if (modoConexion == 0) {
                        if (conectadoWifi) "● Conectado" else "○ Desconectado"
                    } else {
                        if (conectadoBt) "● Conectado a $dispositivoNombre" else "○ Desconectado"
                    }
                    Text(
                        text = textoEstado,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (estaConectado) TextoOscuro else TextoGris
                    )
                }
            }

            if (modoConexion == 1) {
                Button(
                    onClick = { abrirDialogoBt() },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AzulCielo,
                        contentColor = TextoOscuro
                    )
                ) {
                    Text("Elegir dispositivo Bluetooth", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Lectura del sensor (Tarjeta del sensor)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AzulCielo),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sensor ultrasónico",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoOscuro.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = estado?.let { "%.0f cm".format(it.distance) } ?: "-- cm",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoOscuro
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = estado?.let { zonaDe(it.distance) } ?: "Sin lectura",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoOscuro
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Motor: ${estado?.let { motorTexto(it.motor) } ?: "--"}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextoOscuro
                )
            }
        }

        // Ceder el mando al sensor (Tarjeta del modo automático)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Durazno),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mando del sensor (automático)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextoOscuro,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = auto,
                    enabled = estaConectado,
                    onCheckedChange = { nuevoAuto ->
                        if (modoConexion == 0) {
                            accionWifi { Api.setModo(nuevoAuto) }
                        } else {
                            scope.launch {
                                Bt.enviar(if (nuevoAuto) "AUTO" else "MANUAL")
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Lavanda,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.6f)
                    )
                )
            }
        }

        // Botones manuales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (modoConexion == 0) {
                        accionWifi { Api.mover("left") }
                    } else {
                        scope.launch { Bt.enviar("LEFT") }
                    }
                },
                enabled = estaConectado && !auto,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Lavanda,
                    contentColor = TextoOscuro,
                    disabledContainerColor = Lavanda.copy(alpha = 0.4f),
                    disabledContentColor = TextoOscuro.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("◀ Izq.", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    if (modoConexion == 0) {
                        accionWifi { Api.mover("stop") }
                    } else {
                        scope.launch { Bt.enviar("STOP") }
                    }
                },
                enabled = estaConectado && !auto,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RosaSuave,
                    contentColor = TextoOscuro,
                    disabledContainerColor = RosaSuave.copy(alpha = 0.4f),
                    disabledContentColor = TextoOscuro.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("■ Stop", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    if (modoConexion == 0) {
                        accionWifi { Api.mover("right") }
                    } else {
                        scope.launch { Bt.enviar("RIGHT") }
                    }
                },
                enabled = estaConectado && !auto,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Menta,
                    contentColor = TextoOscuro,
                    disabledContainerColor = Menta.copy(alpha = 0.4f),
                    disabledContentColor = TextoOscuro.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("Der. ▶", fontWeight = FontWeight.Bold)
            }
        }

        if (auto) {
            Text(
                text = "Botones bloqueados: el sensor tiene el mando.",
                fontSize = 13.sp,
                color = TextoGris
            )
        }

        if (mensaje.isNotEmpty()) {
            Text(
                text = mensaje,
                color = RojoError,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Diálogo para seleccionar dispositivo Bluetooth
    if (mostrarDialogoBt) {
        val dispositivos = remember(ctx) { Bt.emparejados(ctx) }
        AlertDialog(
            onDismissRequest = { mostrarDialogoBt = false },
            title = {
                Text("Dispositivos emparejados", fontWeight = FontWeight.Bold, color = TextoOscuro)
            },
            text = {
                if (dispositivos.isEmpty()) {
                    Text(
                        "No se encontraron dispositivos emparejados. Asegúrate de emparejar el dispositivo Bluetooth en los Ajustes del teléfono.",
                        color = TextoOscuro
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dispositivos.forEach { device ->
                            @SuppressLint("MissingPermission")
                            val nombre = device.name ?: device.address
                            Surface(
                                onClick = {
                                    mostrarDialogoBt = false
                                    scope.launch {
                                        conectarBtDevice(device)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = AzulCielo,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = nombre,
                                    modifier = Modifier.padding(14.dp),
                                    fontWeight = FontWeight.Medium,
                                    color = TextoOscuro
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarDialogoBt = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Lavanda, contentColor = TextoOscuro)
                ) {
                    Text("Cerrar")
                }
            },
            containerColor = FondoCrema
        )
    }
}
