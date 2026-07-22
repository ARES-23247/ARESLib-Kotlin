package android.os;

/**
 * Mock of {@code android.os.Build} for desktop simulation.
 *
 * <p>The real {@code android.jar} from the Android SDK contains stub
 * implementations that throw {@code RuntimeException("Stub!")} at
 * runtime. Several third-party libraries (notably msgpack-core) access
 * {@code Build.VERSION.SDK_INT} during class initialization to decide
 * which Java features are available.  When the sim classpath includes
 * {@code android.jar} (needed for compiling FTC/FRC robot code), the
 * stub causes {@code MessagePacker.<clinit>} to crash.
 *
 * <p>This mock is placed in the {@code ftc-mocks} module, which appears
 * <b>first</b> on the sim classpath (see the {@code simRun} configuration
 * in {@code ARES-FTC/TeamCode/build.gradle}), so it shadows the SDK stub.
 */
public class Build {

    /** Mock of {@code android.os.Build.VERSION}. */
    public static class VERSION {
        /**
         * Returns {@code 0} so that libraries fall back to standard
         * Java APIs instead of Android-specific paths.
         */
        public static final int SDK_INT = 0;

        /** Simulated release version string. */
        public static final String RELEASE = "0.0";

        /** Simulated codename. */
        public static final String CODENAME = "REL";
    }

    /** Device manufacturer — returns a desktop sentinel. */
    public static final String MANUFACTURER = "ARES-Sim";

    /** Device model — returns a desktop sentinel. */
    public static final String MODEL = "DesktopSimulator";

    /** Hardware identifier. */
    public static final String HARDWARE = "desktop";

    /** Product name. */
    public static final String PRODUCT = "ares_sim";

    /** Device fingerprint. */
    public static final String FINGERPRINT = "ares/sim/desktop:0.0/SIM/0:userdebug/test-keys";
}
