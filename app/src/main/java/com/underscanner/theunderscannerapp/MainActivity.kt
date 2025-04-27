package com.example.theunderscannerapp


import LiDARViewModel
import ServerStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.theunderscannerapp.ui.theme.TheUnderScannerAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    // onCreate is called when the activity is first created.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view of the activity
        setContent {
            // Apply the app's custom Material theme
            TheUnderScannerAppTheme {
                // Create a surface container that fills the entire screen
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Load the main UI screen
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LiDARViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    // Navigation controller to handle screen navigation
    val navController = rememberNavController()
    // State to control the visibility of the dropdown menu
    val showMenu = remember { mutableStateOf(false) }
    // Get the current context (useful for operations needing a context reference)
    val context = LocalContext.current

    // Scaffold provides the basic visual layout structure (TopBar + content area)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UnderScanner") }, // App title
                actions = {
                    // Menu icon button to open/close dropdown menu
                    IconButton(onClick = { showMenu.value = !showMenu.value }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    // Dropdown menu appearing when menu button is clicked
                    DropdownMenu(
                        expanded = showMenu.value,
                        onDismissRequest = { showMenu.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Accueil") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            onClick = {
                                // Navigate to the home screen and clear the backstack to home
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                                showMenu.value = false
                            }
                        )
                        /*
                        // Example of another dropdown menu item (currently commented out)
                        DropdownMenuItem(
                          text = { Text("Configuration Serveur") },
                          leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                          onClick = {
                                navController.navigate("serverConfig")
                                showMenu.value = false
                            }
                        )
                        */
                    }
                }
            )
        }
    ) { paddingValues ->
        // Set up navigation between different composable screens
        NavHost(
            navController = navController,
            startDestination = "home", // Default starting screen
            modifier = Modifier.padding(paddingValues) // Apply padding from the Scaffold
        ) {
            composable("home") {
                HomeScreen(navController)
            }
            composable("serverConfig") { // Server Configuration Screen
                ServerConfigScreen(navController, viewModel)
            }
            composable(
                route = "pcdViewer/{fileName}",
                arguments = listOf(navArgument("fileName") { type = NavType.StringType })
            ) { backStackEntry ->
                // Retrieve the 'fileName' argument passed during navigation
                val fileName = backStackEntry.arguments?.getString("fileName") ?: "scan1.pcd"
                PCDViewerScreen(fileName)
            }
            composable("pcdFileBrowser") {
                PCDFileBrowserScreen(navController)
            }
            composable("lidarControl") {
                LiDARControlScreen(viewModel)
            }
        }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController) {
    // Create a box that fills the screen and centers its content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Arrange items vertically with spacing
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Title
            Text(
                text = "UnderScanner",
                style = MaterialTheme.typography.headlineLarge
            )

            // Button to navigate to the server configuration screen
            Button(
                onClick = { navController.navigate("serverConfig") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp)) // Space between icon and text
                Text("Configuration du Serveur")
            }

            // Button to navigate to the LiDAR control screen
            Button(
                onClick = { navController.navigate("lidarControl") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
            ) {
                Icon(Icons.Default.Scanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Contrôle du LiDAR")
            }

            // Button to navigate to the point cloud file browser
            Button(
                onClick = { navController.navigate("pcdFileBrowser") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Visualiseur de nuages de points")
            }
        }
    }
}

@Composable
fun PCDFileBrowserScreen(
    navController: NavController,
    viewModel: LiDARViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // Context reference to access device storage
    val context = LocalContext.current
    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // List both remote and local files
    var scanList by remember { mutableStateOf<List<ScanInfo>>(emptyList()) }
    var localFilesList by remember { mutableStateOf<List<String>>(emptyList()) }

    // Tab selection state (0 = Local files, 1 = Remote files)
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Fichiers Locaux", "Fichiers Distants")

    // Load local files from internal storage "Scans" folder
    LaunchedEffect(Unit) {
        try {
            val scansDir = context.getExternalFilesDir("Scans") ?: context.filesDir
            localFilesList = if (scansDir.exists()) {
                scansDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".pcd") }
                    ?.map { it.name }  // Map back to just file names
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            localFilesList = emptyList()
        }
    }

    // Load list of remote files from server
    LaunchedEffect(Unit) {
        loadRemoteFiles(viewModel) { loading, scans, error ->
            isLoading = loading
            scanList = scans ?: emptyList()
            errorMessage = error
        }
    }
    // Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Fichiers PCD",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Tab selector between Local and Remote files
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show either remote or local files based on tab selection
        when (selectedTab) {
            0 -> LocalFilesTab(localFilesList, navController)
            1 -> RemoteFilesTab(scanList, isLoading, errorMessage, viewModel, navController)
        }
    }
}

