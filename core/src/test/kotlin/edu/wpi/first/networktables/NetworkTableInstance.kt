package edu.wpi.first.networktables

/**
 * PubSubOption declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class PubSubOption

/**
 * NetworkTableInstance declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class NetworkTableInstance {
    companion object {
        @JvmStatic
        fun getDefault(): NetworkTableInstance = NetworkTableInstance()
    }

    /**
     * getDoubleTopic declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getDoubleTopic(name: String): DoubleTopic = DoubleTopic(name)
    /**
     * getBooleanTopic declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getBooleanTopic(name: String): BooleanTopic = BooleanTopic(name)
    /**
     * getStringTopic declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getStringTopic(name: String): StringTopic = StringTopic(name)
    /**
     * getDoubleArrayTopic declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getDoubleArrayTopic(name: String): DoubleArrayTopic = DoubleArrayTopic(name)

    /**
     * getEntry declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getEntry(name: String): NetworkTableEntry = NetworkTableEntry(name)
}

/**
 * NetworkTableEntry declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class NetworkTableEntry(val name: String) {
    /**
     * setDouble declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setDouble(value: Double) {
        DoublePublisher.lastValues[name] = value
        lastValues[name] = value
    }
    /**
     * setBoolean declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setBoolean(value: Boolean) {
        BooleanPublisher.lastValues[name] = value
        lastValues[name] = value
    }
    /**
     * setString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setString(value: String) {
        StringPublisher.lastValues[name] = value
        lastValues[name] = value
    }
    /**
     * setDoubleArray declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setDoubleArray(value: DoubleArray) {
        DoubleArrayPublisher.lastValues[name] = value
        lastValues[name] = value
    }
    /**
     * getDouble declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getDouble(defaultValue: Double): Double = (lastValues[name] as? Double) ?: defaultValue
    /**
     * getBoolean declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getBoolean(defaultValue: Boolean): Boolean = (lastValues[name] as? Boolean) ?: defaultValue
    /**
     * getString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getString(defaultValue: String): String = (lastValues[name] as? String) ?: defaultValue

    companion object {
        val lastValues = mutableMapOf<String, Any>()
    }
}

/**
 * DoubleTopic declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class DoubleTopic(val name: String) {
    /**
     * publish declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun publish(vararg options: PubSubOption): DoublePublisher = DoublePublisher(name)
}
/**
 * DoublePublisher declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class DoublePublisher(val name: String) {
    companion object {
        val lastValues = mutableMapOf<String, Double>()
    }
    /**
     * set declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun set(v: Double) {
        lastValues[name] = v
        NetworkTableEntry.lastValues[name] = v
    }
}

/**
 * BooleanTopic declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class BooleanTopic(val name: String) {
    /**
     * publish declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun publish(vararg options: PubSubOption): BooleanPublisher = BooleanPublisher(name)
}
/**
 * BooleanPublisher declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class BooleanPublisher(val name: String) {
    companion object {
        val lastValues = mutableMapOf<String, Boolean>()
    }
    /**
     * set declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun set(v: Boolean) {
        lastValues[name] = v
        NetworkTableEntry.lastValues[name] = v
    }
}

/**
 * StringTopic declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class StringTopic(val name: String) {
    /**
     * publish declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun publish(vararg options: PubSubOption): StringPublisher = StringPublisher(name)
}
/**
 * StringPublisher declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class StringPublisher(val name: String) {
    companion object {
        val lastValues = mutableMapOf<String, String>()
    }
    /**
     * set declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun set(v: String) {
        lastValues[name] = v
        NetworkTableEntry.lastValues[name] = v
    }
}

/**
 * DoubleArrayTopic declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class DoubleArrayTopic(val name: String) {
    /**
     * publish declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun publish(vararg options: PubSubOption): DoubleArrayPublisher = DoubleArrayPublisher(name)
}
/**
 * DoubleArrayPublisher declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class DoubleArrayPublisher(val name: String) {
    companion object {
        val lastValues = mutableMapOf<String, DoubleArray>()
    }
    /**
     * set declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun set(v: DoubleArray) {
        lastValues[name] = v
        NetworkTableEntry.lastValues[name] = v
    }
}
