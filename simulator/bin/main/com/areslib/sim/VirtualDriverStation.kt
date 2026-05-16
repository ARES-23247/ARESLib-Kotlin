package com.areslib.sim

import com.areslib.math.ChassisSpeeds
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * A Swing-based virtual driver station that provides keyboard input for teleop driving
 * and visualizes the active key presses.
 */
class VirtualDriverStation : JFrame("ARES Virtual Driver Station"), KeyListener {

    private val pressedKeys = mutableSetOf<Int>()

    // Teleop maximum speeds
    private val MAX_LINEAR_SPEED = 2.0 // m/s
    private val MAX_ANGULAR_SPEED = 2.0 // rad/s

    // Mode toggle
    @Volatile
    var isTeleopMode = false
        private set

    @Volatile
    var isFieldCentric = false
        private set

    @Volatile
    var isRedAlliance = false
        private set

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(400, 300)
        isResizable = false
        
        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                
                g2d.color = Color(30, 30, 30)
                g2d.fillRect(0, 0, width, height)

                g2d.font = Font("Arial", Font.BOLD, 18)
                
                // Draw mode indicator
                g2d.color = if (isTeleopMode) Color(50, 200, 50) else Color(200, 50, 50)
                g2d.drawString("MODE: ${if (isTeleopMode) "TELEOP" else "AUTO (Path)"}", 20, 30)
                g2d.color = Color.WHITE
                g2d.font = Font("Arial", Font.PLAIN, 12)
                g2d.drawString("Press SPACE to toggle", 20, 50)

                // Control State Toggles
                g2d.font = Font("Arial", Font.BOLD, 12)
                g2d.color = if (isFieldCentric) Color(100, 200, 255) else Color(255, 200, 100)
                g2d.drawString("DRIVE: ${if (isFieldCentric) "FIELD-CENTRIC" else "ROBOT-CENTRIC"} (Press C)", 20, 70)

                g2d.color = if (isRedAlliance) Color(255, 100, 100) else Color(100, 150, 255)
                g2d.drawString("ALLIANCE: ${if (isRedAlliance) "RED" else "BLUE"} (Press R)", 20, 85)

                // Define keys to draw
                drawKey(g2d, "W", KeyEvent.VK_W, 100, 110)
                drawKey(g2d, "A", KeyEvent.VK_A, 40, 170)
                drawKey(g2d, "S", KeyEvent.VK_S, 100, 170)
                drawKey(g2d, "D", KeyEvent.VK_D, 160, 170)
                
                drawKey(g2d, "Q", KeyEvent.VK_Q, 40, 110)
                drawKey(g2d, "E", KeyEvent.VK_E, 160, 110)
            }
        }
        
        add(panel)
        pack()
        setLocationRelativeTo(null)
        
        isFocusable = true
        focusableWindowState = true
        addKeyListener(this)
        
        // Ensure it stays on top
        isAlwaysOnTop = true
        
        // Request focus when shown
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowOpened(e: java.awt.event.WindowEvent?) {
                requestFocus()
            }
        })
    }

    private fun drawKey(g2d: Graphics2D, label: String, keyCode: Int, x: Int, y: Int) {
        val size = 50
        val isPressed = pressedKeys.contains(keyCode)
        
        g2d.color = if (isPressed) Color(100, 200, 255) else Color(60, 60, 60)
        g2d.fillRoundRect(x, y, size, size, 10, 10)
        
        g2d.color = if (isPressed) Color.BLACK else Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 20)
        
        val fm = g2d.fontMetrics
        val textWidth = fm.stringWidth(label)
        val textHeight = fm.ascent
        
        g2d.drawString(label, x + (size - textWidth) / 2, y + (size + textHeight) / 2 - 2)
    }

    override fun keyTyped(e: KeyEvent?) {}

    override fun keyPressed(e: KeyEvent?) {
        e?.let {
            pressedKeys.add(it.keyCode)
            if (it.keyCode == KeyEvent.VK_SPACE) {
                isTeleopMode = !isTeleopMode
            }
            if (it.keyCode == KeyEvent.VK_C) {
                isFieldCentric = !isFieldCentric
            }
            if (it.keyCode == KeyEvent.VK_R) {
                isRedAlliance = !isRedAlliance
            }
            repaint()
        }
    }

    override fun keyReleased(e: KeyEvent?) {
        e?.let {
            pressedKeys.remove(it.keyCode)
            repaint()
        }
    }

    /**
     * Calculates requested chassis speeds based on active key presses.
     */
    fun getChassisSpeeds(): ChassisSpeeds {
        var vx = 0.0
        var vy = 0.0
        var omega = 0.0

        if (pressedKeys.contains(KeyEvent.VK_W)) vx += MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_S)) vx -= MAX_LINEAR_SPEED
        
        if (pressedKeys.contains(KeyEvent.VK_A)) vy += MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_D)) vy -= MAX_LINEAR_SPEED

        if (pressedKeys.contains(KeyEvent.VK_Q) || pressedKeys.contains(KeyEvent.VK_LEFT)) omega += MAX_ANGULAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_E) || pressedKeys.contains(KeyEvent.VK_RIGHT)) omega -= MAX_ANGULAR_SPEED

        return ChassisSpeeds(vx, vy, omega)
    }
}