@Composable
fun RemoteFilesTab(
    scanList: List<ScanInfo>,
    isLoading: Boolean,
    errorMessage: String?,
    viewModel: LiDARViewModel,
    navController: NavController
) {
    val context = LocalContext.current

    // Main container for the tab
    Box(modifier = Modifier.fillMaxSize()) {
        // Show a loading spinner when files are being fetched
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (errorMessage != null) {
            // Show error message with retry button
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Erreur: $errorMessage",
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    // Retry loading remote files
                    viewModel.viewModelScope.launch {
                        loadRemoteFiles(viewModel) { _, _, _ -> }
                    }
                }) {
                    Text("Réessayer")
                }
            }
        } else if (scanList.isEmpty()) {
            // If no files are found on the server
            Text(
                text = "Aucun fichier PCD sur le serveur",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // Display the list of remote files
            LazyColumn {
                items(scanList) { scan ->
                    // State for each file download
                    var downloadProgress by remember { mutableStateOf<Float?>(null) }
                    var isDownloading by remember { mutableStateOf(false) }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            // Start download process
                            if (!isDownloading) {
                                isDownloading = true
                                downloadProgress = 0f

                                // Prepare download destination
                                val downloadDir = context.getExternalFilesDir("Scans")
                                    ?: context.filesDir
                                if (!downloadDir.exists()) downloadDir.mkdirs()

                                val destination = File(downloadDir, scan.name)

                                // Launch download in background
                                viewModel.viewModelScope.launch(Dispatchers.IO) {
                                    viewModel.client.downloadScanWithProgress(
                                        scanName = scan.name,
                                        destination = destination,
                                        progress = { progress ->
                                            downloadProgress = progress
                                        }
                                    ).fold(
                                        onSuccess = {
                                            /*
                                            // Navigate to viewer once download is complete
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                downloadProgress = null
                                                // Now navigate to the viewer with the downloaded file
                                                val fileUri = Uri.fromFile(destination).toString()
                                                navController.navigate("pcdViewer/${destination.name}")
                                            }
                                            */
                                        },
                                        onFailure = { error ->
                                            // Handle download error on UI thread
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                downloadProgress = null
                                                // Show error toast
                                                Toast.makeText(
                                                    context,
                                                    "Erreur: ${error.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        // File card layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // File name and size info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(scan.name)
                                    Text(
                                        text = "Taille: ${formatFileSize(scan.size)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // Show download progress if downloading
                            if (isDownloading && downloadProgress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress!! },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Téléchargement: ${(downloadProgress!! * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalFilesTab(
    files: List<String>, // List of local file names (PCD files)
    navController: NavController
) {
    // Case when no local files are found
    if (files.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Aucun fichier PCD local")
        }
    } else {
        // Display the list of local files
        LazyColumn {
            items(files) { file ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), // Space between cards
                    onClick = {
                        // Navigate to the PCD Viewer screen when a file is clicked
                        navController.navigate("pcdViewer/$file")
                    }
                ) {
                    // Row layout inside each card to display file information
                    Row(
                        modifier = Modifier
                            .fillMaxWidth() // Ensure the row fills width of the card
                            .padding(16.dp), // Padding inside the card
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon representing a file (Insert Drive File icon)
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))// Space between icon and text
                        Text(file) // Text showing the file name
                    }
                }
            }
        }
    }
}

// Helper function to load remote files
private suspend fun loadRemoteFiles(
    viewModel: LiDARViewModel, // The ViewModel that handles the LiDAR data
    callback: (loading: Boolean, scans: List<ScanInfo>?, error: String?) -> Unit // Callback to handle the loading state, list of scans, and errors
) {
    // Indicate that loading has started
    callback(true, null, null)
    // Attempt to fetch the list of scans from the server using the viewModel's client
    viewModel.client.listScans().fold(
        onSuccess = { scans ->
            // Pass the scans to the callback if successfully retrieved
            callback(false, scans, null)
        },
        onFailure = { error ->
            callback(false, null, error.message ?: "Error loading files")
        }
    )
}


@Composable
fun PCDViewerScreen(fileName: String = "scan1.pcd") {
    val context = LocalContext.current

    // State variables for showing and managing the point count display
    var showPointCount by remember { mutableStateOf(true) }
    var pointCount by remember { mutableStateOf(0) }

    // LaunchedEffect that hides the point count after 3 seconds
    LaunchedEffect(Unit) {
        delay(3000)
        showPointCount = false
    }

    // Main container Box for holding the OpenGL view and point count overlay
    Box(modifier = Modifier.fillMaxSize()) {
        // AndroidView composable to integrate the custom OpenGL surface view (MyGLSurfaceView)
        AndroidView(
            factory = { ctx ->
                // Initialize MyGLSurfaceView with the context and the file name
                val glView = MyGLSurfaceView(ctx, fileName)

                // Retrieve the point count after the GL surface is initialized
                pointCount = glView.getPointCount()

                glView // Return the GL surface view
            },
            modifier = Modifier.fillMaxSize() // Make the GL view fill the entire screen
        )

        // AnimatedVisibility composable to show the point count for a brief period
        AnimatedVisibility(
            visible = showPointCount, // Display when showPointCount is true
            enter = fadeIn(), // Fade in effect
            exit = fadeOut(), // Fade out effect after 3 seconds
            modifier = Modifier
                .padding(16.dp) // Add padding to the point count display
                .align(Alignment.TopStart) // Position it at the top left
        ) {
            // A rounded surface to display the point count with some styling
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), // Semi-transparent surface color
                shape = RoundedCornerShape(8.dp) // Rounded corners for the background
            ) {
                // Text showing the current point count
                Text(
                    text = "$pointCount points",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelMedium // Style the text with the appropriate typography
                )
            }
        }
    }
}

@Composable
fun LiDARControlScreen(viewModel: LiDARViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    // Observe scan state and server status from the ViewModel
    val scanState by viewModel.scanState
    val serverStatus by viewModel.serverStatus

    // Set up a DisposableEffect to start and stop polling server status when the Composable is created and destroyed
    DisposableEffect(Unit) {
        viewModel.startStatusPolling()
        onDispose {
            viewModel.stopStatusPolling()
        }
    }

    // Main Column layout for LiDAR control UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Contrôle du LiDAR",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Display server status using the ServerStatusCard
        ServerStatusCard(serverStatus)

        Spacer(modifier = Modifier.height(24.dp))

        // Conditional rendering based on the current scan state
        when (scanState) {
            is ScanState.Idle -> {
                // If the scan is idle, show the "Start Scan" button
                Button(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Démarrer le scan")
                }
            }
            is ScanState.Scanning -> {
                // If a scan is in progress, show the "Stop Scan" button
                Button(
                    onClick = { viewModel.stopScan() },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Arrêter le scan")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Scan en cours: ${(scanState as ScanState.Scanning).scanName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is ScanState.Processing -> {
                // If the scan is being processed, show a progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Traitement en cours...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is ScanState.Error -> {
                Text(
                    text = "Erreur: ${(scanState as ScanState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = { viewModel.resetError() },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text("Réessayer")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // real-time visualization during the scan (placeholder for now)
        if (scanState is ScanState.Scanning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Visualisation en temps réel du scan")
                    // *NOTE*:
                    // Ici, intégrer une vue qui reçoit des mises à jour
                    // via WebSocket ou des requêtes périodiques
                }
            }
        }
    }
}

@Composable
fun ServerStatusCard(status: ServerStatus) {
    // Create a card for displaying server status where ever in the UI
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Row for showing the server connection status with a colored circle and text
            Row(
                verticalAlignment = Alignment.CenterVertically // Align items vertically in the center
            ) {
                // Box used for showing a small colored circle indicating the connection status
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (status) {
                                is ServerStatus.Connected -> MaterialTheme.colorScheme.primary
                                is ServerStatus.Disconnected -> MaterialTheme.colorScheme.error
                                is ServerStatus.Loading -> MaterialTheme.colorScheme.tertiary
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp)) // Add space between circle and text
                Text(
                    text = when (status) {
                        is ServerStatus.Connected -> "Connecté"
                        is ServerStatus.Disconnected -> "Déconnecté"
                        is ServerStatus.Loading -> "Connexion..."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // If the server is connected, display additional info about disk space
            if (status is ServerStatus.Connected) {
                Spacer(modifier = Modifier.height(8.dp))
                // Linear progress bar to visualize disk space usage
                LinearProgressIndicator(
                    progress = { status.diskSpaceUsedPercent / 100f }, // Set progress based on disk usage percentage
                    modifier = Modifier.fillMaxWidth()
                )
                // Text displaying the amount of free disk space available
                Text(
                    text = "Espace disque: ${formatFileSize(status.diskSpaceFree)} disponible",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// Utility function to format file size into human-readable units (bytes, KB, MB, GB)
fun formatFileSize(size: Long): String {
    val kb = 1024 // 1 KB = 1024 bytes
    val mb = kb * 1024 // 1 MB = 1024 KB
    val gb = mb * 1024 // 1 GB = 1024 MB

    return when {
        size >= gb -> String.format("%.1f GB", size.toFloat() / gb)
        size >= mb -> String.format("%.1f MB", size.toFloat() / mb)
        size >= kb -> String.format("%.1f KB", size.toFloat() / kb)
        else -> "$size octets" // Otherwise, return the size in bytes
    }
}

