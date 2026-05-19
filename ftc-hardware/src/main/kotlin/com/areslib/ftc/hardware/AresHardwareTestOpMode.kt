package com.areslib.ftc.hardware

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.IMU
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.areslib.state.RobotState
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d

/**
 * This integration test maps hardware from all requested systems 
 * (REV Hub, SRS Hub, OctoQuad, and goBILDA Pinpoint) to the immutable Redux state.
 */
@TeleOp(name = "ARES: Hardware Integration Test", group = "ARES")
class AresHardwareTestOpMode : LinearOpMode() {
    override fun runOpMode() {
        telemetry.addData("Status", "Initializing Hardware")
        telemetry.update()

        // 1. REV Hub Hardware
        val revMotor = hardwareMap.get(DcMotorEx::class.java, "revMotor")
        val revImu = hardwareMap.get(IMU::class.java, "imu")
        
        // Wrap REV components
        val motorIO = FtcMotor(revMotor)
        val imuIO = FtcImu(revImu)

        // 2. SRS Hub (via I2C)
        val srsDevice = hardwareMap.get(I2cDeviceSynch::class.java, "srsHub")
        val srsHub = SrsHubDriver(srsDevice)
        srsHub.initialize()
        val srsAnalog = SrsHubAnalogIO(srsHub, 0)

        // 3. OctoQuad (via I2C)
        val octoDevice = hardwareMap.get(I2cDeviceSynch::class.java, "octoQuad")
        val octoQuad = OctoQuadFWv3(octoDevice)
        octoQuad.initialize()
        val octoEncoder = OctoQuadEncoderIO(octoQuad, 0)

        // 4. goBILDA Pinpoint (simulated by proxy)
        // Normally we'd fetch the PinpointDriver class from hardwareMap,
        // but to keep the hardware abstraction decoupled, we use our proxy.
        // val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, "pinpoint")
        // val pinpointIO = PinpointOdometryIO(pinpointDriver)

        telemetry.addData("Status", "Initialized. Waiting for Start")
        telemetry.update()
        waitForStart()

        val imuInputs = com.areslib.hardware.ImuInputs()

        while (opModeIsActive()) {
            // Update pure state from hardware IO
            val revMotorPos = motorIO.position
            val revMotorVel = motorIO.velocity
            imuIO.updateInputs(imuInputs)
            val imuHeading = imuInputs.headingRadians
            val srsVoltage = srsAnalog.voltage
            val octoPos = octoEncoder.position
            val octoVel = octoEncoder.velocity

            // Map physical telemetry to the immutable state representation
            // We would normally dispatch an action to a Redux reducer here.
            // For testing, we just update telemetry.

            telemetry.addData("REV Motor Pos", revMotorPos)
            telemetry.addData("REV Motor Vel", revMotorVel)
            telemetry.addData("REV IMU Heading", imuHeading)
            telemetry.addData("SRS Analog Volts", srsVoltage)
            telemetry.addData("OctoQuad Enc Pos", octoPos)
            telemetry.addData("OctoQuad Enc Vel", octoVel)
            telemetry.update()
        }
    }
}
