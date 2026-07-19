package com.areslib.hardware

/**
 * Interface for hardware components that require periodic synchronous I2C polling
 * on a dedicated background thread (e.g. reading motor current).
 *
 * Devices implementing this interface can be registered with [HardwareRegistry.registerSyncPolledDevice]
 * to be included in the centralized hardware polling loop.
 */
interface SyncPolledDevice {
    /**
     * Executes the synchronous hardware read.
     * This method is called repeatedly on a dedicated background thread by the HardwareRegistry.
     * Implementations should catch any hardware exceptions to avoid killing the polling thread.
     */
    fun pollSync()
}
