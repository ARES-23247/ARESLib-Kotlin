package com.areslib.sim

import com.areslib.math.ChassisSpeeds
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JFrame
import javax.swing.JPanel
import org.lwjgl.glfw.GLFW.*

/**
 * A Swing-based virtual driver station that provides keyboard and physical gamepad input
 * for teleop driving and visualizes the active inputs on a gamepad overlay.
 *
 * Controls:
 *   WASD       = Drive (translation)
 *   Q / E      = Rotate (CCW / CW)
 *   SPACE      = Toggle Teleop / Auto
 *   C          = Toggle Field-Centric / Robot-Centric
 *   R          = Toggle Red / Blue Alliance
 *   SHIFT (LB) = Toggle Intake
 *   F (RB)     = Toggle Flywheel
 *   ENTER (RT) = Transfer/Shoot (hold, only fires when flywheel at speed)
 */
class VirtualDriverStation : JFrame("ARES Virtual Driver Station"), KeyListener {

    private val pressedKeys = mutableSetOf<Int>()

    // Teleop maximum speeds
    private val MAX_LINEAR_SPEED = 4.0 // m/s
    private val MAX_ANGULAR_SPEED = 4.0 // rad/s

    // Mode toggles
    @Volatile var isTeleopMode = false
        private set

    @Volatile var isFieldCentric = false
        private set

    @Volatile var isRedAlliance = false
        private set

    // FSM Toggles
    @Volatile var isIntaking = false
        private set
    
    @Volatile var isFlywheelOn = false
        private set

    @Volatile var isTransferring = false
        private set

    // Gamepad axes
    @Volatile private var gamepadLx = 0f
    @Volatile private var gamepadLy = 0f
    @Volatile private var gamepadRx = 0f
    @Volatile private var gamepadRy = 0f

    @Volatile private var lastGamepadShift = false
    @Volatile private var lastGamepadRb = false
    @Volatile private var isGamepadConnected = false
    @Volatile private var gamepadName = "No Gamepad Detected"

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(500, 380)
        isResizable = false
        
        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                
                // Background
                g2d.color = Color(25, 25, 25)
                g2d.fillRect(0, 0, width, height)

                // Top Status Text
                g2d.font = Font("Segoe UI", Font.BOLD, 14)
                g2d.color = if (isTeleopMode) Color(50, 200, 50) else Color(200, 50, 50)
                g2d.drawString("MODE: ${if (isTeleopMode) "TELEOP" else "AUTO (Path)"}", 20, 25)
                
                g2d.color = if (isFieldCentric) Color(100, 200, 255) else Color(255, 200, 100)
                g2d.drawString("DRIVE: ${if (isFieldCentric) "FIELD-CENTRIC" else "ROBOT-CENTRIC"}", 200, 25)

                g2d.color = if (isRedAlliance) Color(255, 100, 100) else Color(100, 150, 255)
                g2d.drawString("ALLIANCE: ${if (isRedAlliance) "RED" else "BLUE"}", 380, 25)

                g2d.color = Color(150, 150, 150)
                g2d.font = Font("Segoe UI", Font.PLAIN, 11)
                g2d.drawString("Toggles: [SPACE]=Mode, [C]=Drive, [R]=Alliance", 20, 45)
                
                g2d.color = if (isGamepadConnected) Color(50, 200, 50) else Color(150, 150, 150)
                g2d.drawString("Gamepad: $gamepadName", 20, 60)

