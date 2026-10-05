package net.plainnotes.app.security

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/** Two strong shakes within a second (≈2.7 g each) fire [onShake]. */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var first = 0L; private var last = 0L
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        if (g < 2.7f) return
        val now = SystemClock.elapsedRealtime()
        if (now - last < 250) return // same jolt
        if (now - first > 1000) first = now else { first = 0; onShake() }
        last = now
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    fun register(m: SensorManager) = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    fun unregister(m: SensorManager) = m.unregisterListener(this)
}
