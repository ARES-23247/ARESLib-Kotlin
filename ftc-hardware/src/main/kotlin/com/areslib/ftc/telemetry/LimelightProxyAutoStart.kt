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
    fun registerWebHandlers(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") manager: WebHandlerManager) {
        start()
        System.out.println("LimelightProxyAutoStart: Automatically registered web handlers.")
    }

    fun start() {
        if (proxy == null) {
            val p = LimelightProxy()
            proxy = p
            p.start()
            System.out.println("LimelightProxyAutoStart: Started Limelight Proxy tunnels.")
        }
    }

    fun stop() {
        proxy?.stop()
        proxy = null
        System.out.println("LimelightProxyAutoStart: Stopped Limelight Proxy tunnels.")
    }
}