                // Draw Gamepad Overlay
                drawGamepadOverlay(g2d)
            }
            
            private fun drawGamepadOverlay(g2d: Graphics2D) {
                val cx = width / 2
                val cy = height / 2 + 30
                
                // Gamepad Body
                g2d.color = Color(50, 50, 55)
                g2d.fillRoundRect(cx - 160, cy - 80, 320, 160, 80, 80)
                g2d.fillRoundRect(cx - 180, cy - 20, 100, 120, 50, 50) // Left handle
                g2d.fillRoundRect(cx + 80, cy - 20, 100, 120, 50, 50)  // Right handle

                // Left Bumper (Intake) - SHIFT / LB
                val isLbActive = isIntaking
                g2d.color = if (isLbActive) Color(100, 200, 255) else Color(30, 30, 35)
                g2d.fillRoundRect(cx - 140, cy - 100, 80, 30, 15, 15)
                g2d.color = if (isLbActive) Color.BLACK else Color.WHITE
                g2d.font = Font("Segoe UI", Font.BOLD, 12)
                g2d.drawString("LB / SHIFT", cx - 130, cy - 80)
                g2d.drawString("INTAKE", cx - 120, cy - 110)

                // Right Bumper (Flywheel) - F / RB
                val isRbActive = isFlywheelOn
                g2d.color = if (isRbActive) Color(255, 200, 50) else Color(30, 30, 35)
                g2d.fillRoundRect(cx + 60, cy - 100, 80, 30, 15, 15)
                g2d.color = if (isRbActive) Color.BLACK else Color.WHITE
                g2d.drawString("RB / F", cx + 75, cy - 80)
                g2d.drawString("FLYWHEEL", cx + 70, cy - 110)

                // Right Trigger (Transfer/Shoot) - ENTER / RT
                val isRtActive = isTransferring
                g2d.color = if (isRtActive) Color(255, 100, 100) else Color(30, 30, 35)
                g2d.fillRoundRect(cx + 60, cy - 140, 80, 35, 15, 15)
                g2d.color = if (isRtActive) Color.BLACK else Color.WHITE
                g2d.drawString("RT / ENTER", cx + 65, cy - 118)
                g2d.drawString("SHOOT", cx + 80, cy - 150)

                // Left Stick (WASD)
                var lxOffset = (gamepadLx * 20).toInt()
                var lyOffset = (gamepadLy * 20).toInt()
                if (pressedKeys.contains(KeyEvent.VK_A)) lxOffset = -20
                if (pressedKeys.contains(KeyEvent.VK_D)) lxOffset = 20
                if (pressedKeys.contains(KeyEvent.VK_W)) lyOffset = -20
                if (pressedKeys.contains(KeyEvent.VK_S)) lyOffset = 20

                g2d.color = Color(30, 30, 35)
                g2d.fillOval(cx - 120, cy - 40, 70, 70) // Base
                g2d.color = Color(120, 130, 140)
                g2d.fillOval(cx - 105 + lxOffset, cy - 25 + lyOffset, 40, 40) // Stick
                g2d.color = Color.WHITE
                g2d.drawString("WASD", cx - 102, cy + 50)

                // Right Stick (QE)
                var rxOffset = (gamepadRx * 20).toInt()
                var ryOffset = (gamepadRy * 20).toInt()
                if (pressedKeys.contains(KeyEvent.VK_Q)) rxOffset = -20
                if (pressedKeys.contains(KeyEvent.VK_E)) rxOffset = 20

                g2d.color = Color(30, 30, 35)
                g2d.fillOval(cx + 50, cy + 10, 70, 70) // Base
                g2d.color = Color(140, 120, 120)
                g2d.fillOval(cx + 65 + rxOffset, cy + 25 + ryOffset, 40, 40) // Stick
                g2d.color = Color.WHITE
                g2d.drawString("Q / E", cx + 70, cy + 100)

                // Status bar at bottom
                g2d.font = Font("Segoe UI", Font.BOLD, 11)
                g2d.color = Color(80, 80, 80)
                g2d.drawString("SHIFT=Intake  F=Flywheel  ENTER=Shoot", cx - 130, cy + 120)
            }
        }
        
        add(panel)
        pack()
        setLocationRelativeTo(null)
        
        isFocusable = true
        focusableWindowState = true
        addKeyListener(this)
        isAlwaysOnTop = true
        
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowOpened(e: java.awt.event.WindowEvent?) {
                requestFocus()
            }
        })

        // Initialize GLFW for Gamepad Support
        if (glfwInit()) {
            Thread { pollGamepad() }.apply { isDaemon = true; start() }
        } else {
            println("Failed to initialize GLFW. Gamepad support disabled.")
        }
    }

    private fun pollGamepad() {
        val gamepadState = org.lwjgl.glfw.GLFWGamepadState.malloc()
        try {
            while (true) {
                try {
                    // Find first connected joystick
                    var activeJoy = -1
                    for (i in GLFW_JOYSTICK_1..GLFW_JOYSTICK_16) {
                        if (glfwJoystickPresent(i)) {
                            activeJoy = i
                            break
                        }
                    }

                    if (activeJoy != -1) {
                        isGamepadConnected = true
                        gamepadName = glfwGetJoystickName(activeJoy) ?: "Unknown Gamepad"
                        
                        var lbPressedThisFrame = false
                        var rbPressedThisFrame = false
                        var rtPressedThisFrame = false

                        if (glfwJoystickIsGamepad(activeJoy) && glfwGetGamepadState(activeJoy, gamepadState)) {
                            // Standardized Gamepad API (Xbox Controller Normalized Mappings)
                            gamepadLx = gamepadState.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
                            gamepadLy = gamepadState.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)
                            gamepadRx = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_X)
                            gamepadRy = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y)
                            
                            // Triggers map from -1.0 to 1.0 in GLFW Gamepad State
                            val rtValue = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
                            if (rtValue > 0.0f) {
                                rtPressedThisFrame = true
                            }
                            
                            lbPressedThisFrame = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW_PRESS.toByte()
                            rbPressedThisFrame = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW_PRESS.toByte()
                        } else {
                            // Fallback to raw joystick inputs
                            val axes = glfwGetJoystickAxes(activeJoy)
                            val buttons = glfwGetJoystickButtons(activeJoy)
                            
                            if (axes != null && axes.capacity() >= 4) {
                                gamepadLx = axes[0]
                                gamepadLy = axes[1]
                                
                                if (axes.capacity() >= 6) {
                                    gamepadRx = axes[2]
                                    gamepadRy = axes[3]
                                    if (axes[5] > 0.5f) {
                                        rtPressedThisFrame = true
                                    }
                                } else {
                                    gamepadRx = axes[2]
                                    gamepadRy = axes[3]
                                }
                            } else {
                                gamepadLx = 0f; gamepadLy = 0f; gamepadRx = 0f; gamepadRy = 0f
                            }

                            if (buttons != null && buttons.capacity() >= 6) {
                                if (buttons[4] == GLFW_PRESS.toByte()) {
                                    lbPressedThisFrame = true
                                }
                                if (buttons[5] == GLFW_PRESS.toByte()) {
                                    rbPressedThisFrame = true
                                }
                            }
                        }

                        // Intake Toggle Edge Detection (Left Bumper)
                        if (lbPressedThisFrame && !lastGamepadShift) {
                            isIntaking = !isIntaking
                        }
                        lastGamepadShift = lbPressedThisFrame

                        // Flywheel Toggle Edge Detection (Right Bumper)
                        if (rbPressedThisFrame && !lastGamepadRb) {
                            isFlywheelOn = !isFlywheelOn
                        }
                        lastGamepadRb = rbPressedThisFrame

                        // Transfer/Shoot Momentary (Right Trigger)
                        val isKeyboardTransferring = pressedKeys.contains(KeyEvent.VK_ENTER)
                        val newIsTransferring = rtPressedThisFrame || isKeyboardTransferring
                        if (isTransferring != newIsTransferring) {
                            isTransferring = newIsTransferring
                        }

                        // Only repaint if axes or buttons changed significantly to save CPU
                        val changed = kotlin.math.abs(gamepadLx - lastLx) > 0.05f ||
                                      kotlin.math.abs(gamepadLy - lastLy) > 0.05f ||
                                      kotlin.math.abs(gamepadRx - lastRx) > 0.05f ||
                                      kotlin.math.abs(gamepadRy - lastRy) > 0.05f ||
                                      lbPressedThisFrame != lastGamepadShift ||
                                      rbPressedThisFrame != lastGamepadRb ||
                                      rtPressedThisFrame != lastGamepadEnter

                        if (changed || isKeyboardTransferring) {
                            lastLx = gamepadLx; lastLy = gamepadLy; lastRx = gamepadRx; lastRy = gamepadRy
                            lastGamepadEnter = rtPressedThisFrame
                            repaint()
                        }
                    } else {
                        if (isGamepadConnected) {
                            isGamepadConnected = false
                            gamepadName = "No Gamepad Detected"
                            gamepadLx = 0f; gamepadLy = 0f; gamepadRx = 0f; gamepadRy = 0f
                            repaint()
                        }
                    }
                    Thread.sleep(20)
                } catch (e: Exception) {
                    Thread.sleep(1000)
                }
            }
        } finally {
            gamepadState.free()
        }
    }

    @Volatile private var lastLx = 0f
    @Volatile private var lastLy = 0f
    @Volatile private var lastRx = 0f
    @Volatile private var lastRy = 0f
    @Volatile private var lastGamepadEnter = false

    override fun keyTyped(e: KeyEvent?) {}

    override fun keyPressed(e: KeyEvent?) {
        e?.let {
            pressedKeys.add(it.keyCode)
            when (it.keyCode) {
                KeyEvent.VK_SPACE -> isTeleopMode = !isTeleopMode
                KeyEvent.VK_C -> isFieldCentric = !isFieldCentric
                KeyEvent.VK_R -> isRedAlliance = !isRedAlliance
                KeyEvent.VK_SHIFT -> isIntaking = !isIntaking
                KeyEvent.VK_F -> isFlywheelOn = !isFlywheelOn
                KeyEvent.VK_ENTER -> isTransferring = true
            }
            repaint()
        }
    }

    override fun keyReleased(e: KeyEvent?) {
        e?.let {
            pressedKeys.remove(it.keyCode)
            if (it.keyCode == KeyEvent.VK_ENTER) {
                isTransferring = false
            }
            repaint()
        }
    }

    /**
     * Calculates requested chassis speeds based on active key presses and gamepad axes.
     */
    fun getChassisSpeeds(): ChassisSpeeds {
        var vx = 0.0
        var vy = 0.0
        var omega = 0.0

        // Keyboard
        if (pressedKeys.contains(KeyEvent.VK_W)) vx += MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_S)) vx -= MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_A)) vy += MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_D)) vy -= MAX_LINEAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_Q)) omega += MAX_ANGULAR_SPEED
        if (pressedKeys.contains(KeyEvent.VK_E)) omega -= MAX_ANGULAR_SPEED

        // Gamepad (Deadzone applied)
        if (kotlin.math.abs(gamepadLy) > 0.1) vx += -gamepadLy * MAX_LINEAR_SPEED
        if (kotlin.math.abs(gamepadLx) > 0.1) vy += -gamepadLx * MAX_LINEAR_SPEED
        if (kotlin.math.abs(gamepadRx) > 0.1) omega += -gamepadRx * MAX_ANGULAR_SPEED

        // Clamp to max speeds
        vx = vx.coerceIn(-MAX_LINEAR_SPEED, MAX_LINEAR_SPEED)
        vy = vy.coerceIn(-MAX_LINEAR_SPEED, MAX_LINEAR_SPEED)
        omega = omega.coerceIn(-MAX_ANGULAR_SPEED, MAX_ANGULAR_SPEED)

        return ChassisSpeeds(vx, vy, omega)
    }
}
