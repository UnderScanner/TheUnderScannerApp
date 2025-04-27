package com.example.theunderscannerapp


import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent



class MyGLSurfaceView(context: Context, fileName: String = "scan1.pcd") : GLSurfaceView(context) {

    // Renderer responsible for OpenGL drawing
    private val renderer: MyGLRenderer

    // Variables for touch handling
    private var prevTouchDistance = 0f
    private var prevMidX = 0f
    private var prevMidY = 0f
    private var mode = 0 // 0 = none, 1 = one finger, 2 = two fingers
    private var previousX = 0f
    private var previousY = 0f

    init {
        // Set OpenGL ES version
        setEGLContextClientVersion(2)

        // Create renderer and assign it
        renderer = MyGLRenderer(context, fileName)
        setRenderer(renderer)

        // Render continuously (or RENDERMODE_WHEN_DIRTY for manual updates)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Reset previous values to avoid jumps on finger lift
        // *NOTE*: make sure it works
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            prevTouchDistance = 0f
            prevMidX = 0f
            prevMidY = 0f
        }

        // Handle different touch scenarios
        when (event.pointerCount) {
            1 -> handleSingleTouch(event) // Single finger: orbit rotation
            2 -> handleMultiTouch(event) // Two fingers: zoom and pan
        }
        return true
    }

    // Handle single touch events (orbit rotation).
    private fun handleSingleTouch(event: MotionEvent) {
        val x = event.getX(0)
        val y = event.getY(0)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY
                renderer.rotateOrbit(-dx, dy) // Rotate camera based on movement delta
            }
        }

        previousX = x
        previousY = y
    }

    // Handle multi-touch events (pinch zoom and two-finger pan)
    private fun handleMultiTouch(event: MotionEvent) {
        if (event.pointerCount < 2) return

        val x1 = event.getX(0)
        val y1 = event.getY(0)
        val x2 = event.getX(1)
        val y2 = event.getY(1)

        val midX = (x1 + x2) / 2f
        val midY = (y1 + y2) / 2f

        val dx = midX - prevMidX
        val dy = midY - prevMidY

        val newDist = distance(x1, y1, x2, y2)
        val distDelta = newDist - prevTouchDistance

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Pinch Zoom
            renderer.zoomOrbitCamera(distDelta * 0.01f)

            // Two-finger Pan
            renderer.panOrbitTarget(-dx, -dy)
        }

        // Update previous values
        prevTouchDistance = newDist
        prevMidX = midX
        prevMidY = midY
    }

    // Utility function to calculate the distance between two points (used for pinch detection)
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // Public method to expose the number of points in the point cloud
    fun getPointCount(): Int {
        return renderer.getPointCount()
    }


}
