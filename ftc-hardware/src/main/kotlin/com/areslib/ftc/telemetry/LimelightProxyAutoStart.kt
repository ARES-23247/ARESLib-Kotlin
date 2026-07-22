package com.areslib.ftc.telemetry

import android.content.Context
import org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
import com.qualcomm.robotcore.util.WebHandlerManager

/**
 * Object implementation for Limelight Proxy Auto Start.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
object LimelightProxyAutoStart {
    private var proxy: LimelightProxy? = null

    @WebHandlerRegistrar
    @JvmStatic
    /**
     * registerWebHandlers declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun registerWebHandlers(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") manager: WebHandlerManager) {
        start()
        System.out.println("LimelightProxyAutoStart: Automatically registered web handlers.")
    }

    /**
     * start declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun start() {
        if (proxy == null) {
            val p = LimelightProxy()
            proxy = p
            p.start()
            System.out.println("LimelightProxyAutoStart: Started Limelight Proxy tunnels.")
        }
    }

    /**
     * stop declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun stop() {
        proxy?.stop()
        proxy = null
        System.out.println("LimelightProxyAutoStart: Stopped Limelight Proxy tunnels.")
    }
}
