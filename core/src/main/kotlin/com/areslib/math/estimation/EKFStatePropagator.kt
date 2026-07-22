package com.areslib.math.estimation

import com.areslib.math.geometry.Matrix3x3
import com.areslib.math.wrapAngle

object EKFStatePropagator {
    fun propagate(
        covarianceArray: DoubleArray,
        deltaX: Double,
        deltaY: Double,
        thetaMid: Double,
        qMatrix: Matrix3x3,
        outCovariance: Matrix3x3
    ) {
        val sinMid = kotlin.math.sin(thetaMid)
        val cosMid = kotlin.math.cos(thetaMid)
        val f02 = -deltaX * sinMid - deltaY * cosMid
        val f12 =  deltaX * cosMid - deltaY * sinMid
        
        val fp00 = covarianceArray[0] + f02 * covarianceArray[6]
        val fp01 = covarianceArray[1] + f02 * covarianceArray[7]
        val fp02 = covarianceArray[2] + f02 * covarianceArray[8]
        val fp10 = covarianceArray[3] + f12 * covarianceArray[6]
        val fp11 = covarianceArray[4] + f12 * covarianceArray[7]
        val fp12 = covarianceArray[5] + f12 * covarianceArray[8]
        val fp20 = covarianceArray[6]
        val fp21 = covarianceArray[7]
        val fp22 = covarianceArray[8]
        
        outCovariance.m00 = fp00 + f02 * fp02 + qMatrix.m00
        outCovariance.m01 = fp01 + f12 * fp02 + qMatrix.m01
        outCovariance.m02 = fp02 + qMatrix.m02
        outCovariance.m10 = fp10 + f02 * fp12 + qMatrix.m10
        outCovariance.m11 = fp11 + f12 * fp12 + qMatrix.m11
        outCovariance.m12 = fp12 + qMatrix.m12
        outCovariance.m20 = fp20 + f02 * fp22 + qMatrix.m20
        outCovariance.m21 = fp21 + f12 * fp22 + qMatrix.m21
        outCovariance.m22 = fp22 + qMatrix.m22
    }

    fun repropagateHistory(
        state: PoseEstimatorState,
        closestIndex: Int,
        baseEntry: PoseHistoryEntry,
        dxX: Double, dxY: Double, dxZ: Double,
        updatedCovariance: Matrix3x3,
        baseQ: Matrix3x3,
        scratchHistory: HistoryBuffer,
        scratchCov2: Matrix3x3
    ) {
        var currentX = baseEntry.x + dxX
        var currentY = baseEntry.y + dxY
        var currentHeadingRad = wrapAngle(baseEntry.headingRad + dxZ)
        
        scratchCov2.setTo(updatedCovariance)

        scratchHistory.updateEntryDirect(closestIndex, baseEntry.timestampMs, currentX, currentY, currentHeadingRad, scratchCov2, baseEntry.qScale)

        for (i in (closestIndex + 1) until state.history.size) {
            val prevRaw = state.history[i - 1]
            val currRaw = state.history[i]
            
            val deltaX = currRaw.x - prevRaw.x
            val deltaY = currRaw.y - prevRaw.y
            val deltaHeading = currRaw.headingRad - prevRaw.headingRad

            currentX += deltaX
            currentY += deltaY
            currentHeadingRad = wrapAngle(currentHeadingRad + deltaHeading)

            val scale = currRaw.qScale
            val reThetaMid = currentHeadingRad + deltaHeading * 0.5
            val reSinMid = kotlin.math.sin(reThetaMid)
            val reCosMid = kotlin.math.cos(reThetaMid)
            val reF02 = -deltaX * reSinMid - deltaY * reCosMid
            val reF12 =  deltaX * reCosMid - deltaY * reSinMid
            
            val reFp00 = scratchCov2.m00 + reF02 * scratchCov2.m20
            val reFp01 = scratchCov2.m01 + reF02 * scratchCov2.m21
            val reFp02 = scratchCov2.m02 + reF02 * scratchCov2.m22
            val reFp10 = scratchCov2.m10 + reF12 * scratchCov2.m20
            val reFp11 = scratchCov2.m11 + reF12 * scratchCov2.m21
            val reFp12 = scratchCov2.m12 + reF12 * scratchCov2.m22
            val reFp20 = scratchCov2.m20
            val reFp21 = scratchCov2.m21
            val reFp22 = scratchCov2.m22
            
            val newM00 = reFp00 + reF02 * reFp02 + baseQ.m00 * scale
            val newM01 = reFp01 + reF12 * reFp02 + baseQ.m01 * scale
            val newM02 = reFp02 + baseQ.m02 * scale
            val newM10 = reFp10 + reF02 * reFp12 + baseQ.m10 * scale
            val newM11 = reFp11 + reF12 * reFp12 + baseQ.m11 * scale
            val newM12 = reFp12 + baseQ.m12 * scale
            val newM20 = reFp20 + reF02 * reFp22 + baseQ.m20 * scale
            val newM21 = reFp21 + reF12 * reFp22 + baseQ.m21 * scale
            val newM22 = reFp22 + baseQ.m22 * scale
            
            scratchCov2.m00 = newM00
            scratchCov2.m01 = newM01
            scratchCov2.m02 = newM02
            scratchCov2.m10 = newM10
            scratchCov2.m11 = newM11
            scratchCov2.m12 = newM12
            scratchCov2.m20 = newM20
            scratchCov2.m21 = newM21
            scratchCov2.m22 = newM22
            
            scratchHistory.updateEntryDirect(i, state.history[i].timestampMs, currentX, currentY, currentHeadingRad, scratchCov2, currRaw.qScale)
        }

        // Apply back to state
        state.estimatedPoseX = currentX
        state.estimatedPoseY = currentY
        state.estimatedPoseHeading = currentHeadingRad
        
        state.covarianceArray[0] = scratchCov2.m00
        state.covarianceArray[1] = scratchCov2.m01
        state.covarianceArray[2] = scratchCov2.m02
        state.covarianceArray[3] = scratchCov2.m10
        state.covarianceArray[4] = scratchCov2.m11
        state.covarianceArray[5] = scratchCov2.m12
        state.covarianceArray[6] = scratchCov2.m20
        state.covarianceArray[7] = scratchCov2.m21
        state.covarianceArray[8] = scratchCov2.m22
    }
}
