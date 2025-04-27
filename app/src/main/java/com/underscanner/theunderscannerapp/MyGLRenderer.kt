package com.example.theunderscannerapp

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class MyGLRenderer(private val context: Context, private val fileName: String = "scan1.pcd") : GLSurfaceView.Renderer {

    // Vertex buffer containing the point cloud vertices
    private lateinit var vertexBuffer: FloatBuffer
    // OpenGL shader program ID
    private var program = 0
    // Total number of points loaded from the PCD file
    private var pointCount: Int = 0


    // ----------------------------
    // Orbit Camera Parameters
    // ----------------------------

    // The target point the camera is orbiting around
    private val target = floatArrayOf(0f, 0f, 0f)
    var yaw = 0f          // in degrees
    var pitch = 20f       // in degrees
    var distance = 20f    // distance from target
    var orbitYaw = 0f
    var orbitPitch = 0f

    // ----------------------------
    // Matrices for rendering
    // ----------------------------

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    // Surface dimensions (used for aspect ratio calculation)
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    // ----------------------------
    // Circle helpers (helps to understand the orientation of the whole scene)
    // ----------------------------

    private lateinit var circleXY: FloatBuffer
    private lateinit var circleYZ: FloatBuffer
    private lateinit var circleZX: FloatBuffer
    private val circleSegmentCount = 100 // Number of segments for each circle (smoothness)
    private val circleModelMatrix = FloatArray(16) // Transformation matrix for drawing circles

    // ----------------------------
    // Initialization block
    // ----------------------------

    init {
        // Parse the PCD file and create the vertex buffer
        val parsed = parsePCD(context)

        vertexBuffer = parsed.first // The float buffer containing 3D points
        pointCount = parsed.second // The number of points parsed

        // Log the loading result
        Log.d("PCD", "Loaded $pointCount points from $fileName")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set the background color to black (RGBA)
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Initialize 3D circles on the XY, YZ, and ZX planes for reference/grid visualization
        circleXY = CircleGeometry.generateCircle(1.0f, circleSegmentCount, "XY")
        circleYZ = CircleGeometry.generateCircle(1.0f, circleSegmentCount, "YZ")
        circleZX = CircleGeometry.generateCircle(1.0f, circleSegmentCount, "ZX")

        // Load and compile the vertex shader from asset file
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, readShader("shaders/vertex_shader.glsl"))
        // Load and compile the fragment shader from asset file
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, readShader("shaders/fragment_shader.glsl"))

        // Create an OpenGL program and attach the shaders
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)      // Attach compiled vertex shader
            GLES20.glAttachShader(it, fragmentShader)    // Attach compiled fragment shader
            GLES20.glLinkProgram(it)                     // Link the program (prepare it for use)
        }
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Update surface dimensions
        surfaceWidth = width
        surfaceHeight = height

        // Adjust the OpenGL viewport to the new surface size
        GLES20.glViewport(0, 0, width, height)
    }


    override fun onDrawFrame(gl: GL10?) {
        // Clear the screen (color and depth buffers)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Use the compiled and linked shader program
        GLES20.glUseProgram(program)

        // --- Update camera view (orbit camera logic) ---

        // Convert yaw and pitch from degrees to radians
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()

        // Calculate camera eye position in 3D space based on orbit parameters
        val eyeX = target[0] + distance * cos(pitchRad) * sin(yawRad)
        val eyeY = target[1] + distance * sin(pitchRad)
        val eyeZ = target[2] + distance * cos(pitchRad) * cos(yawRad)

        // Set up vector based on pitch to prevent camera flipping
        val upY = if (cos(Math.toRadians(pitch.toDouble())) > 0) 1f else -1f

        // Create the view matrix based on camera position and orientation
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, target[0], target[1], target[2], 0f, upY, 0f)

        // --- Setup the perspective projection matrix ---

        val ratio = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 60f, ratio, 1f, 100f)

        // --- Create final MVP (Model-View-Projection) matrix ---

        Matrix.setIdentityM(modelMatrix, 0)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        // Pass the MVP matrix to the shader
        val mvpHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // --- Render the point cloud ---

        val posHandle = GLES20.glGetAttribLocation(program, "a_Position")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount)
        GLES20.glDisableVertexAttribArray(posHandle)

        // --- Draw the 3D axis circles at the target point ---

        // Reset model matrix and move to target position
        Matrix.setIdentityM(circleModelMatrix, 0)
        Matrix.translateM(circleModelMatrix, 0, target[0], target[1], target[2])
        //Matrix.scaleM(circleModelMatrix, 0, 0.1f, 0.1f, 0.1f)

        // Draw the circles in red (XY plane), green (YZ plane), and blue (ZX plane)
        drawCircle(circleXY, floatArrayOf(1f, 0f, 0f, 1f), circleModelMatrix) // XY - red
        drawCircle(circleYZ, floatArrayOf(0f, 1f, 0f, 1f), circleModelMatrix) // YZ - green
        drawCircle(circleZX, floatArrayOf(0f, 0f, 1f, 1f), circleModelMatrix) // ZX - blue



    }

    private fun readShader(name: String): String {
        // Read shader code from assets folder
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun loadShader(type: Int, code: String): Int {
        // Create and compile a shader of the given type (vertex or fragment)
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
    }

    private fun parsePCD(context: Context): Pair<FloatBuffer, Int> {
        // Get the directory where scans are stored
        val scansDir = context.getExternalFilesDir("Scans") ?: context.filesDir
        val file = File(scansDir, fileName)

        // Check if the file exists, otherwise throw an error
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }

        // Read the PCD file header
        val reader = BufferedReader(InputStreamReader(FileInputStream(file)))
        val headerLines = mutableListOf<String>()
        var line: String?

        // Read PCD header lines until we hit the DATA line
        while (true) {
            line = reader.readLine() ?: break
            headerLines.add(line)
            if (line.startsWith("DATA")) break
        }
        reader.close()

        // Extract the number of points from the header ("POINTS" line)
        val pointCount = headerLines.firstOrNull { it.startsWith("POINTS") }
            ?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0

        // Reopen the file to read the binary point cloud data
        val binaryStream = FileInputStream(file)
        // Skip the header portion to reach binary data
        val skip = headerLines.joinToString("\n").toByteArray().size + 1
        binaryStream.skip(skip.toLong())

        // Read the point cloud data (each point is 8 floats of 4 bytes each: x, y, z, intensity...)
        val byteArray = ByteArray(pointCount * 8 * 4)
        binaryStream.read(byteArray)
        binaryStream.close()

        // Wrap the byte array into a FloatBuffer using little-endian byte order
        val sourceBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()

        // Prepare a FloatBuffer for OpenGL, storing only x, y, z (3 floats per point)
        // *NOTE* : Add intensity
        val floatBuffer = ByteBuffer.allocateDirect(pointCount * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Populate floatBuffer with only x, y, z from the source buffer
        // *NOTE* : Add intensity (i*8+3)
        for (i in 0 until pointCount) {
            floatBuffer.put(sourceBuffer.get(i * 8))
            floatBuffer.put(sourceBuffer.get(i * 8 + 1))
            floatBuffer.put(sourceBuffer.get(i * 8 + 2))
        }
        floatBuffer.flip() // Finalize the buffer for use in OpenGL

        // Return the final buffer and the number of points
        return floatBuffer to pointCount
    }


    // ====Public for touch updates=====

    // Rotate the orbit camera around the target based on user touch drag
    fun rotateOrbit(dYaw: Float, dPitch: Float) {
        yaw -= dYaw
        pitch += dPitch // *old* : pitch.coerceIn(-89f, 89f)
        pitch %= 360f // Keep pitch within 0-360 degrees
    }

    // Zoom the orbit camera in or out by adjusting the distance to the target
    fun zoomOrbitCamera(delta: Float) {
        distance -= delta
        distance = distance.coerceIn(1f, 100f) // Clamp zoom range
    }

    // Pan the orbit camera's target based on screen drag
    fun panOrbitTarget(dx: Float, dy: Float) {
        // Scale movement with distance
        val panSpeed = distance * 0.001f
        val right = floatArrayOf(
            -Math.cos(Math.toRadians(orbitYaw.toDouble())).toFloat(),
            Math.sin(Math.toRadians(orbitYaw.toDouble())).toFloat(),
            0f
        )
        val up = floatArrayOf(0f, 0f, 1f) // Up vector in Z-axis

        target[0] += right[0] * dx * panSpeed + up[0] * dy * panSpeed
        target[1] += right[1] * dx * panSpeed + up[1] * dy * panSpeed
        target[2] += right[2] * dx * panSpeed + up[2] * dy * panSpeed
    }

    // *NOTE* :  not used
    fun zoomOrbit(delta: Float) {
        distance = (distance - delta).coerceIn(2f, 100f)
    }
    // *NOTE* :  not used
    fun panOrbit(dx: Float, dy: Float) {
        // Pan the target based on camera orientation
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val right = floatArrayOf(sin(yawRad - PI.toFloat() / 2), 0f, cos(yawRad - PI.toFloat() / 2))
        val up = floatArrayOf(0f, 1f, 1f)

        val scale = distance * 0.00001f
        for (i in 0..2) {
            target[i] += right[i] * dx * scale
            target[i] += up[i] * dy * scale
        }
    }
    // *NOTE* :  not used
    fun rotateOrbitCamera(dx: Float, dy: Float) {
        orbitYaw += dx * 0.5f
        orbitPitch += dy * 0.5f
        orbitPitch = orbitPitch.coerceIn(-89f, 89f)
    }


    // =====for Circles========

    // Draws a colored circle using the provided vertex buffer and model matrix
    fun drawCircle(buffer: FloatBuffer, color: FloatArray, modelMatrix: FloatArray) {
        val mvpMatrix = FloatArray(16) // Final combined Model-View-Projection matrix
        val tempMatrix = FloatArray(16) // Temporary matrix for intermediate calculations

        // Combine view and model matrices first
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        // Then apply the projection matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Get the shader attribute and uniform locations
        val positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        val colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        val mvpHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")

        // Pass the final MVP matrix to the shader
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Enable the position attribute and bind the circle vertex buffer
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

        // Pass the color uniform to the shader
        GLES20.glUniform4fv(colorHandle, 1, color, 0)

        // Draw the circle as a connected loop of lines
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, circleSegmentCount + 1)

        // Disable the position attribute after drawing
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    // Getter to retrieve the number of points loaded from the point cloud file
    fun getPointCount(): Int {
        return pointCount
    }

}
