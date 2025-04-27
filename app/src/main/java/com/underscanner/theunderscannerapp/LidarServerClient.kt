package com.example.theunderscannerapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

// Client to interact with the LiDAR server via HTTP
class LidarServerClient(private var serverUrl: String = "http://10.244.178.232:5000") {

    // HTTP client with custom timeout settings
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)// Maximum time to establish a connection
        .readTimeout(30, TimeUnit.SECONDS)     // Maximum time to wait for a server response
        .build()


    // Add a configurable server URL
    fun updateServerUrl(newUrl: String) {
        serverUrl = newUrl
    }


    // Add download with progress monitoring
    suspend fun downloadScanWithProgress(
        scanName: String,
        destination: File,
        progress: (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) { // Run the download operation in the IO dispatcher (background thread)
            try {
                // Build a GET request to download the scan file
                val request = Request.Builder()
                    .url("$serverUrl/scans/$scanName")
                    .get()
                    .build()

                // Execute the HTTP request
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.let { body ->
                        val contentLength = body.contentLength() // Total size of the file to download
                        var bytesRead = 0L // Track how many bytes have been downloaded

                        val outputStream = FileOutputStream(destination)

                        // Stream the file from network to disk
                        body.byteStream().use { input ->
                            outputStream.use { output ->
                                val buffer = ByteArray(8192) // Buffer to read chunks of data
                                var bytes: Int

                                // Read data in chunks and write to the file
                                while (input.read(buffer).also { bytes = it } != -1) {
                                    output.write(buffer, 0, bytes)
                                    bytesRead += bytes

                                    // Calculate and report progress
                                    val downloadProgress = bytesRead.toFloat() / contentLength.toFloat()
                                    progress(downloadProgress)
                                }
                            }
                        }
                        // Return success result with destination file
                        Result.success(destination)
                    } ?: Result.failure(IOException("Empty response body"))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Test the connection to the server (calls /test endpoint)
    suspend fun testConnection(): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/test")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    Result.success(JSONObject(jsonStr))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }


    // Fetch the current server status (calls /status endpoint)
    suspend fun getServerStatus(): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/status")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    Result.success(JSONObject(jsonStr))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Start a new scan session with a given scan name
    // *NOTE*: change accordingly when fully linked
    suspend fun startScan(scanName: String): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().put("name", scanName)
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$serverUrl/start_scan")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    Result.success(JSONObject(jsonStr))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Stop the currently running scan session
    // *NOTE*: change accordingly when fully linked
    suspend fun stopScan(): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = "{}".toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$serverUrl/stop_scan")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    Result.success(JSONObject(jsonStr))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // List all available scans on the server (calls /scans endpoint)
    suspend fun listScans(): Result<List<ScanInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/scans")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    val jsonObj = JSONObject(jsonStr)
                    val scansArray = jsonObj.getJSONArray("scans")

                    val scansList = mutableListOf<ScanInfo>()
                    for (i in 0 until scansArray.length()) {
                        val scan = scansArray.getJSONObject(i)
                        scansList.add(
                            ScanInfo(
                                name = scan.getString("name"),
                                size = scan.getLong("size"),
                                date = scan.getLong("date")
                            )
                        )
                    }

                    Result.success(scansList)
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Download a scan without progress tracking
    // *NOTE* : Not used
    suspend fun downloadScan(scanName: String, destination: File): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/scans/$scanName")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        val outputStream = FileOutputStream(destination)
                        body.byteStream().use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        Result.success(destination)
                    } ?: Result.failure(IOException("Empty response body"))
                } else {
                    Result.failure(IOException("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}


// Represents metadata for a LiDAR scan stored on the server
data class ScanInfo(
    val name: String, // Scan file name
    val size: Long, // Scan file size in bytes
    val date: Long // Date of scan (timestamp format)
)