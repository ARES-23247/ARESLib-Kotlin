package com.areslib.ftc.telemetry

import android.content.Context
import org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
import com.qualcomm.robotcore.util.WebHandlerManager

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
