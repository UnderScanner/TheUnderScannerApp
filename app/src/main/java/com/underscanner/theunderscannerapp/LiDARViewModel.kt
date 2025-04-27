import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theunderscannerapp.LidarServerClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class LiDARViewModel : ViewModel() {

    // Current scan state (Idle, Scanning, Processing, or Error)
    // *NOTE*: change accordingly when fully linked
    private val _scanState = mutableStateOf<ScanState>(ScanState.Idle)
    val scanState: State<ScanState> = _scanState

    // Current server status (Connected, Disconnected, or Loading)
    private val _serverStatus = mutableStateOf<ServerStatus>(ServerStatus.Loading)
    val serverStatus: State<ServerStatus> = _serverStatus

    // Job to handle continuous server polling
    private var statusJob: Job? = null

    // Network connection state (Connected or Disconnected)
    private val _networkState = mutableStateOf<NetworkState>(NetworkState.Disconnected)
    val networkState: State<NetworkState> = _networkState

    // Test message from server connection testing
    private val _serverTestMessage = mutableStateOf("No message yet")
    val serverTestMessage: State<String> = _serverTestMessage

    // Client instance to communicate with the LiDAR server
    val client = LidarServerClient()

    // Configure server manually
    fun configureServer(ipAddress: String) {
        val serverUrl = "http://$ipAddress"
        client.updateServerUrl(serverUrl)
        _networkState.value = NetworkState.Connected(serverUrl)
        // Start status polling with new server address
        startStatusPolling()
    }


    // Test if the server is reachable and update the test message accordingly.
    fun testConnexion() {
        viewModelScope.launch {
            try {
                val result = client.testConnection()

                result.onSuccess { json ->
                    _serverTestMessage.value = "Server responded: ${json.toString()}"
                }.onFailure { e ->
                    _serverTestMessage.value = "Error: ${e.message}"
                }

            } catch (e: Exception) {
                _serverTestMessage.value = "Exception: ${e.message}"
            }
        }
    }


    // Start polling the server status every 2 seconds.
    fun startStatusPolling() {
        statusJob = viewModelScope.launch {
            while (isActive) {
                fetchStatus()
                delay(2000) // Interroger toutes les 2 secondes
            }
        }
    }

    fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }


    // Fetch the current status from the server and update UI states accordingly.
    private suspend fun fetchStatus() {
        client.getServerStatus().fold(
            onSuccess = { response ->
                val status = response.getString("status")
                when (status) {
                    "scanning" -> {
                        val scanName = response.getString("current_scan")
                        _scanState.value = ScanState.Scanning(scanName)
                    }
                    "processing" -> {
                        _scanState.value = ScanState.Processing
                    }
                    else -> {
                        _scanState.value = ScanState.Idle
                    }
                }

                // Update disk space information
                val diskSpace = response.getJSONObject("disk_space")
                _serverStatus.value = ServerStatus.Connected(
                    diskSpaceFree = diskSpace.getLong("free"),
                    diskSpaceTotal = diskSpace.getLong("total"),
                    diskSpaceUsedPercent = diskSpace.getDouble("percent_used").toFloat()
                )
            },
            onFailure = {
                // Set status to disconnected if any error occurs
                _serverStatus.value = ServerStatus.Disconnected(it.message ?: "Erreur de connexion")
            }
        )
    }

    // Fetch only the scanning status from the server.
    // *NOTE* (Could be refactored later to avoid duplication with fetchStatus)
    private suspend fun fetchScanningStatus() {
        // Changer en getScanningStatus
        client.getServerStatus().fold(
            onSuccess = { response ->
                val status = response.getString("status")
                when (status) {
                    "scanning" -> {
                        val scanName = response.getString("current_scan")
                        _scanState.value = ScanState.Scanning(scanName)
                    }
                    "processing" -> {
                        _scanState.value = ScanState.Processing
                    }
                    else -> {
                        _scanState.value = ScanState.Idle
                    }
                }

            },
            onFailure = {
                _serverStatus.value = ServerStatus.Disconnected(it.message ?: "Erreur de connexion")
            }
        )
    }

    // Start a new LiDAR scan with a generated name
    fun startScan() {
        viewModelScope.launch {
            val scanName = "scan_${System.currentTimeMillis()}"
            client.startScan(scanName).fold(
                onSuccess = {
                    _scanState.value = ScanState.Scanning(scanName)
                },
                onFailure = { e ->
                    _scanState.value = ScanState.Error(e.message ?: "Erreur lors du démarrage du scan")
                }
            )
        }
    }

    // Stop the ongoing LiDAR scan
    fun stopScan() {
        viewModelScope.launch {
            client.stopScan().fold(
                onSuccess = {
                    _scanState.value = ScanState.Processing
                },
                onFailure = { e ->
                    _scanState.value = ScanState.Error(e.message ?: "Erreur lors de l'arrêt du scan")
                }
            )
        }
    }

    fun resetError() {
        _scanState.value = ScanState.Idle
    }

}

// Represents different states of the network connection to the server
sealed class NetworkState {
    object Disconnected : NetworkState()
    object Searching : NetworkState()
    data class Connected(val serverUrl: String) : NetworkState()
}

// Represents the current state of the LiDAR scanning process
// *NOTE*: change accordingly when fully linked
sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val scanName: String) : ScanState()
    object Processing : ScanState()
    data class Error(val message: String) : ScanState()
}

// Represents the current status of the server connection and disk space
sealed class ServerStatus {
    object Loading : ServerStatus()
    data class Connected(
        val diskSpaceFree: Long,
        val diskSpaceTotal: Long,
        val diskSpaceUsedPercent: Float
    ) : ServerStatus()
    data class Disconnected(val reason: String) : ServerStatus()
}