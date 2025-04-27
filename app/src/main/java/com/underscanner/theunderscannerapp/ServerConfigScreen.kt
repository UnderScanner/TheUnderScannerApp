package com.example.theunderscannerapp

import LiDARViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    navController: NavController,
    viewModel: LiDARViewModel
) {
    // Observe the current server status and test message from the ViewModel
    val serverStatus by viewModel.serverStatus
    val serverTestMessage by viewModel.serverTestMessage

    // Local states for IP address and port input fields
    var ipAddress by remember { mutableStateOf("10.244.178.232") }
    var port by remember { mutableStateOf("5000") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen title
        Text(
            text = "Configuration du Serveur LiDAR",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Display the current connection status of the server
        ServerStatusCard(serverStatus)

        Spacer(modifier = Modifier.height(24.dp))

        // Section title for manual server configuration
        Text(
            text = "Configuration Manuelle",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input field for IP address
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Adresse IP") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input field for port
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Button to connect to the server with the entered IP and port
            Button(
                onClick = { viewModel.configureServer("$ipAddress:$port") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Connecter")
            }

            //Spacer(modifier = Modifier.width(16.dp))


        }

        Spacer(modifier = Modifier.height(32.dp))

        // Connection Test Button
        Button(
            onClick = {viewModel.testConnexion()},
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Tester Connection")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = serverTestMessage,
            style = MaterialTheme.typography.bodyLarge
        )
        /*
        // Continue button, enabled only when connected
        Button(
            onClick = { navController.navigate("lidarControl") },
            enabled = networkState is NetworkState.Connected,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Continuer")
        }
         */
    }
}